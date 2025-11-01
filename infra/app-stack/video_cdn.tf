# ---------------------------
# Private S3 bucket for videos (no CloudFront)
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
      }
      # No public allows. Access is via IAM (backend) and S3 pre-signed URLs.
    ]
  })
}

# ---- Outputs ----
output "video_bucket_name" {
  value       = aws_s3_bucket.videos.bucket
  description = "Private S3 bucket storing your videos"
}
