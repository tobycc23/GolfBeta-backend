# ---------------------------
# Video CDN stack: S3 (private) + CloudFront (OAC)
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

resource "aws_s3_bucket_versioning" "videos" {
  bucket = aws_s3_bucket.videos.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_public_access_block" "videos" {
  bucket                  = aws_s3_bucket.videos.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
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

# ---- CloudFront + Origin Access Control (keeps bucket private) ----
resource "aws_cloudfront_origin_access_control" "oac" {
  name                              = "${var.project}-${var.aws_region}-oac"
  description                       = "OAC for ${aws_s3_bucket.videos.bucket}"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "videos_cdn" {
  enabled         = true
  comment         = "${var.project} videos CDN"
  is_ipv6_enabled = true
  price_class     = var.price_class

  origin {
    origin_id                = "s3-videos"
    domain_name              = aws_s3_bucket.videos.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.oac.id
  }

  default_cache_behavior {
    target_origin_id       = "s3-videos"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    # Use AWS managed policies instead of legacy ForwardedValues
    # CachingOptimized
    cache_policy_id = "658327ea-f89d-4fab-a63d-7e88639e58f6"
    # AllViewer (no headers/cookies/qs forwarding unless needed later)
    origin_request_policy_id = "b689b0a8-53d0-40ab-baf2-68738e2966ac" # AllViewerExceptHostHeader

    min_ttl     = 0
    default_ttl = 3600
    max_ttl     = 86400
  }

  restrictions {
    geo_restriction { restriction_type = "none" }
  }

  viewer_certificate { cloudfront_default_certificate = true }

  tags = local.common_tags
}

# Bucket policy: allow ONLY this CloudFront distribution via OAC
data "aws_caller_identity" "videos" {}

resource "aws_s3_bucket_policy" "videos" {
  bucket = aws_s3_bucket.videos.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Sid       = "AllowCloudFrontOACRead",
        Effect    = "Allow",
        Principal = { "Service" : "cloudfront.amazonaws.com" },
        Action    = ["s3:GetObject"],
        Resource  = "${aws_s3_bucket.videos.arn}/*",
        Condition = {
          StringEquals = {
            # CloudFront is global; no region in ARN
            "AWS:SourceArn" : "arn:aws:cloudfront::${data.aws_caller_identity.videos.account_id}:distribution/${aws_cloudfront_distribution.videos_cdn.id}"
          }
        }
      }
    ]
  })
}

# Outputs 

output "video_bucket_name" {
  value       = aws_s3_bucket.videos.bucket
  description = "Private S3 bucket storing your videos"
}

output "videos_cdn_domain" {
  value       = aws_cloudfront_distribution.videos_cdn.domain_name
  description = "Use this CloudFront domain to serve videos"
}