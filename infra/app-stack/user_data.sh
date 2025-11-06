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

dnf update -y
dnf install -y docker jq amazon-ssm-agent amazon-cloudwatch-agent postgresql15

dnf clean all
rm -rf /var/cache/dnf

systemctl enable --now docker
systemctl enable --now amazon-ssm-agent

BOOTSTRAP_LOG_GROUP="/${project}/ec2/bootstrap"
CONTAINER_LOG_GROUP="/${project}/app/containers"

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
          }
        ]
      }
    }
  }
}
EOF

systemctl enable amazon-cloudwatch-agent
/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a fetch-config \
  -m ec2 \
  -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json \
  -s

# docker compose plugin
DOCKER_PLUGINS=/usr/libexec/docker/cli-plugins
mkdir -p $DOCKER_PLUGINS
# for t3.micro (x86_64), use the x86_64 compose binary
curl -fsSL https://github.com/docker/compose/releases/download/v2.29.2/docker-compose-linux-x86_64 -o $DOCKER_PLUGINS/docker-compose
chmod +x $DOCKER_PLUGINS/docker-compose

mkdir -p /opt/${project}
cd /opt/${project}

# fetch secrets from SSM (Standard tier)
DB_PASSWORD=$(aws ssm get-parameter --with-decryption --name "/${project}/db/password" --region "$region" --query "Parameter.Value" --output text)
FIREBASE_SA_B64=$(aws ssm get-parameter --with-decryption --name "/${project}/firebase/sa_b64" --region "$region" --query "Parameter.Value" --output text)
CLOUDFRONT_PRIVATE_KEY_B64=$(aws ssm get-parameter --with-decryption --name "$cloudfront_private_key_param" --region "$region" --query "Parameter.Value" --output text)
CLOUDFRONT_KEY_PAIR_ID=$(aws ssm get-parameter --name "$cloudfront_key_pair_param" --region "$region" --query "Parameter.Value" --output text)

# ensure required Postgres extensions exist before app starts (safe if already created)
until PGPASSWORD="$DB_PASSWORD" psql -h "$rds_host" -U "$db_user" -d "$db_name" -c "select 1" >/dev/null 2>&1; do
  echo "Waiting for database to be reachable..."
  sleep 5
done

PGPASSWORD="$DB_PASSWORD" psql -h "$rds_host" -U "$db_user" -d "$db_name" -v ON_ERROR_STOP=1 -c "CREATE EXTENSION IF NOT EXISTS pgcrypto;"
PGPASSWORD="$DB_PASSWORD" psql -h "$rds_host" -U "$db_user" -d "$db_name" -v ON_ERROR_STOP=1 -c "CREATE EXTENSION IF NOT EXISTS pg_trgm;"

# Log in to ECR so Compose can pull the app image
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
aws ecr get-login-password --region "$region" | docker login --username AWS --password-stdin "$ACCOUNT_ID.dkr.ecr.${region}.amazonaws.com"

cat > .env <<EOF
DB_PASSWORD=$DB_PASSWORD
FIREBASE_SA_B64=$FIREBASE_SA_B64
JDBC_URL=jdbc:postgresql://${rds_host}:5432/${db_name}
JDBC_USER=$db_user
ECR_IMAGE=$ecr_image
AWS_REGION=${region}
AWS_S3_BUCKET=$video_bucket
AWS_CLOUDFRONT_DOMAIN=$cloudfront_domain
AWS_CLOUDFRONT_KEY_PAIR_ID=$CLOUDFRONT_KEY_PAIR_ID
AWS_CLOUDFRONT_PRIVATE_KEY_B64=$CLOUDFRONT_PRIVATE_KEY_B64
AWS_CLOUDFRONT_SIGNED_URL_DURATION_SECONDS=$cloudfront_signed_url_ttl
LOG_GROUP_APP_CONTAINERS=$CONTAINER_LOG_GROUP
EOF

# minimal nginx reverse proxy
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

# compose without a local DB; RDS is the DB
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
      AWS_S3_BUCKET: $${AWS_S3_BUCKET}
      AWS_CLOUDFRONT_DOMAIN: $${AWS_CLOUDFRONT_DOMAIN}
      AWS_CLOUDFRONT_KEY_PAIR_ID: $${AWS_CLOUDFRONT_KEY_PAIR_ID}
      AWS_CLOUDFRONT_PRIVATE_KEY_B64: $${AWS_CLOUDFRONT_PRIVATE_KEY_B64}
      AWS_CLOUDFRONT_SIGNED_URL_DURATION_SECONDS: $${AWS_CLOUDFRONT_SIGNED_URL_DURATION_SECONDS}
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

docker compose --env-file .env pull --quiet
docker compose --env-file .env up -d

echo "userdata OK"
