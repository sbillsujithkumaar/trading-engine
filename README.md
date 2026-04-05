# Trading Engine

## Overview

Trading Engine is a single-node deterministic limit-order matching service built in Java 21. It accepts `BUY` and `SELL` limit orders plus cancellations, matches orders using price-time priority, persists accepted commands to an append-only write-ahead log, and reconstructs in-memory state on every boot by replaying that log.

The project is deliberately designed around a single authoritative process:

- `tradingengine.App` wires the runtime, verifies the write-ahead log, replays state, starts analytics, and launches Jetty.
- `tradingengine.matchingengine.MatchingEngine` owns matching orchestration.
- `tradingengine.persistence.CommandLog` is the authoritative append-only command history.
- `tradingengine.persistence.FileTradeStore` persists `trades.csv`, but that file is derived from replay and is not the source of truth.
- The browser UI is thin. HTTP snapshot endpoints are authoritative; WebSocket is a push signal that tells the UI to refetch snapshots.

Key technical properties:

- Deterministic matching with price-time priority.
- Append-only WAL stored as JSON lines.
- Hash-chained WAL records for tamper detection.
- Crash-durable WAL appends via `FileChannel.force(true)`.
- Replay-based recovery on every boot.
- Replay-stable trade timestamps derived from the incoming order timestamp.
- Single-node Kubernetes deployment on k3s, backed by a retained EBS volume.

---

## Architecture

There are two distinct paths through the system: the **bootstrap path** (first boot after `terraform apply`) and the **CI/CD deploy path** (every `git push` to main).

```
┌─────────────────────────────────────────────────────────────────────────┐
│ GitHub Actions                                                           │
│                                                                          │
│  [test] ──► [publish-image] ──────────────────────► [deploy]            │
│                    │                                     │              │
│                    │ push :sha (immutable)                │              │
│                    │ push :prod (bootstrap fallback)      │ SSH +        │
│                    │                                     │ kubectl      │
└────────────────────┼─────────────────────────────────────┼──────────────┘
                     │                                     │
                     ▼                                     │
        ┌────────────────────────┐                         │
        │ Amazon ECR             │                         │
        │ trading-engine repo    │                         │
        │                        │                         │
        │  :sha-abc123 ◄─────────┘                         │
        │  :prod       (bootstrap fallback only)           │
        └────────────┬───────────┘                         │
                     │                                     │
     pull via IAM    │                                     │
     role + ECR      │                                     │
     cred provider   │                                     │
                     ▼                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│ EC2  (Amazon Linux 2 — eu-north-1c)                                    │
│ Elastic IP: stable public endpoint, allocated outside Terraform        │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ k3s single-node cluster                                          │  │
│  │                                                                  │  │
│  │  Deployment/trading-engine                                       │  │
│  │  ├── strategy: Recreate  (ReadWriteOnce PVC constraint)          │  │
│  │  ├── imagePullPolicy: Always                                     │  │
│  │  ├── liveness  → /health                                         │  │
│  │  └── readiness → /ready                                          │  │
│  │                                                                  │  │
│  │  Service/trading-engine-svc  (NodePort 30080)                    │  │
│  │                                                                  │  │
│  │  PVC/trading-engine-pvc                                          │  │
│  │   └── PV/trading-engine-pv  (local-storage)                     │  │
│  │        └── /mnt/trading-data  ──────────────────────────────┐   │  │
│  └─────────────────────────────────────────────────────────────┼───┘  │
│                                                                 │ mount │
│  ┌──────────────────────────────────────────────────────────────▼────┐  │
│  │ EBS Volume — allocated OUTSIDE Terraform, survives destroy        │  │
│  │ mounted at /mnt/trading-data                                      │  │
│  │                                                                   │  │
│  │  commands.log   ← authoritative WAL (hash-chained, fsync'd)      │  │
│  │  trades.csv     ← derived, cleared and rebuilt on every boot     │  │
│  │  analytics.csv  ← derived, rebuilt every 30 seconds              │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  Bootstrap path (userdata.sh, runs once at first boot):                 │
│  install deps → ECR cred provider → k3s → kubeconfig →                 │
│  mount EBS → apply k8s manifests                                        │
└─────────────────────────────────────────────────────────────────────────┘

EXTERNAL RESOURCES (survive terraform destroy):
  Elastic IP   — pre-allocated manually, referenced via data source
  EBS Volume   — pre-existing, referenced via data source + var.trading_data_volume_id
```

---

## Application

### Core runtime and classes

