// variables.tf
variable "aws_region" {
  description = "Region to deploy (London by default)."
  type        = string
  default     = "eu-west-2"
}

variable "project" {
  description = "Project name for tagging."
  type        = string
  default     = "golfbeta"
}

variable "alert_email" {
  description = "Email to receive budget/anomaly alerts."
  type        = string
}

variable "monthly_budget_gbp" {
  description = "Monthly soft cap."
  type        = string
  default     = "10"
}

variable "monthly_budget_usd" {
  description = "Monthly soft cap in USD (AWS Budgets only supports USD)."
  type        = string
  default     = "13"
}