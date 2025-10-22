# Zip the lambda code folder
data "archive_file" "mc_zip" {
  type        = "zip"
  source_dir  = "${path.module}/lambda/mediaconvert_auto"
  output_path = "${path.module}/lambda/mediaconvert_auto.zip"
}

# Lambda execution role
resource "aws_iam_role" "lambda_mc_role" {
  name = "${var.project}-lambda-mediaconvert"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect    = "Allow",
      Principal = { Service = "lambda.amazonaws.com" },
      Action    = "sts:AssumeRole"
    }]
  })
}

# Permissions for Lambda: logs + MediaConvert + S3 I/O
resource "aws_iam_policy" "lambda_mc_policy" {
  name = "${var.project}-lambda-mediaconvert-policy"
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      { "Effect" : "Allow", "Action" : [
        "logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"
      ], "Resource" : "*" },
      { "Effect" : "Allow", "Action" : [
        "mediaconvert:CreateJob", "mediaconvert:DescribeEndpoints", "mediaconvert:ListPresets" 
      ], "Resource" : "*" },
      { "Effect" : "Allow", "Action" : ["s3:GetObject", "s3:ListBucket"],
        "Resource" : [
          aws_s3_bucket.videos.arn,
          "${aws_s3_bucket.videos.arn}/demo/*"
      ] },
      { "Effect" : "Allow", "Action" : ["s3:PutObject", "s3:PutObjectAcl", "s3:ListBucket", "s3:GetBucketLocation"],
        "Resource" : [
          aws_s3_bucket.videos.arn,
          "${aws_s3_bucket.videos.arn}/hls/*"
      ] },
      { "Effect" : "Allow",
        "Action" : "iam:PassRole",
        "Resource" : "${aws_iam_role.mediaconvert_role.arn}"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_mc_attach" {
  role       = aws_iam_role.lambda_mc_role.name
  policy_arn = aws_iam_policy.lambda_mc_policy.arn
}

# Lambda function
resource "aws_lambda_function" "mediaconvert_auto" {
  function_name    = "${var.project}-mediaconvert-auto"
  role             = aws_iam_role.lambda_mc_role.arn
  runtime          = "python3.12"
  handler          = "index.handler"
  filename         = data.archive_file.mc_zip.output_path
  source_code_hash = data.archive_file.mc_zip.output_base64sha256
  timeout          = 60
  memory_size      = 256
  environment {
    variables = {
      MEDIACONVERT_ROLE_ARN = aws_iam_role.mediaconvert_role.arn
    }
  }
  tags = local.common_tags
}

# Allow S3 to invoke the Lambda
resource "aws_lambda_permission" "allow_s3_invoke" {
  statement_id  = "AllowS3Invoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.mediaconvert_auto.arn
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.videos.arn
}

# S3 event: new .mp4 under demo/
# NOTE: Only one aws_s3_bucket_notification resource is allowed per bucket.
resource "aws_s3_bucket_notification" "videos_notify" {
  bucket = aws_s3_bucket.videos.id

  lambda_function {
    lambda_function_arn = aws_lambda_function.mediaconvert_auto.arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "demo/"
    filter_suffix       = ".mp4"
  }

  depends_on = [aws_lambda_permission.allow_s3_invoke]
}