The runtime starts in `app/src/main/java/tradingengine/App.java`. `tradingengine.App` performs all top-level assembly:

- Creates `EventDispatcher`.
- Registers `MarketDataBroadcaster` as a listener for `TradeExecutedEvent` and `OrderBookEvent`.
- Resolves `commands.log`, `trades.csv`, and `analytics.csv` under `DATA_DIR`.
- Builds `CommandLog`, `FileTradeStore`, `AnalyticsStore`, and `MatchingEngine`.
- Verifies WAL integrity before replay.
- Clears `trades.csv` and replays `commands.log` to rebuild engine state.
- Starts `AnalyticsJob` immediately, then every 30 seconds.
- Starts Jetty through `tradingengine.websocket.WebSocketServer`.
- Flips readiness only after replay and initial analytics complete.

Core classes and responsibilities:

| Class / File | Responsibility |
| --- | --- |
| `tradingengine.App` | Process entry point, dependency wiring, replay, analytics scheduler, server startup |
| `tradingengine.matchingengine.MatchingEngine` | Orchestrates submit/cancel, matching loop, trade creation, event publication, WAL appends |
| `tradingengine.book.OrderBook` | Owns bid side, ask side, and order index for cancel-by-id |
| `tradingengine.book.OrderBookSide` | Maintains price priority and aggregated level snapshots |
| `tradingengine.book.OrdersQueue` | Maintains FIFO ordering within a single price level |
| `tradingengine.book.OrderLocator` | Stores an order's side and price level for direct cancellation |
| `tradingengine.domain.Order` | Mutable order state: ID, side, price, timestamp, remaining quantity, status |
| `tradingengine.domain.Trade` | Immutable execution record |
| `tradingengine.persistence.CommandLog` | Append-only WAL, hash chain, verification, replay record loading |
| `tradingengine.persistence.FileTradeStore` | Appends and loads `trades.csv`; cleared and rebuilt during replay |
| `tradingengine.ops.EngineRuntime` | Shared runtime state for readiness and operational counters |
| `tradingengine.websocket.WebSocketServer` | Mounts all HTTP and WebSocket routes |
| `tradingengine.websocket.MarketDataBroadcaster` | Converts engine events into outbound WebSocket JSON messages |
| `tradingengine.analytics.AnalyticsJob` | Periodic ETL runner for analytics snapshots |
| `tradingengine.analytics.AnalyticsCalculator` | Pure aggregation logic |
| `tradingengine.analytics.AnalyticsStore` | Persists and serves `analytics.csv` |

### Order matching

Matching is implemented in `app/src/main/java/tradingengine/matchingengine/MatchingEngine.java`. The engine uses explicit price-time priority:

- Price priority is held in `OrderBookSide` through a `TreeMap<Long, OrdersQueue>`.
- Bid side is created with `Comparator.reverseOrder()` so highest price wins.
- Ask side is created with `Comparator.naturalOrder()` so lowest price wins.
- FIFO inside each price level is enforced by `OrdersQueue`, which uses `LinkedHashMap<String, Order>`.

Submission flow in `MatchingEngine.submit(Order incoming)`:

1. Validate `incoming` is not null.
2. If not in replay mode, append an `ORDER` record to `CommandLog`.
3. Call `matchIncoming(incoming, trades)`.
4. If the incoming order still has remaining quantity, add it to `OrderBook` and emit an `OrderBookEvent` of type `ADD`.
5. Return the list of generated `Trade` objects.

`matchIncoming(...)` is the matching loop:

- Chooses the opposing side based on the incoming order side.
- Peeks the best resting order with `OrderBookSide.peekBestOrderOrNull()`.
- Stops if the opposing side is empty.
- Stops if prices do not cross using `Order.canMatch(resting.getPrice())`.
- Executes one trade with `executeTrade(incoming, resting)`.
- Emits `TradeExecutedEvent` when not replaying.
- Removes an inactive best resting order with `OrderBook.removeBestOrderIfInactive(...)`.
- Emits `OrderBookEvent` of type `REMOVE` when a resting order leaves the book.

Execution rules in `executeTrade(...)`:

- Orders must be on opposite sides.
- Fill quantity is `min(buy.remainingQty, sell.remainingQty)`.
- Trade price is always the resting order price.
- Both `Order` objects are mutated via `Order.execute(...)`.
- The resulting `Trade` is immediately persisted through `FileTradeStore.save(...)`.

These behaviors are exercised in `app/src/test/java/tradingengine/matchingengine/MatchingEngineIntegrationTest.java`.

### Command log / WAL

