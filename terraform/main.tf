# Configure the AWS provider
# Tells Terraform which cloud and region to use
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# Persistent EBS volume lookup.
# The volume is created and retained outside this stack so Terraform destroy
# only removes the EC2 instance and its attachment.
data "aws_ebs_volume" "trading_data" {
  filter {
    name   = "volume-id"
    values = [var.trading_data_volume_id]
  }
}

# Explicit subnet selection keeps the EC2 instance in the same AZ as the
# retained EBS volume. Without this, AWS may launch the instance elsewhere.
data "aws_subnet" "trading_engine" {
  id = var.trading_engine_subnet_id
}

# ── IAM ROLE (solves Issue 3) ────────────────────────────────────────
# Instead of storing static AWS credentials on EC2 (aws configure),
# we attach an IAM role so EC2 gets auto-rotating temporary credentials
# EC2 can then talk to ECR without any credentials file on disk

resource "aws_iam_role" "ec2_ecr_role" {
  name = "ec2-ecr-read-role"

  # Trust policy — allows EC2 service to assume this role
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

# Attach ECR read-only policy to the role
# EC2 can pull images from ECR but cannot push or delete
resource "aws_iam_role_policy_attachment" "ecr_read" {
  role       = aws_iam_role.ec2_ecr_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# Instance profile — container for the IAM role
# EC2 uses instance profiles, not roles directly
resource "aws_iam_instance_profile" "ec2_profile" {
  name = "ec2-ecr-profile"
  role = aws_iam_role.ec2_ecr_role.name
}

# ── SECURITY GROUP (solves Issue 1) ──────────────────────────────────
# Defines firewall rules for EC2
# Previously we had to manually edit inbound rules in the console
# Now it's defined in code — always correct from day one

resource "aws_security_group" "trading_engine_sg" {
  name        = "trading-engine-sg"
  description = "Security group for trading engine EC2"

  # Allow SSH from anywhere — needed for CI/CD to SSH in
  # Safe because EC2 still requires .pem key to authenticate
  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Allow HTTP traffic on port 8080 — the trading engine API
  ingress {
    description = "Trading engine API"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Allow external access to trading engine UI and API
  ingress {
    description = "Trading engine NodePort"
    from_port   = 30080
    to_port     = 30080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Allow all outbound traffic — needed to pull from ECR, talk to AWS
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ── EC2 INSTANCE ─────────────────────────────────────────────────────
# The main server that runs the trading engine
# Amazon Linux 2 AMI — ec2-user is always the correct username (solves Issue 2)
# IAM profile attached — no aws configure needed (solves Issue 3)
# userdata.sh runs at boot — fixes kubectl permissions (solves Issue 4)
#                          — configures ECR pull via IAM (solves Issue 5)

resource "aws_instance" "trading_engine" {
  # Amazon Linux 2 AMI for eu-north-1
  # Always use Amazon Linux 2 so username is always ec2-user
  ami           = "ami-015869825c7c8ac64"
  instance_type = var.instance_type

  # SSH key pair for access — must exist in AWS already
  key_name = var.key_pair_name

  # Pin EC2 to the subnet that matches the persistent EBS volume's AZ.
  subnet_id = data.aws_subnet.trading_engine.id

  # Attach IAM role via instance profile
  iam_instance_profile = aws_iam_instance_profile.ec2_profile.name

  # Attach security group
  vpc_security_group_ids = [aws_security_group.trading_engine_sg.id]

  # Bootstrap script — runs once at launch
  # Installs Docker, k3s, fixes permissions, configures ECR
  user_data = replace(
    file("${path.module}/userdata.sh"),
    "__TRADING_DATA_VOLUME_ID__",
    var.trading_data_volume_id
  )

  # Storage — 20GB is enough for the app + Docker images
  root_block_device {
    volume_size = 20
    volume_type = "gp3"
  }

  lifecycle {
    precondition {
      condition     = data.aws_subnet.trading_engine.availability_zone == data.aws_ebs_volume.trading_data.availability_zone
      error_message = "trading_engine_subnet_id must be in the same Availability Zone as trading_data_volume_id."
    }
  }

  tags = {
    Name = "trading-engine-prod"
  }
}

# ── ELASTIC IP (fixes changing IP problem) ───────────────────────────
# Pre-allocated manually in AWS console — survives terraform destroy/apply
# IP: 13.53.204.117 | Allocation ID: eipalloc-007082707b505ad9a
# EC2_HOST GitHub secret never needs updating after first setup
# IP: 13.53.204.117 | Allocation ID: eipalloc-007082707b505ad9a
# This means EC2_HOST never changes after first setup
data "aws_eip" "trading_engine_ip" {
  id = "eipalloc-007082707b505ad9a"
}

# Associate the pre-existing EIP with EC2
# Uses aws_eip_association so Terraform manages the attachment
# but NOT the EIP itself — destroy won't release it
resource "aws_eip_association" "trading_engine_ip" {
  instance_id   = aws_instance.trading_engine.id
  allocation_id = data.aws_eip.trading_engine_ip.id
}


# ── EBS ATTACHMENT (persistent volume managed outside this stack) ────
# The volume already exists. Terraform only manages its attachment to the
# current EC2 instance so destroy/apply cycles keep the data disk.
resource "aws_volume_attachment" "trading_data_attachment" {
  device_name = "/dev/sdf"
  volume_id   = data.aws_ebs_volume.trading_data.id
  instance_id = aws_instance.trading_engine.id
}
