#!/bin/bash
# Bootstrap script for trading engine EC2
# Runs automatically on first boot via Terraform userdata

set -e  # stop on any error
exec > /var/log/userdata.log 2>&1  # log everything to a file

echo "=== Starting bootstrap ==="

# ── STEP 1: Install dependencies ─────────────────────────────────────
echo "=== Installing dependencies ==="

# Install git and curl normally
yum install -y git curl wget

# Install Docker the correct way on Amazon Linux 2
amazon-linux-extras install docker -y

# Start and enable Docker
systemctl enable docker
systemctl start docker
usermod -aG docker ec2-user

echo "=== Docker installed ==="

# ── STEP 2: Install ECR credential provider ──────────────────────────
echo "=== Installing ECR credential provider ==="
wget https://github.com/dntosas/ecr-credential-provider/releases/download/v1.2.0/ecr-credential-provider-linux-amd64
chmod +x ecr-credential-provider-linux-amd64

# Place in k3s's default credential provider directory
# k3s automatically picks this up — no --kubelet-arg flags needed
# This avoids the KubeletCredentialProviders feature gate crash in k3s v1.28+
mkdir -p /var/lib/rancher/credentialprovider/bin
mv ecr-credential-provider-linux-amd64 /var/lib/rancher/credentialprovider/bin/ecr-credential-provider

# Config file in k3s's default location
mkdir -p /var/lib/rancher/credentialprovider
cat <<EOF > /var/lib/rancher/credentialprovider/config.yaml
apiVersion: kubelet.config.k8s.io/v1
kind: CredentialProviderConfig
providers:
  - name: ecr-credential-provider
    matchImages:
      - "*.dkr.ecr.*.amazonaws.com"
    defaultCacheDuration: "12h"
    apiVersion: credentialprovider.kubelet.k8s.io/v1
EOF

echo "=== ECR credential provider installed ==="

# ── STEP 3: Install k3s ──────────────────────────────────────────────
# No --kubelet-arg flags needed — k3s auto-detects credential provider
# from /var/lib/rancher/credentialprovider/
echo "=== Installing k3s ==="
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--disable traefik" INSTALL_K3S_SKIP_SELINUX_RPM=true sh -

# Wait for k3s API server to be fully ready
# k3s.yaml appears quickly but API server takes longer to start
echo "=== Waiting for k3s to be ready ==="
for i in $(seq 1 60); do
  if kubectl --kubeconfig=/etc/rancher/k3s/k3s.yaml get nodes > /dev/null 2>&1; then
    echo "k3s API server ready after ${i} seconds"
    break
  fi
  sleep 2
done

echo "=== k3s installed ==="

# ── STEP 4: Fix kubectl permissions (solves Issue 4) ─────────────────
# Copy kubeconfig to ec2-user so kubectl works without sudo
# Previously we had to add sudo to every kubectl command in ci.yml
# Now ec2-user owns the config — no sudo needed ever
echo "=== Setting up kubeconfig ==="
mkdir -p /home/ec2-user/.kube
cp /etc/rancher/k3s/k3s.yaml /home/ec2-user/.kube/config
chown ec2-user:ec2-user /home/ec2-user/.kube/config
chmod 600 /home/ec2-user/.kube/config
# Set KUBECONFIG permanently for ec2-user
echo 'export KUBECONFIG=/home/ec2-user/.kube/config' >> /home/ec2-user/.bashrc

echo "=== kubeconfig set up ==="

# ── STEP 5: Format and mount EBS volume ──────────────────────────────
# Known Terraform issue: EBS volumes attach AFTER EC2 boots
# so userdata must wait for the volume to be ready
# We temporarily disable set -e here because blkid returns non-zero
# on unformatted volumes — which would kill the script with set -e active
echo "=== Setting up EBS volume ==="

# Temporarily disable exit-on-error for EBS setup
# blkid returns non-zero on unformatted volumes — this is expected, not an error
set +e

# Wait for EBS device to be attached by Terraform
echo "=== Waiting for EBS device to appear ==="
for i in $(seq 1 60); do
  if [ -e /dev/nvme1n1 ]; then
    echo "EBS device present after ${i} seconds"
    break
  fi
  sleep 2
done

# Wait for filesystem metadata to be readable
echo "=== Waiting for EBS filesystem to be readable ==="
for i in $(seq 1 30); do
  result=$(sudo blkid /dev/nvme1n1 2>/dev/null)
  if [ -n "$result" ]; then
    echo "EBS filesystem readable after ${i} seconds"
    break
  fi
  sleep 1
done

# Check if volume already has a filesystem
HAS_FS=$(sudo blkid /dev/nvme1n1 2>/dev/null | grep -c "TYPE")

# Re-enable exit-on-error
set -e

if [ "$HAS_FS" -eq 0 ]; then
  # No filesystem found — format it (first time only)
  mkfs -t ext4 /dev/nvme1n1
  echo "=== EBS volume formatted ==="
else
  echo "=== EBS volume already formatted, skipping ==="
fi

# Create mount point and mount
mkdir -p /mnt/trading-data
mount /dev/nvme1n1 /mnt/trading-data
echo '/dev/nvme1n1 /mnt/trading-data ext4 defaults,nofail 0 2' >> /etc/fstab
echo "=== EBS volume mounted at /mnt/trading-data ==="

# ── STEP 6: Apply Kubernetes manifests ───────────────────────────────
# EBS volume must be mounted first so PVC binds to /mnt/trading-data
# not to k3s default local-path storage
# Clone repo temporarily just to apply k8s manifests
# Deleted immediately after — EC2 doesn't need the source code
echo "=== Applying k8s manifests ==="
git clone https://github.com/sbillsujithkumaar/trading-engine.git /tmp/trading-engine
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl apply -f /tmp/trading-engine/k8s/
rm -rf /tmp/trading-engine

echo "=== k8s manifests applied ==="

echo "=== Bootstrap complete. EC2 is ready ==="
