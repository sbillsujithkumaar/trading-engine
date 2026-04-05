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

# Name of your existing EC2 key pair for SSH access (.pem key)
variable "key_pair_name" {
  description = "EC2 key pair name"
  type        = string
}