The write-ahead log lives in `app/src/main/java/tradingengine/persistence/CommandLog.java` and persists to `commands.log`. This file is the authoritative state history for the engine.

Record format — each record is one JSON line. The schema is unified for both `ORDER` and `CANCEL` records:

- `type`, `orderId`, `side`, `price`, `quantity`, `timestamp`, `cancelOrderId`, `prevHash`, `hash`

Write path:

- `appendOrder(...)` creates a `Record` with `type = ORDER`.
- `appendCancel(...)` creates a `Record` with `type = CANCEL`.
- `append(Record r)` loads the last nonblank line to determine the previous hash. If the file is empty, `prevHash` is set to `GENESIS`.
- It computes `hash = sha256Hex(prevHash + "|" + payload)` using a stable `payloadForHash(...)`.
- It writes the JSON line using `FileChannel` with `CREATE`, `WRITE`, and `APPEND`.
- It calls `channel.force(true)` before returning — crash-durable by design.

Why the hash chain exists: the engine re-verifies the entire chain at boot. Any modified, missing, reordered, or corrupted record causes startup to fail before replay. That makes `commands.log` tamper-evident, not just append-only.

Verification and replay: `CommandLog.verifyChainOrThrow()` walks the log from top to bottom, checks every `prevHash`, and recomputes every hash. `App` calls this before any replay. Only after verification succeeds does `App` replay records into `MatchingEngine`.

### Trade persistence

Trades are persisted by `FileTradeStore` to `trades.csv`. This file is **derived state**, not the authoritative source of truth.

- `App` clears `trades.csv` on every boot.
- It replays `commands.log` through the normal engine path.
- `MatchingEngine.executeTrade(...)` regenerates the same `Trade` objects and re-persists them.

This keeps one source of truth for recovery: the WAL.

### Timestamp stability

There are three distinct timestamp decisions in the code:

1. `App` constructs `MatchingEngine` with `Clock.systemUTC()`.
2. `OrderApiServlet` creates live orders with `Instant.now()`.
3. `MatchingEngine.executeTrade(...)` stamps each trade with `incoming.getTimestamp()`, **not** `Instant.now(clock)`.

The third decision is the key replay-stability rule. The incoming order timestamp is persisted in `commands.log`. During replay, `App` reconstructs the original `Order` with that same timestamp. When the replayed order executes, `executeTrade(...)` uses the same timestamp again. The regenerated `Trade` objects and `trades.csv` rows keep the same timestamps across any number of restarts.

This was an explicit fix — the original implementation used `Instant.now(clock)` in `executeTrade(...)`, which caused trade timestamps to shift to the replay clock on every EC2 recreation. The fix is covered by `CommandLogRecoveryIntegrationTest.replayPreservesTradeTimestampsWhenRecoveryClockDiffers()`.

### API endpoints

| Method | Path | Implemented by | Description |
| --- | --- | --- | --- |
| `GET` | `/` | `RootRedirectServlet` | Redirects to `/ui` |
| `GET` | `/ui` | `UiServlet` | Serves the HTML dashboard |
| `GET` | `/health` | `HealthServlet` | Liveness probe; returns `200 OK` if process is alive |
| `GET` | `/ready` | `ReadyServlet` | Readiness probe; returns `200 READY` only after replay/startup complete |
| `GET` | `/metrics` | `MetricsServlet` | Plain-text service counters and uptime |
| `POST` | `/api/order` | `OrderApiServlet` | Accepts a limit order and returns `accepted`, `orderId`, and trade count |
| `POST` | `/api/cancel` | `CancelApiServlet` | Cancels an order by ID and returns `ok`/`orderId` |
| `GET` | `/api/book` | `BookApiServlet` | Returns current book snapshot as JSON; `?format=text` returns a debug dump |
| `GET` | `/api/trades` | `TradesApiServlet` | Returns recent trades newest-first; `?limit=<n>` supported |
| `GET` | `/api/analytics` | `AnalyticsServlet` | Returns the latest persisted `analytics.csv` as raw CSV |
| `WS` | `/ws` | `EngineWebSocket` | Push channel for trade and book-change notifications |

### WebSocket

WebSocket is a push model, not a streaming authority. `MarketDataBroadcaster` converts `TradeExecutedEvent` and `OrderBookEvent` into JSON messages and broadcasts them to all connected clients. The UI (`ws.js`) triggers `onPush()` on every message. `app.js` debounces that push and refetches `/api/book` and `/api/trades` over HTTP. HTTP snapshots remain authoritative.

