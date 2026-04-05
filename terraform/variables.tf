# AWS region where all resources will be created
variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "eu-north-1"
}

# Your AWS account ID — used to construct ECR URI
variable "aws_account_id" {
  description = "AWS account ID"
  type        = string
  default     = "020880439584"
}

# EC2 instance type — t3.small is enough for this project
variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t3.small"
}

# Existing EBS volume that stores trading engine data.
# This volume is managed outside this Terraform stack so destroy/apply cycles
# do not delete the persisted command log and trade history.
variable "trading_data_volume_id" {
  description = "Existing persistent EBS volume ID for trading engine data"
  type        = string
  default     = "vol-0fc7e2b6e508420f6"
}

# Subnet used for the EC2 instance.
# It must be in the same Availability Zone as the persistent EBS volume.
variable "trading_engine_subnet_id" {
  description = "Subnet ID for the trading engine EC2 instance"
  type        = string
  default     = "subnet-0c1e2152461a02c8b"
}

# Name of your existing EC2 key pair for SSH access (.pem key)
variable "key_pair_name" {
  description = "EC2 key pair name"
  type        = string
}
