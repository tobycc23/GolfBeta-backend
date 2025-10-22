// main.tf
terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.62" }
    archive = { source = "hashicorp/archive", version = "~> 2.4" }
  }
}

provider "aws" {
  region = var.aws_region
}

# ===== SNS for alerts =====
resource "aws_sns_topic" "budget_alerts" {
  name = "${var.project}-budget-alerts"
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.budget_alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# Lambda subscription for automatic stops on 100% budget
resource "aws_sns_topic" "budget_actions" {
  name = "${var.project}-budget-actions"
}

# ===== Lambda that stops EC2 & RDS with tag budget-protect=true =====
data "archive_file" "lambda_zip" {
  type        = "zip"
  source_dir  = "${path.module}/lambda"
  output_path = "${path.module}/lambda.zip"
}

resource "aws_iam_role" "lambda_role" {
  name = "${var.project}-budget-guard-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect = "Allow",
      Principal = { Service = "lambda.amazonaws.com" },
      Action = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_policy" "lambda_policy" {
  name = "${var.project}-budget-guard-policy"
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      { Effect = "Allow", Action = ["logs:CreateLogGroup","logs:CreateLogStream","logs:PutLogEvents"], Resource = "*" },
      { Effect = "Allow", Action = ["ec2:DescribeInstances","ec2:StopInstances","ec2:DescribeInstanceStatus"], Resource = "*" },
      { Effect = "Allow", Action = ["rds:DescribeDBInstances","rds:StopDBInstance"], Resource = "*" }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "attach" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = aws_iam_policy.lambda_policy.arn
}

resource "aws_lambda_function" "budget_guard" {
  function_name = "${var.project}-budget-guard"
  filename      = data.archive_file.lambda_zip.output_path
  handler       = "handler.handler"
  runtime       = "python3.12"
  timeout       = 60
  role          = aws_iam_role.lambda_role.arn
  environment {
    variables = {
      PROJECT_TAG_KEY   = "budget-protect"
      PROJECT_TAG_VALUE = "true"
    }
  }
}

# Subscribe the Lambda to the “actions” topic
resource "aws_sns_topic_subscription" "lambda_sub" {
  topic_arn = aws_sns_topic.budget_actions.arn
  protocol  = "lambda"
  endpoint  = aws_lambda_function.budget_guard.arn
}

resource "aws_lambda_permission" "allow_sns" {
  statement_id  = "AllowExecutionFromSNS"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.budget_guard.function_name
  principal     = "sns.amazonaws.com"
  source_arn    = aws_sns_topic.budget_actions.arn
}

# ===== $10 monthly cost budget with alerts and action trigger =====
resource "aws_budgets_budget" "monthly_cost" {
  name              = "${var.project}-monthly-gbp-cap"
  budget_type       = "COST"
  limit_amount      = var.monthly_budget_usd
  limit_unit        = "USD"
  time_unit         = "MONTHLY"
  time_period_start = formatdate("YYYY-MM-01_00:00", timestamp())

  # Heads-up at 60% (email only)
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 60
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.alert_email]
  }

  # Urgent at 80% (email only)
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.alert_email]
  }

  # “Cap” at 100% (notify email AND the Lambda topic)
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.alert_email]
    subscriber_sns_topic_arns  = [aws_sns_topic.budget_actions.arn]
  }

  # Also trigger the action on ACTUAL 100% just in case forecasting lags
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.alert_email]
    subscriber_sns_topic_arns  = [aws_sns_topic.budget_actions.arn]
  }
}