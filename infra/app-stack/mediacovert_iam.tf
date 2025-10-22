# IAM role MediaConvert will assume
resource "aws_iam_role" "mediaconvert_role" {
  name = "${var.project}-mediaconvert-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect : "Allow",
      Principal : { Service : "mediaconvert.amazonaws.com" },
      Action : "sts:AssumeRole"
    }]
  })
  tags = local.common_tags
}

# Allow read from input prefix and write to output prefix, plus logs
data "aws_caller_identity" "acct" {}

resource "aws_iam_policy" "mediaconvert_policy" {
  name = "${var.project}-mediaconvert-io"
  policy = jsonencode({
    Version : "2012-10-17",
    Statement : [
      {
        Sid : "ReadInput",
        Effect : "Allow",
        Action : ["s3:GetObject", "s3:ListBucket", "s3:GetBucketLocation"],
        Resource : [
          "arn:aws:s3:::${aws_s3_bucket.videos.bucket}",
          "arn:aws:s3:::${aws_s3_bucket.videos.bucket}/demo/*"
        ]
      },
      {
        Sid : "WriteOutput",
        Effect : "Allow",
        Action : ["s3:PutObject", "s3:PutObjectAcl", "s3:ListBucket", "s3:GetBucketLocation"],
        Resource : [
          "arn:aws:s3:::${aws_s3_bucket.videos.bucket}",
          "arn:aws:s3:::${aws_s3_bucket.videos.bucket}/hls/*"
        ]
      },
      {
        Sid : "CloudWatchLogs",
        Effect : "Allow",
        Action : ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"],
        Resource : "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "mediaconvert_attach" {
  role       = aws_iam_role.mediaconvert_role.name
  policy_arn = aws_iam_policy.mediaconvert_policy.arn
}

# Output the role ARN for the CLI command in step 3
output "mediaconvert_role_arn" {
  value       = aws_iam_role.mediaconvert_role.arn
  description = "Role for AWS Elemental MediaConvert"
}