### Analytics

`AnalyticsJob.runOnce()` snapshots current bids, asks, and trade history, computes an `AnalyticsSnapshot` via `AnalyticsCalculator`, and persists it through `AnalyticsStore` using temp-file-then-rename semantics (`ATOMIC_MOVE`). `App` runs one snapshot before readiness, then every 30 seconds on a daemon scheduler. Analytics are derived output — they do not mutate the engine.

---

## Infrastructure

Infrastructure code lives under `terraform/`.

### What Terraform manages

Terraform creates and manages:

- `aws_iam_role.ec2_ecr_role`
- `aws_iam_role_policy_attachment.ecr_read`
- `aws_iam_instance_profile.ec2_profile`
- `aws_security_group.trading_engine_sg`
- `aws_instance.trading_engine`
- `aws_eip_association.trading_engine_ip`
- `aws_volume_attachment.trading_data_attachment`

These are instance lifecycle concerns. They are safe to destroy and recreate with the instance.

### What lives outside Terraform

Terraform intentionally does not create:

- The persistent EBS data volume
- The Elastic IP allocation
- The subnet used for AZ pinning
- The EC2 key pair
- The ECR repository

Why:
- The EBS volume must survive `terraform destroy` so `commands.log` survives instance recreation.
- The Elastic IP must survive so the public endpoint and the GitHub `EC2_HOST` secret never change.
- The subnet is pre-existing infrastructure used to pin the instance to the correct AZ.

Terraform references these through data sources and variables: `data.aws_ebs_volume.trading_data`, `data.aws_eip.trading_engine_ip`, `data.aws_subnet.trading_engine`.

### IAM role

The EC2 node gets ECR permissions from an instance profile (`AmazonEC2ContainerRegistryReadOnly`). There is no `aws configure` step and no credentials file on disk. The node gets temporary credentials from EC2 metadata. The same mechanism is used by the ECR credential provider at kubelet level.

### Security group

`aws_security_group.trading_engine_sg` opens:

- `22/tcp` — SSH from GitHub Actions and manual admin access
- `8080/tcp` — direct HTTP to the Jetty application
- `30080/tcp` — the k3s `NodePort` service
- all outbound traffic for ECR pulls and AWS API access

### Elastic IP

Looked up via `data.aws_eip.trading_engine_ip`, associated via `aws_eip_association`. Keeping it outside Terraform means `terraform destroy` does not change the public endpoint. The `EC2_HOST` GitHub secret remains stable indefinitely.

### EBS volume

Looked up via `data.aws_ebs_volume.trading_data`, attached via `aws_volume_attachment`. Keeping it outside Terraform means `terraform destroy` does not delete `commands.log`. Data survives any number of instance recreations.

### AZ pinning

The instance is pinned indirectly by subnet. Terraform looks up `data.aws_subnet.trading_engine` from `var.trading_engine_subnet_id`. A lifecycle `precondition` asserts that the subnet's AZ equals `data.aws_ebs_volume.trading_data.availability_zone`. If they do not match, `terraform apply` fails at planning time before any instance is launched. This enforces the AZ constraint at the infrastructure layer, not through documentation.

### Volume ID injection into userdata

```hcl
user_data = replace(
  file("${path.module}/userdata.sh"),
  "__TRADING_DATA_VOLUME_ID__",
  var.trading_data_volume_id
)
```

The bootstrap script must know the exact EBS volume to wait for. Nitro NVMe device numbering is not stable, so the volume ID is converted into a stable `/dev/disk/by-id/...` path at boot.

---

## Bootstrap (userdata.sh)

Bootstrap runs on first boot through EC2 user data. The script runs with `set -e` and logs to `/var/log/userdata.log`.

### Step 1: Install host dependencies

Installs `git`, `curl`, `wget`, and Docker via `amazon-linux-extras`. Enables and starts Docker, adds `ec2-user` to the `docker` group.

### Step 2: Install the ECR credential provider

Downloads `ecr-credential-provider-linux-amd64` and installs it at `/var/lib/rancher/credentialprovider/bin/ecr-credential-provider`. Writes the kubelet credential-provider config to `/var/lib/rancher/credentialprovider/config.yaml` matching `*.dkr.ecr.*.amazonaws.com`.

Why this is used instead of `imagePullSecrets`: image authentication is handled at node level using the EC2 IAM role. Registry credentials do not need to be stored as Kubernetes secrets. k3s kubelet resolves ECR credentials directly when pulling pod images.

### Step 3: Install k3s

