#!/bin/bash
set -euxo pipefail

# mirror all output to a log you can read from the console
exec >> /var/log/userdata.log 2>&1

project="${project}"
region="${region}"
rds_host="${rds_host}"
db_name="${db_name}"
db_user="${db_user}"
ecr_image="${ecr_image}"
domain_name="${domain_name}"
email_for_lets="${email_for_lets}"
video_bucket="${video_bucket_name}"
cloudfront_domain="${cloudfront_domain}"
cloudfront_key_pair_param="${cloudfront_key_pair_parameter}"
cloudfront_private_key_param="${cloudfront_private_key_parameter}"
cloudfront_signed_url_ttl="${cloudfront_signed_url_ttl_seconds}"
firebase_web_api_key_param="${firebase_web_api_key_parameter}"
admin_uids_param="${admin_uids_parameter}"
BOOTSTRAP_LOG_GROUP="/${project}/ec2/bootstrap"
CONTAINER_LOG_GROUP="/${project}/app/containers"

# Install OS packages (docker, cloudwatch agent, etc.) and enable services.
# Install required packages (Docker, SSM agent, CloudWatch agent) and enable services.
install_packages() {
  dnf update -y
  dnf install -y docker jq amazon-ssm-agent amazon-cloudwatch-agent postgresql15
  dnf clean all && rm -rf /var/cache/dnf
  systemctl enable --now docker
  systemctl enable --now amazon-ssm-agent
}

# Configure CloudWatch Logs to ship userdata and SSM logs for troubleshooting.
configure_cloudwatch() {
  aws logs create-log-group --log-group-name "$BOOTSTRAP_LOG_GROUP" --region "$region" 2>/dev/null || true
  aws logs create-log-group --log-group-name "$CONTAINER_LOG_GROUP" --region "$region" 2>/dev/null || true

  mkdir -p /opt/aws/amazon-cloudwatch-agent/etc
  cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json <<EOF
{
  "logs": {
    "logs_collected": {
      "files": {
        "collect_list": [
          {
            "file_path": "/var/log/userdata.log",
            "log_group_name": "$BOOTSTRAP_LOG_GROUP",
            "log_stream_name": "{instance_id}/userdata",
            "timestamp_format": "%Y-%m-%d %H:%M:%S"
          },
          {
            "file_path": "/var/log/amazon/ssm/amazon-ssm-agent.log",
            "log_group_name": "$BOOTSTRAP_LOG_GROUP",
            "log_stream_name": "{instance_id}/ssm-agent",
            "timestamp_format": "%Y-%m-%d %H:%M:%S"
          },
          {
            "file_path": "/var/log/messages",
            "log_group_name": "$BOOTSTRAP_LOG_GROUP",
            "log_stream_name": "{instance_id}/messages",
            "timestamp_format": "%Y-%m-%d %H:%M:%S"
          }
        ]
      }
    }
  }
}
EOF

  systemctl enable amazon-cloudwatch-agent
  /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
    -a fetch-config -m ec2 \
    -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json \
    -s

  export BOOTSTRAP_LOG_GROUP CONTAINER_LOG_GROUP
}

# Install Docker Compose CLI plugin (x86_64 binary for t3.micro).
install_docker_compose() {
  local DOCKER_PLUGINS=/usr/libexec/docker/cli-plugins
  mkdir -p "$DOCKER_PLUGINS"
  curl -fsSL https://github.com/docker/compose/releases/download/v2.29.2/docker-compose-linux-x86_64 \
    -o "$DOCKER_PLUGINS/docker-compose"
  chmod +x "$DOCKER_PLUGINS/docker-compose"
}

# Pull application secrets and config values from SSM Parameter Store.
fetch_secrets() {
  DB_PASSWORD=$(aws ssm get-parameter --with-decryption --name "/${project}/db/password" --region "$region" --query "Parameter.Value" --output text)
  FIREBASE_SA_B64=$(aws ssm get-parameter --with-decryption --name "/${project}/firebase/sa_b64" --region "$region" --query "Parameter.Value" --output text)
  FIREBASE_WEB_API_KEY=$(aws ssm get-parameter --with-decryption --name "$firebase_web_api_key_param" --region "$region" --query "Parameter.Value" --output text)
  CLOUDFRONT_PRIVATE_KEY_B64=$(aws ssm get-parameter --with-decryption --name "$cloudfront_private_key_param" --region "$region" --query "Parameter.Value" --output text)
  CLOUDFRONT_KEY_PAIR_ID=$(aws ssm get-parameter --name "$cloudfront_key_pair_param" --region "$region" --query "Parameter.Value" --output text)
  SECURITY_ADMIN_UIDS=$(aws ssm get-parameter --with-decryption --name "$admin_uids_param" --region "$region" --query "Parameter.Value" --output text)
}

# Wait until the RDS database is reachable before proceeding.
wait_for_database() {
  until PGPASSWORD="$DB_PASSWORD" psql -h "$rds_host" -U "$db_user" -d "$db_name" -c "select 1" >/dev/null 2>&1; do
    echo "Waiting for database to be reachable..."
    sleep 5
  done
}

