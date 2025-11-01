terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws     = { source = "hashicorp/aws", version = "~> 5.0" }
    random  = { source = "hashicorp/random", version = "~> 3.6" }
    archive = { source = "hashicorp/archive", version = "~> 2.5" }
  }
}

provider "aws" {
  region  = var.aws_region
  profile = "golfbeta"
}

locals {
  protect_tag = { "budget-protect" = "true" }
  project_tag = { "Project" = var.project }
  common_tags = merge(local.project_tag, local.protect_tag)
}

# ---------------- VPC (1 public + 1 extra public for capacity, 2 private) ----------------
resource "aws_vpc" "main" {
  cidr_block           = "10.10.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = merge(local.common_tags, { Name = "${var.project}-vpc" })
}

resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.main.id
  tags   = merge(local.common_tags, { Name = "${var.project}-igw" })
}

# AZs
data "aws_availability_zones" "azs" {
  state = "available"
}

# ---------------- Subnets ----------------
# Original public subnet (kept)
resource "aws_subnet" "public_a" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.10.1.0/24"
  availability_zone       = data.aws_availability_zones.azs.names[0]
  map_public_ip_on_launch = true
  tags                    = merge(local.common_tags, { Name = "${var.project}-public-a" })
}

# NEW: second public subnet in another AZ to dodge capacity issues for t4g.micro
resource "aws_subnet" "public_b" {
  vpc_id     = aws_vpc.main.id
  cidr_block = "10.10.2.0/24"
  # pick a different AZ from index 0; typically this is eu-north-1b
  availability_zone       = length(data.aws_availability_zones.azs.names) > 1 ? data.aws_availability_zones.azs.names[1] : data.aws_availability_zones.azs.names[0]
  map_public_ip_on_launch = true
  tags                    = merge(local.common_tags, { Name = "${var.project}-public-b" })
}

resource "aws_subnet" "private_a" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.10.11.0/24"
  availability_zone = data.aws_availability_zones.azs.names[0]
  tags              = merge(local.common_tags, { Name = "${var.project}-private-a" })
}

resource "aws_subnet" "private_b" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.10.12.0/24"
  availability_zone = length(data.aws_availability_zones.azs.names) > 1 ? data.aws_availability_zones.azs.names[1] : data.aws_availability_zones.azs.names[0]
  tags              = merge(local.common_tags, { Name = "${var.project}-private-b" })
}

# ---------------- Routing ----------------
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.igw.id
  }

  tags = merge(local.common_tags, { Name = "${var.project}-public-rt" })
}

resource "aws_route_table_association" "pub_a" {
  subnet_id      = aws_subnet.public_a.id
  route_table_id = aws_route_table.public.id
}

# NEW: associate extra public subnet to the same public route table
resource "aws_route_table_association" "pub_b" {
  subnet_id      = aws_subnet.public_b.id
  route_table_id = aws_route_table.public.id
}

# ---------------- Security Groups ----------------
resource "aws_security_group" "ec2_sg" {
  name        = "${var.project}-ec2-sg"
  description = "Web ingress for app"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["86.150.28.115/32"] # replace with your IP or remove if not needed
    description = "SSH from your workstation (1 Hamond Sq)"
  }

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["82.132.234.136/32"] # replace with your IP or remove if not needed
    description = "SSH from your workstation (Tobys iPhone (2))"
  }

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["18.202.216.48/29"] # replace with your IP or remove if not needed
    description = "SSH from eu-north-1 AWS internal"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.common_tags
}

resource "aws_security_group" "rds_sg" {
  name   = "${var.project}-rds-sg"
  vpc_id = aws_vpc.main.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ec2_sg.id]
    description     = "Postgres from EC2 only"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.common_tags
}

# ---------------- SSM parameters ----------------
resource "aws_ssm_parameter" "db_password" {
  name  = "/${var.project}/db/password"
  type  = "SecureString"
  value = var.db_password
  tags  = local.common_tags
}

resource "aws_ssm_parameter" "firebase_sa_b64" {
  name  = "/${var.project}/firebase/sa_b64"
  type  = "SecureString"
  value = var.firebase_service_account_b64
  tags  = local.common_tags
}