Installed with `INSTALL_K3S_EXEC="--disable traefik"`. Traefik is disabled because the service is exposed through a `NodePort`, not an ingress controller. After installation, the script polls `kubectl --kubeconfig=/etc/rancher/k3s/k3s.yaml get nodes` until the API server is ready.

### Step 4: Set up kubeconfig for `ec2-user`

Copies `/etc/rancher/k3s/k3s.yaml` to `/home/ec2-user/.kube/config`, sets ownership and `600` permissions, and appends `export KUBECONFIG=...` to `.bashrc`. This allows GitHub Actions (which connects as `ec2-user`) to run `kubectl` without `sudo`.

### Step 5: Detect, format, and mount the EBS volume

This is the critical state-preservation path. It went through several iterations before reaching its current form.

The script resolves a stable device path from the injected volume ID:

```bash
EBS_ID="__TRADING_DATA_VOLUME_ID__"
EBS_ID_NODASH="${EBS_ID//-/}"
STABLE_DEV="/dev/disk/by-id/nvme-Amazon_Elastic_Block_Store_${EBS_ID_NODASH}"
```

**Why the stable by-id path instead of `/dev/nvme1n1`:** On Nitro instances, NVMe numbering is not stable across reboots and reattachments. Using `/dev/nvme1n1` risks probing or formatting the wrong device. The by-id path encodes the actual EBS volume identity.

**Why `udevadm settle` instead of `sleep`:**

```bash
udevadm settle --exit-if-exists="${STABLE_DEV}" --timeout=60
```

Terraform attaches the external EBS volume after EC2 boot has already started — this is a known Terraform limitation. Earlier iterations used `sleep` or a loop checking for `/dev/nvme1n1`, but both were unreliable because the device could appear before its filesystem metadata was readable. `udevadm settle` blocks until udev has finished processing the exact device path. If the device never appears, bootstrap fails fast with a clear error instead of falling through to a format path.

**Why `blkid -p` instead of plain `blkid`:**

```bash
if fs_type=$(blkid -p -o value -s TYPE "${STABLE_DEV}" 2>/dev/null); then
  echo "Already formatted (${fs_type}), skipping"
else
  mkfs.ext4 -F "${STABLE_DEV}"
fi
```

The script runs under `set -e`. Earlier iterations used `blkid` in a pipe or with `grep`, which caused `set -e` to kill the script on a non-zero exit code from an unformatted volume — the exact case we needed to handle. Using `blkid -p` directly in the `if` condition makes "filesystem exists" the true branch and "no filesystem found" the controlled false branch. This is the difference between correctly skipping format and accidentally wiping a previously used volume.

The script then mounts by UUID (stable across device renaming) and adds an idempotent `fstab` entry.

### Step 6: Apply Kubernetes manifests

Only after the EBS volume is mounted does the script apply manifests:

```bash
kubectl apply -f /tmp/trading-engine/k8s/
```

**Why EBS is mounted before manifests:** The `PersistentVolume` points at `/mnt/trading-data`. If manifests were applied before the host mount existed, Kubernetes would bind the local PV to an empty host path rather than the mounted EBS-backed filesystem. Mounting first guarantees the local PV path is already backed by the retained disk when the pod is scheduled.

---

## Kubernetes

### Manifests overview

- `k8s/deployment.yaml` — `Deployment/trading-engine`, one replica, `Recreate` strategy, liveness/readiness probes, mounts PVC at `/data`
- `k8s/service.yaml` — `Service/trading-engine-svc`, `NodePort`, port `30080`
- `k8s/pvc.yaml` — `PVC/trading-engine-pvc`, `9Gi`, `ReadWriteOnce`, `local-storage`
- `k8s/storage.yaml` — `StorageClass/local-storage` (`no-provisioner`, `WaitForFirstConsumer`) + `PV/trading-engine-pv` pointing at `/mnt/trading-data`, `Retain` policy

### Recreate strategy

```yaml
strategy:
  type: Recreate
```

`RollingUpdate` was considered but rejected. The application mounts a `ReadWriteOnce` PVC backed by a host-local path. Two pods simultaneously owning the same local volume path would cause write conflicts and potential log corruption. `Recreate` makes the single-writer assumption explicit. The tradeoff is brief downtime during deploys, which is acceptable for this design. The path to zero-downtime deploys would be migrating state to PostgreSQL, which would remove the `ReadWriteOnce` constraint entirely.

### PVC / PV / StorageClass chain

The persistence chain:

