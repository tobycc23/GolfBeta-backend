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

dnf update -y
dnf install -y docker jq

dnf clean all
rm -rf /var/cache/dnf

systemctl enable --now docker

# docker compose plugin
DOCKER_PLUGINS=/usr/libexec/docker/cli-plugins
mkdir -p $DOCKER_PLUGINS
# for t3.micro (x86_64), use the x86_64 compose binary
curl -fsSL https://github.com/docker/compose/releases/download/v2.29.2/docker-compose-linux-x86_64 -o $DOCKER_PLUGINS/docker-compose
chmod +x $DOCKER_PLUGINS/docker-compose

mkdir -p /opt/$${project}
cd /opt/$${project}

# fetch secrets from SSM (Standard tier)
DB_PASSWORD=$(aws ssm get-parameter --with-decryption --name "/$${project}/db/password" --region $${region} --query "Parameter.Value" --output text)
FIREBASE_SA_B64=$(aws ssm get-parameter --with-decryption --name "/$${project}/firebase/sa_b64" --region $${region} --query "Parameter.Value" --output text)


# Log in to ECR so Compose can pull the app image
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
aws ecr get-login-password --region $${region} | docker login --username AWS --password-stdin "$${ACCOUNT_ID}.dkr.ecr.$${region}.amazonaws.com"

cat > .env <<EOF
DB_PASSWORD=$${DB_PASSWORD}
FIREBASE_SA_B64=$${FIREBASE_SA_B64}
JDBC_URL=jdbc:postgresql://$${rds_host}:5432/$${db_name}
JDBC_USER=$${db_user}
ECR_IMAGE=$${ecr_image}
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
      JAVA_TOOL_OPTIONS: "-XX:+UseZGC -Xmx512m"
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
networks:
  appnet: {}
YAML

docker compose --env-file .env pull --quiet
docker compose --env-file .env up -d

echo "userdata OK"