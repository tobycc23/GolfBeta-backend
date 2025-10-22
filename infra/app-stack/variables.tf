variable "aws_region" {
  type    = string
  default = "eu-north-1"
}

variable "project" {
  type    = string
  default = "golfbeta"
}

# Note: passing secrets via TF stores them in state. Prefer SSM CLI if you want to avoid that.
variable "db_password" {
  type = string
}

variable "firebase_service_account_b64" {
  type = string
}

variable "ssh_key_name" {
  type    = string
  default = "" # leave blank to skip SSH key attachment
}

variable "ecr_image" {
  type    = string
  default = "" # e.g. <acct>.dkr.ecr.eu-north-1.amazonaws.com/golfbeta-api:latest
}

variable "domain_name" {
  type    = string
  default = "" # optional for TLS
}

variable "email_for_lets" {
  type    = string
  default = "" # optional for TLS email
}

variable "price_class" {
  description = "CloudFront price class: PriceClass_100 | PriceClass_200 | PriceClass_All"
  type        = string
  default     = "PriceClass_100"
}

variable "enable_cors" {
  description = "Enable CORS on the S3 videos bucket (only if you upload directly from browsers/apps)"
  type        = bool
  default     = false
}

variable "cors_allowed_origins" {
  description = "Allowed origins for S3 uploads if enable_cors=true"
  type        = list(string)
  default     = ["http://localhost:19006"]
}