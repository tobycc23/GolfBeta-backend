# ---------------------------
# Private S3 bucket for videos with CloudFront distribution
# ---------------------------

# Unique suffix so the S3 bucket name doesn't collide globally
resource "random_id" "videos_suffix" {
  byte_length = 3
}

# ---- S3 bucket (private) to store videos ----
resource "aws_s3_bucket" "videos" {
  bucket        = "${var.project}-${var.aws_region}-videos-${random_id.videos_suffix.hex}"
  force_destroy = false
  tags          = local.common_tags
}

resource "aws_s3_bucket" "cloudfront_logs" {
  provider      = aws.us_east_1
  bucket        = "${var.project}-cf-logs-${random_id.videos_suffix.hex}"
  force_destroy = false
  tags          = local.common_tags
}

resource "aws_s3_bucket_ownership_controls" "cloudfront_logs" {
  provider = aws.us_east_1
  bucket   = aws_s3_bucket.cloudfront_logs.id

  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

resource "aws_s3_bucket_acl" "cloudfront_logs" {
  provider   = aws.us_east_1
  depends_on = [aws_s3_bucket_ownership_controls.cloudfront_logs]
  bucket     = aws_s3_bucket.cloudfront_logs.id
  acl        = "private"
}

resource "aws_s3_bucket_public_access_block" "cloudfront_logs" {
  provider                = aws.us_east_1
  bucket                  = aws_s3_bucket.cloudfront_logs.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_policy" "cloudfront_logs" {
  provider = aws.us_east_1
  bucket   = aws_s3_bucket.cloudfront_logs.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Sid       = "AllowCloudFrontLogsDelivery",
        Effect    = "Allow",
        Principal = { Service = "delivery.logs.amazonaws.com" },
        Action    = "s3:PutObject",
        Resource  = "${aws_s3_bucket.cloudfront_logs.arn}/cloudfront/*",
        Condition = {
          StringEquals = {
            "s3:x-amz-acl" = "bucket-owner-full-control"
          }
        }
      },
      {
        Sid       = "AllowCloudFrontLogsAclCheck",
        Effect    = "Allow",
        Principal = { Service = "delivery.logs.amazonaws.com" },
        Action    = "s3:GetBucketAcl",
        Resource  = aws_s3_bucket.cloudfront_logs.arn
      }
    ]
  })
}

# Signing key material for CloudFront signed URLs
resource "tls_private_key" "videos_signing" {
  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "aws_cloudfront_public_key" "videos" {
  name        = "${var.project}-videos-public-key"
  comment     = "Signing key for ${var.project} videos distribution"
  encoded_key = tls_private_key.videos_signing.public_key_pem
}

resource "aws_cloudfront_key_group" "videos" {
  name    = "${var.project}-videos-key-group"
  comment = "Trusted key group for ${var.project} videos distribution"
  items   = [aws_cloudfront_public_key.videos.id]
}

# CloudWatch request metrics (includes bytes downloaded/uploaded)
resource "aws_s3_bucket_metric" "videos_request_metrics" {
  bucket = aws_s3_bucket.videos.id
  name   = "EntireBucket"
}

# Versioning (useful for safety; lifecycle below will trim old versions)
resource "aws_s3_bucket_versioning" "videos" {
  bucket = aws_s3_bucket.videos.id
  versioning_configuration { status = "Enabled" }
}

# Block all public access
resource "aws_s3_bucket_public_access_block" "videos" {
  bucket                  = aws_s3_bucket.videos.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Server-side encryption by default
resource "aws_s3_bucket_server_side_encryption_configuration" "videos" {
  bucket = aws_s3_bucket.videos.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Keep costs tidy if you replace files often
resource "aws_s3_bucket_lifecycle_configuration" "videos" {
  bucket = aws_s3_bucket.videos.id

  rule {
    id     = "expire-old-versions"
    status = "Enabled"
    filter { prefix = "" }
    noncurrent_version_expiration { noncurrent_days = 30 }
  }

  rule {
    id     = "abort-mpu"
    status = "Enabled"
    filter { prefix = "" }
    abort_incomplete_multipart_upload { days_after_initiation = 7 }
  }
}

# Only needed if you will upload directly to S3 from a browser/app
resource "aws_s3_bucket_cors_configuration" "videos" {
  count  = var.enable_cors ? 1 : 0
  bucket = aws_s3_bucket.videos.id
  cors_rule {
    allowed_methods = ["GET", "HEAD", "PUT", "POST"]
    allowed_origins = var.cors_allowed_origins
    allowed_headers = ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3600
  }
}

# ---- Bucket policy (keep private; deny non-HTTPS) ----
resource "aws_s3_bucket_policy" "videos" {
  bucket = aws_s3_bucket.videos.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Sid       = "DenyInsecureTransport",
        Effect    = "Deny",
        Principal = "*",
        Action    = "s3:*",
        Resource = [
          aws_s3_bucket.videos.arn,
          "${aws_s3_bucket.videos.arn}/*"
        ],
        Condition = { Bool = { "aws:SecureTransport" : "false" } }
      },
      {
        Sid    = "AllowCloudFrontServicePrincipalRead",
        Effect = "Allow",
        Principal = {
          Service = "cloudfront.amazonaws.com"
        },
        Action = [
          "s3:GetObject"
        ],
        Resource = "${aws_s3_bucket.videos.arn}/*",
        Condition = {
          StringEquals = {
            "AWS:SourceArn" = aws_cloudfront_distribution.videos.arn
          }
        }
      }
      # No public allows. Access is via IAM (backend) and S3 pre-signed URLs.
    ]
  })
}

