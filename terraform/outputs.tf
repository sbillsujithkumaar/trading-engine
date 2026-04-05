output "ec2_public_ip" {
  description = "Fixed public IP — pre-allocated, survives terraform destroy/apply"
  value       = data.aws_eip.trading_engine_ip.public_ip
}

output "ecr_uri" {
  description = "ECR repository URI"
  value       = "${var.aws_account_id}.dkr.ecr.${var.aws_region}.amazonaws.com/trading-engine"
}

output "ssh_command" {
  description = "SSH command to connect to EC2"
  value       = "ssh -i your-key.pem ec2-user@${data.aws_eip.trading_engine_ip.public_ip}"
}
