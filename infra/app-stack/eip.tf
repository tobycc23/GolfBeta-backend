# Allocate an Elastic IP in your VPC
resource "aws_eip" "app" {
  domain = "vpc"
  tags   = local.common_tags
}

# Always (re)attach the same EIP to the instance
resource "aws_eip_association" "app" {
  instance_id   = aws_instance.app.id
  allocation_id = aws_eip.app.id
}

# Use the stable EIP in outputs (replace your existing ec2_public_ip output)
output "ec2_public_ip" {
  value       = aws_eip.app.public_ip
  description = "Stable Elastic IP for the app EC2"
}