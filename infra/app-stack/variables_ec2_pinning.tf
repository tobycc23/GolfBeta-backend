variable "ami_id" {
  description = "Pinned AMI for the app EC2"
  type        = string
  default     = "ami-0d041a2e640771b52" # from your last apply output
}