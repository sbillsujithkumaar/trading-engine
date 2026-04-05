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
# Terraform attaches the retained EBS volume after EC2 starts booting.
# Wait for the specific volume by ID so we don't guess based on unstable NVMe
# numbering and accidentally probe or format the wrong disk.
echo "=== Setting up EBS volume ==="

EBS_ID="__TRADING_DATA_VOLUME_ID__"
EBS_ID_NODASH="${EBS_ID//-/}"
STABLE_DEV="/dev/disk/by-id/nvme-Amazon_Elastic_Block_Store_${EBS_ID_NODASH}"

echo "=== Waiting for EBS volume ${EBS_ID} to be attached ==="
udevadm settle --exit-if-exists="${STABLE_DEV}" --timeout=60 || {
  echo "ERROR: EBS volume ${EBS_ID} did not appear within 60 seconds"
  exit 1
}
echo "EBS volume attached at ${STABLE_DEV}"

if fs_type=$(blkid -p -o value -s TYPE "${STABLE_DEV}" 2>/dev/null); then
  echo "=== EBS volume already formatted (${fs_type}), skipping ==="
else
  echo "=== Formatting EBS volume (first time) ==="
  mkfs.ext4 -F "${STABLE_DEV}"
  echo "=== EBS volume formatted ==="
fi

VOL_UUID=$(blkid -p -o value -s UUID "${STABLE_DEV}")
echo "EBS volume UUID: ${VOL_UUID}"

mkdir -p /mnt/trading-data

if ! mountpoint -q /mnt/trading-data; then
  mount "UUID=${VOL_UUID}" /mnt/trading-data
  echo "=== EBS volume mounted at /mnt/trading-data ==="
else
  echo "=== EBS volume already mounted at /mnt/trading-data ==="
fi

if ! grep -q "^UUID=${VOL_UUID} /mnt/trading-data " /etc/fstab; then
  echo "UUID=${VOL_UUID} /mnt/trading-data ext4 defaults,nofail 0 2" >> /etc/fstab
  echo "=== fstab entry added ==="
else
  echo "=== fstab entry already present ==="
fi

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