1. `StorageClass/local-storage` — no dynamic provisioner, `WaitForFirstConsumer`
2. `PersistentVolume/trading-engine-pv` — local path `/mnt/trading-data`, `Retain` policy
3. `PVC/trading-engine-pvc` — requests `9Gi` from `local-storage`
4. `Deployment/trading-engine` — mounts the claim at `/data`

`WaitForFirstConsumer` defers binding until the pod is scheduled. `Retain` prevents the PV from being deleted if the PVC is removed.

### No `imagePullSecrets`

The Deployment has no `imagePullSecrets`. The kubelet credential provider installed by `userdata.sh` uses the EC2 IAM role to resolve ECR credentials at node level. Registry auth is not stored in Kubernetes secrets.

---

## CI/CD Pipeline

The pipeline is defined in `.github/workflows/ci.yml` with three intentional jobs.

### Evolution from two jobs to three

The original pipeline had two jobs: `build` and `deploy`. This had two problems. First, PRs triggered the `build` job which pushed Docker images to ECR — unmerged code was being published to the registry. Second, the `deploy` job SSHed into EC2 and ran `docker login` and `docker pull` on the host, which had no effect on k3s image pulls (k3s uses containerd, not the host Docker daemon). The fix was to split into three jobs with explicit guards.

### `test`

Runs on every push and every pull request: checkout, JDK 21, `./gradlew clean test`. PRs validate code without touching AWS.

### `publish-image`

Runs only on `push` to `main`, after `test`:

- `docker build -t trading-engine:${{ github.sha }} .`
- Push `:${{ github.sha }}` — the immutable deploy artifact
- Push `:prod` — the bootstrap fallback for fresh EC2 nodes

Build once, tag twice. The same tested image gets both tags. `:sha` is immutable and traceable to a specific commit. `:prod` is kept only because the static manifest in `k8s/deployment.yaml` must reference a tag that exists before any CI deploy has run on a fresh node.

### `deploy`

Runs only on `push` to `main`, after `publish-image`. No AWS credentials needed — this job only SSHes into EC2:

```yaml
script: |
  export KUBECONFIG=/home/ec2-user/.kube/config
  /usr/local/bin/kubectl set image deployment/trading-engine \
    trading-engine=<ecr>:${{ github.sha }}
```

Then waits:

```yaml
script: |
  export KUBECONFIG=/home/ec2-user/.kube/config
  /usr/local/bin/kubectl rollout status deployment/trading-engine --timeout=180s
```

**Why `kubectl set image` instead of `rollout restart`:** `rollout restart` forces a pod restart without changing the image reference. That was the original approach, but it meant the live Deployment was always pointing at `:prod` — a mutable tag. `kubectl set image` explicitly patches the Deployment to the immutable SHA, making every deploy auditably tied to a specific tested commit. Rollback is trivial: `kubectl set image` to any prior SHA.

**Why `KUBECONFIG` is set explicitly and `/usr/local/bin/kubectl` is used:** `appleboy/ssh-action` runs a non-interactive shell. `.bashrc` is not sourced, and `$PATH` can differ from an interactive SSH session. Setting `KUBECONFIG` inline and using the absolute path removes any dependency on shell initialisation.

---

## The 5 Issues Solved

### Issue 1: Port 22 blocked

**What happened:** The first CI/CD run timed out trying to SSH into EC2. The security group had no inbound rule for port 22 — it had to be manually added in the console after launch.

**Temporary fix:** Manually edit the security group inbound rules in the AWS console after every `terraform apply`.

**Proper fix:** Define the SSH ingress rule in `aws_security_group.trading_engine_sg` inside `terraform/main.tf`. The firewall configuration is now part of the stack definition. A fresh `terraform apply` produces the correct SSH access from day one. Manual console drift is eliminated.

### Issue 2: Wrong SSH username

**What happened:** GitHub Actions was configured with `username: ubuntu`. The EC2 instance was running Amazon Linux 2, which uses `ec2-user`. SSH refused the connection.

**Temporary fix:** Know the correct username and update `ci.yml` manually.

**Proper fix:** Standardize on Amazon Linux 2 AMI in `aws_instance.trading_engine`. Amazon Linux 2 always uses `ec2-user`. The username is now a known constant, not an AMI-specific detail that needs to be looked up.

### Issue 3: No AWS credentials on EC2

**What happened:** The EC2 node had no way to authenticate to AWS. ECR image pulls failed. The initial fix was to `aws configure` with static access keys directly on the instance.

