// outputs.tf
output "sns_budget_alerts_topic" {
  value = aws_sns_topic.budget_alerts.arn
}

output "sns_budget_actions_topic" {
  value = aws_sns_topic.budget_actions.arn
}

output "lambda_budget_guard" {
  value = aws_lambda_function.budget_guard.arn
}