# Ensure required Postgres extensions exist (run once; safe if already there).
ensure_db_extensions() {
  PGPASSWORD="$DB_PASSWORD" psql -h "$rds_host" -U "$db_user" -d "$db_name" -v ON_ERROR_STOP=1 \
    -c "CREATE EXTENSION IF NOT EXISTS pgcrypto;"
  PGPASSWORD="$DB_PASSWORD" psql -h "$rds_host" -U "$db_user" -d "$db_name" -v ON_ERROR_STOP=1 \
    -c "CREATE EXTENSION IF NOT EXISTS pg_trgm;"
}

# Authenticate Docker with ECR so we can pull the app image.
login_to_ecr() {
  local ACCOUNT_ID
  ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
  aws ecr get-login-password --region "$region" | docker login --username AWS --password-stdin \
    "$ACCOUNT_ID.dkr.ecr.${region}.amazonaws.com"
}

# Write the container environment (.env) file consumed by docker-compose.
write_env_file() {
  cat > .env <<EOF
DB_PASSWORD=$DB_PASSWORD
FIREBASE_SA_B64=$FIREBASE_SA_B64
FIREBASE_WEB_API_KEY=$FIREBASE_WEB_API_KEY
JDBC_URL=jdbc:postgresql://${rds_host}:5432/${db_name}
JDBC_USER=$db_user
ECR_IMAGE=$ecr_image
AWS_REGION=${region}
AWS_S3_BUCKET=$video_bucket
AWS_CLOUDFRONT_DOMAIN=$cloudfront_domain
AWS_CLOUDFRONT_KEY_PAIR_ID=$CLOUDFRONT_KEY_PAIR_ID
AWS_CLOUDFRONT_PRIVATE_KEY_B64=$CLOUDFRONT_PRIVATE_KEY_B64
AWS_CLOUDFRONT_SIGNED_URL_DURATION_SECONDS=$cloudfront_signed_url_ttl
SECURITY_ADMIN_UIDS=$SECURITY_ADMIN_UIDS
LOG_GROUP_APP_CONTAINERS=$CONTAINER_LOG_GROUP
EOF
}

# Write a minimal nginx reverse proxy config to front the app container.
write_nginx_config() {
  cat > nginx.conf <<'CONF'
server {
  listen 80;
  server_name _;
  location / {
    proxy_pass http://app:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
  }
}
CONF
}

# Generate docker-compose.yml (app + nginx) with logging and env wiring.
write_compose_file() {
  cat > docker-compose.yml <<'YAML'
services:
  app:
    image: $${ECR_IMAGE:-local/app:latest}
    container_name: app
    restart: unless-stopped
    environment:
      SPRING_DATASOURCE_URL: $${JDBC_URL}
      SPRING_DATASOURCE_USERNAME: $${JDBC_USER}
      SPRING_DATASOURCE_PASSWORD: $${DB_PASSWORD}
      FIREBASE_SERVICE_ACCOUNT_B64: $${FIREBASE_SA_B64}
      FIREBASE_WEB_API_KEY: $${FIREBASE_WEB_API_KEY}
      AWS_S3_BUCKET: $${AWS_S3_BUCKET}
      AWS_CLOUDFRONT_DOMAIN: $${AWS_CLOUDFRONT_DOMAIN}
      AWS_CLOUDFRONT_KEY_PAIR_ID: $${AWS_CLOUDFRONT_KEY_PAIR_ID}
      AWS_CLOUDFRONT_PRIVATE_KEY_B64: $${AWS_CLOUDFRONT_PRIVATE_KEY_B64}
      AWS_CLOUDFRONT_SIGNED_URL_DURATION_SECONDS: $${AWS_CLOUDFRONT_SIGNED_URL_DURATION_SECONDS}
      SECURITY_ADMIN_UIDS: $${SECURITY_ADMIN_UIDS}
      JAVA_TOOL_OPTIONS: "-XX:+UseZGC -Xmx512m"
    logging:
      driver: awslogs
      options:
        awslogs-region: $${AWS_REGION}
        awslogs-group: $${LOG_GROUP_APP_CONTAINERS}
        awslogs-stream: app
        awslogs-create-group: "true"
    networks: [appnet]
  web:
    image: nginx:1.27-alpine
    container_name: web
    restart: unless-stopped
    volumes:
      - ./nginx.conf:/etc/nginx/conf.d/default.conf:ro
    ports:
      - "80:80"
    depends_on: [app]
    networks: [appnet]
    logging:
      driver: awslogs
      options:
        awslogs-region: $${AWS_REGION}
        awslogs-group: $${LOG_GROUP_APP_CONTAINERS}
        awslogs-stream: web
        awslogs-create-group: "true"
networks:
  appnet: {}
YAML
}

# Pull the latest image and start the compose stack.
deploy_stack() {
  docker compose --env-file .env pull --quiet
  docker compose --env-file .env up -d
}

# Main orchestrates bootstrap flow: packages -> logging -> compose deployment.
main() {
  install_packages
  configure_cloudwatch
  install_docker_compose
  mkdir -p /opt/${project}
  cd /opt/${project}

  fetch_secrets
  wait_for_database
  ensure_db_extensions
  login_to_ecr
  write_env_file
  write_nginx_config
  write_compose_file
  deploy_stack

  echo "userdata OK"
}

main
*** End Patch