**Why static keys are a security risk:** The keys are stored in plaintext in `~/.aws/credentials`. If the instance is compromised, an attacker gets full access to any AWS service those keys allow — in this case an IAM user with `AmazonEC2FullAccess` and `IAMFullAccess`. The keys also need manual rotation.

**Proper fix:** Attach `AmazonEC2ContainerRegistryReadOnly` to an EC2 IAM role via an instance profile. The node gets temporary credentials from EC2 metadata. No secrets file on disk. No manual rotation. The same mechanism is used by the ECR credential provider at kubelet level.

### Issue 4: `kubectl` permission denied

**What happened:** The k3s kubeconfig at `/etc/rancher/k3s/k3s.yaml` is owned by root. `ec2-user` could not use it directly. Every `kubectl` command in CI had to be prefixed with `sudo`, which introduced PATH and environment issues.

**Temporary fix:** `sudo kubectl` on every command.

**Why `sudo kubectl` broke things:** `sudo` resets `PATH` and environment variables. `KUBECONFIG` set for `ec2-user` was not visible to the sudo context. Commands worked interactively but failed in non-interactive shells used by `appleboy/ssh-action`.

**Proper fix:** `userdata.sh` copies `/etc/rancher/k3s/k3s.yaml` to `/home/ec2-user/.kube/config`, changes ownership to `ec2-user`, and sets mode `600`. Deploy commands run as `ec2-user` without privilege escalation.

### Issue 5: `ImagePullBackOff` because k3s uses containerd, not Docker

**What happened:** k3s uses containerd as its container runtime, not Docker. The CI pipeline was doing `docker login` and `docker pull` on the EC2 host, which populated Docker's credential store. containerd has a completely separate credential store. k3s never saw those credentials and pod image pulls failed with `ImagePullBackOff`.

**Temporary fix:** Create a Kubernetes `imagePullSecret` with a short-lived ECR token and reference it in the Deployment. This worked but expired every 12 hours, requiring manual renewal.

**Proper fix:** Install the ECR credential provider binary at `/var/lib/rancher/credentialprovider/bin/ecr-credential-provider` and write the kubelet credential-provider config to `/var/lib/rancher/credentialprovider/config.yaml`. k3s automatically discovers and uses this provider. The provider calls AWS using the EC2 IAM role and fetches fresh ECR tokens as needed. No `imagePullSecrets`, no expiry, no Docker involved.

---

## Design Decisions

### 1. Recreate vs RollingUpdate

**Chosen:** `Recreate`. The pod mounts a `ReadWriteOnce` PVC. Two simultaneous pods would conflict on the same local volume. `Recreate` makes the single-writer assumption explicit. **Tradeoff:** brief downtime on every deploy.

### 2. ECR credential provider vs `imagePullSecrets`

**Chosen:** Node-level ECR credential provider. The EC2 node already has an IAM role. Registry auth belongs at the kubelet layer, not in per-namespace Kubernetes secrets. **Tradeoff:** bootstrap depends on correctly installing the provider binary.

### 3. EBS + local PV vs cloud-native CSI storage

**Chosen:** Retained EBS volume mounted on the host, exposed as a `local` PV. The system is intentionally single-node and stateful. The goal is durable state across EC2 recreation without a larger storage stack. **Tradeoff:** storage is tied to one node and one AZ. No horizontal scaling.

### 4. IAM role vs static credentials

**Chosen:** EC2 instance profile with `AmazonEC2ContainerRegistryReadOnly`. Temporary credentials from EC2 metadata. No secrets file on disk. **Tradeoff:** pull auth depends on correct IAM attachment and metadata access.

### 5. Stable device path vs `/dev/nvme1n1`

**Chosen:** `/dev/disk/by-id/nvme-Amazon_Elastic_Block_Store_<volume-id-without-dashes>`. Nitro NVMe numbering is not stable. The boot script must identify the exact retained volume. **Tradeoff:** bootstrap depends on correct volume-ID injection and the by-id symlink being present.

### 6. `blkid -p` vs plain `blkid`

**Chosen:** `blkid -p` in the `if` condition. The script runs under `set -e`. A plain `blkid` call outside a guarded branch exits non-zero on an unformatted volume, killing the script before the format check can run correctly. `blkid -p` as the condition makes both outcomes explicit branches. **Tradeoff:** the logic is more deliberate than a naive check.

### 7. `udevadm settle` vs `sleep`

**Chosen:** `udevadm settle --exit-if-exists=... --timeout=60`. Terraform attaches EBS after EC2 boots. Sleep-based approaches were unreliable — the device appeared before its filesystem metadata was readable, causing the format check to see a blank device and reformat it. `udevadm settle` waits on the exact device and filesystem state. **Tradeoff:** bootstrap fails hard if the device does not appear, which is intentional.