# Origin access control for CloudFront -> S3
resource "aws_cloudfront_origin_access_control" "videos" {
  name                              = "${var.project}-videos-oac"
  description                       = "Origin access control for ${var.project} videos bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# CloudFront distribution in front of the S3 bucket
resource "aws_cloudfront_distribution" "videos" {
  enabled         = true
  is_ipv6_enabled = true
  comment         = "${var.project} videos CDN"
  price_class     = "PriceClass_100"

  origin {
    domain_name              = aws_s3_bucket.videos.bucket_regional_domain_name
    origin_id                = "videos-s3-origin"
    origin_access_control_id = aws_cloudfront_origin_access_control.videos.id
  }

  default_cache_behavior {
    target_origin_id       = "videos-s3-origin"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true
    trusted_key_groups     = [aws_cloudfront_key_group.videos.id]

    forwarded_values {
      query_string = true
      cookies {
        forward = "none"
      }
    }

    # Cache for 5 minutes by default, allow up to 24h if object headers permit it.
    min_ttl     = 0
    default_ttl = 300
    max_ttl     = 86400
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }

  logging_config {
    include_cookies = false
    bucket          = "${aws_s3_bucket.cloudfront_logs.bucket}.s3.amazonaws.com"
    prefix          = "cloudfront/"
  }

  tags = local.common_tags
}

resource "aws_ssm_parameter" "cloudfront_private_key_b64" {
  name        = "/${var.project}/cloudfront/private_key_b64"
  description = "Base64-encoded private key for signing CloudFront URLs"
  type        = "SecureString"
  value       = base64encode(tls_private_key.videos_signing.private_key_pem)
  overwrite   = true
  tags        = local.common_tags
}

resource "aws_ssm_parameter" "cloudfront_key_pair_id" {
  name        = "/${var.project}/cloudfront/key_pair_id"
  description = "CloudFront public key ID used for signed URLs"
  type        = "String"
  value       = aws_cloudfront_public_key.videos.id
  overwrite   = true
  tags        = local.common_tags
}

# ---- Outputs ----
output "video_bucket_name" {
  value       = aws_s3_bucket.videos.bucket
  description = "Private S3 bucket storing your videos"
}

output "video_cdn_domain_name" {
  value       = aws_cloudfront_distribution.videos.domain_name
  description = "CloudFront distribution domain serving video content"
}

output "video_cdn_key_pair_id_parameter_name" {
  value       = aws_ssm_parameter.cloudfront_key_pair_id.name
  description = "SSM parameter storing the CloudFront public key ID used for signing URLs"
}

output "video_cdn_private_key_parameter_name" {
  value       = aws_ssm_parameter.cloudfront_private_key_b64.name
  description = "SSM parameter storing the base64-encoded CloudFront private key"
}