# ---------------- RDS PostgreSQL (Free Tier micro) ----------------
resource "aws_db_subnet_group" "db_subnets" {
  name       = "${var.project}-db-subnets"
  subnet_ids = [aws_subnet.private_a.id, aws_subnet.private_b.id] # unchanged
  tags       = local.common_tags
}

resource "aws_db_instance" "pg" {
  identifier                 = "${var.project}-pg"
  engine                     = "postgres"
  engine_version             = "16.8"
  instance_class             = "db.t4g.micro" # Free Tier eligible
  allocated_storage          = 20
  storage_type               = "gp2"
  db_name                    = "appdb"
  username                   = "appuser"
  password                   = var.db_password
  vpc_security_group_ids     = [aws_security_group.rds_sg.id]
  db_subnet_group_name       = aws_db_subnet_group.db_subnets.name
  backup_retention_period    = 7
  skip_final_snapshot        = true
  publicly_accessible        = false
  deletion_protection        = false
  auto_minor_version_upgrade = true
  tags                       = local.common_tags
}

# ---------------- EC2 to run Docker Compose ----------------

data "aws_caller_identity" "me" {}

resource "aws_iam_role" "ec2_role" {
  name = "${var.project}-ec2-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect    = "Allow",
      Principal = { Service = "ec2.amazonaws.com" },
      Action    = "sts:AssumeRole"
    }]
  })
  tags = local.common_tags
}

resource "aws_iam_policy" "ec2_policy" {
  name = "${var.project}-ec2-ssm-logs"
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect   = "Allow",
        Action   = ["ssm:GetParameter", "ssm:GetParameters"],
        Resource = "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.me.account_id}:parameter/${var.project}/*"
      },
      {
        Effect   = "Allow",
        Action   = ["s3:GetObject"],
        Resource = "arn:aws:s3:::golfbeta-eu-north-1-videos-39695a/videos/*"
      },
      {
        Effect   = "Allow",
        Action   = ["s3:ListBucket"],
        Resource = "arn:aws:s3:::golfbeta-eu-north-1-videos-39695a",
        Condition = {
          StringLike = {
            "s3:prefix" = ["videos/*"]
          }
        }
      },
      {
        Effect   = "Allow",
        Action   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"],
        Resource = "*"
      }
    ]
  })
  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "attach" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = aws_iam_policy.ec2_policy.arn
}

resource "aws_iam_role_policy_attachment" "ec2_ssm_core" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "ecr_readonly" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_instance_profile" "ec2_profile" {
  name = "${var.project}-ec2-profile"
  role = aws_iam_role.ec2_role.name
}

resource "aws_instance" "app" {
  ami           = var.ami_id
  instance_type = "t3.micro" # Free Tier eligible
  # IMPORTANT: launch in the second public subnet (different AZ with capacity)
  subnet_id                   = aws_subnet.public_b.id
  vpc_security_group_ids      = [aws_security_group.ec2_sg.id]
  iam_instance_profile        = aws_iam_instance_profile.ec2_profile.name
  associate_public_ip_address = true
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  # Optional: only set a key if provided (lets you skip SSH)
  key_name = var.ssh_key_name != "" ? var.ssh_key_name : null

  user_data = templatefile("${path.module}/user_data.sh", {
    project        = var.project
    region         = var.aws_region
    rds_host       = aws_db_instance.pg.address
    db_name        = "appdb"
    db_user        = "appuser"
    ecr_image      = var.ecr_image
    domain_name    = var.domain_name
    email_for_lets = var.email_for_lets
  })

  user_data_replace_on_change = true

  tags = merge(local.common_tags, { Name = "${var.project}-ec2" })

  root_block_device {
    volume_size           = 20 # or 30 if you want headroom
    volume_type           = "gp3"
    delete_on_termination = true
  }
}

# ---------------- Outputs ----------------
output "rds_endpoint" {
  value = aws_db_instance.pg.address
}

output "rds_port" {
  value = aws_db_instance.pg.port
}

output "ssm_db_password" {
  value = aws_ssm_parameter.db_password.name
}