### 8. `fsync` in WAL append

**Chosen:** `FileChannel.force(true)` on every append. A trading engine should not acknowledge accepted commands without durably writing them to disk. Without `fsync`, a crash after `write()` but before OS flush loses acknowledged commands. **Tradeoff:** higher append latency than a buffered write.

### 9. Trade timestamps from the incoming order vs `Instant.now()`

**Chosen:** `incoming.getTimestamp()` in `executeTrade(...)`. The incoming order timestamp is persisted in the WAL. During replay, the same timestamp is used again. Trade history is identical across any number of restarts. **Tradeoff:** the trade timestamp reflects the aggressive order arrival time, not a separately sampled execution clock.

### 10. EBS and Elastic IP outside the Terraform stack

**Chosen:** Both are data sources, not managed resources. `terraform destroy` releases the instance and attachments but not the data disk or public IP. **Tradeoff:** the stack is not fully self-contained. The EBS volume and Elastic IP must be pre-created before `terraform apply`.

### 11. AZ pinning via subnet + precondition

**Chosen:** Launch the instance into an explicit subnet and assert at plan time that the subnet's AZ matches the EBS volume's AZ. A mismatch causes `terraform apply` to fail before any instance launches. **Tradeoff:** the deployment is deliberately coupled to one subnet and one AZ.

### 12. Three CI jobs vs two

**Chosen:** `test` → `publish-image` → `deploy`. PRs run only `test` — no AWS credentials, no ECR push, no SSH. Publishing and deploying are gated to `main` branch pushes after tests pass. **Tradeoff:** more verbose workflow than a two-job pipeline.

---

## Known Tradeoffs

- **Single AZ, single node, single volume.** The deployment is pinned to one EC2 instance, one k3s node, and one retained EBS volume. Any AZ or node failure is downtime.
- **Unauthenticated public API.** Ports `8080` and `30080` are open to `0.0.0.0/0`. The HTTP API has no authentication layer.
- **No TLS.** The service is exposed over plain HTTP.
- **`:prod` bootstrap fallback is mutable.** Fresh nodes start from a static manifest referencing `:prod` until CI patches the live Deployment to an immutable SHA.
- **Bootstrap is not fully reproducible.** `userdata.sh` downloads the ECR credential provider binary from GitHub, pipes `get.k3s.io` into `sh`, and clones the repo from GitHub at boot time.

---

## Future Improvements

- **PostgreSQL** — removes the single-writer local-volume constraint, makes `RollingUpdate` and horizontal scaling realistic.
- **GitHub OIDC instead of static AWS keys** — replace long-lived GitHub secrets with short-lived federated credentials.
- **TLS and authentication on the API** — the current public HTTP surface is intentionally minimal but not hardened.
- **EBS snapshots for backup automation** — the retained volume is durable across instance recreation but has no automated backup.
- **Baked AMI for reproducible bootstrap** — move host dependencies and k3s prerequisites out of first-boot scripting.
- **Image digest pinning** — the live Deployment is SHA-tagged today, but the bootstrap fallback still depends on the mutable `:prod` tag.

---

## Local Development

### Prerequisites

- Java 21
- Docker (optional, for containerized build)
- Gradle wrapper included (`./gradlew`)

### Run locally

```bash
./gradlew :app:run
```

`App` uses `DATA_DIR=data` by default. It writes `data/commands.log`, `data/trades.csv`, and `data/analytics.csv`.

Once started:

- `http://localhost:8080/ui` — dashboard
- `http://localhost:8080/health` — liveness
- `http://localhost:8080/ready` — readiness
- `http://localhost:8080/metrics` — counters

### Run with Docker

```bash
docker build -t trading-engine:dev .
mkdir -p docker-data
docker run --rm -p 8080:8080 \
  -e DATA_DIR=/data \
  -v "$(pwd)/docker-data:/data" \
  trading-engine:dev
```

### Run tests

```bash
./gradlew test
```

Key test files:

- `app/src/test/java/tradingengine/matchingengine/MatchingEngineIntegrationTest.java` — deterministic matching, FIFO, price priority, partial fills
- `app/src/test/java/tradingengine/persistence/CommandLogRecoveryIntegrationTest.java` — WAL hash-chain verification, replay recovery, timestamp stability
- `app/src/test/java/tradingengine/websocket/MarketDataBroadcasterTest.java` — WebSocket message mapping