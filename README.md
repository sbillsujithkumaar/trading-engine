# Trading Engine

Deterministic limit order matching engine in Java (Gradle + Jetty) with persistent command logging, replay-based recovery, REST snapshots, WebSocket push, and Docker/AWS deployment.

This project is backend-first. The browser UI is intentionally thin and reconstructs state from server snapshots.

## Project Overview

This service accepts `BUY`/`SELL` limit orders and cancellations, matches orders using price-time priority, persists accepted commands, and can recover state after restart by replaying the command log.
This engine is intentionally stateful and designed to run as a single authoritative instance backed by a persistent volume.

Key behavior:

- **HTTP snapshots are the source of truth** (`/api/book`, `/api/trades`).
- **WebSockets are push signals only** (`/ws` tells clients to refetch snapshots).
- Matching supports **price-time priority**, **partial fills**, and **multi-order sweeps**.
- Persistence is handled through an append-only write-ahead command log (`commands.log`) plus derived trade history (`trades.csv`).

This avoids client-side divergence and ensures state consistency across multiple UI sessions.

Main endpoints:

- Ops: `/health`, `/ready`, `/metrics`
- Trading APIs: `POST /api/order`, `POST /api/cancel`, `GET /api/book`, `GET /api/trades`
- Analytics API: `GET /api/analytics` (CSV snapshot)
- UI: `/ui`
- WebSocket: `/ws`

## Architecture

### Core design

- **Matching engine core**: `MatchingEngine` executes crossing logic and updates the order book.
- **Order book model**:
  - Price priority by side (`BUY` descending, `SELL` ascending)
  - FIFO queue per price level
- **Write-ahead command log**:
  - Accepted commands are appended to `commands.log`
  - Log records are hash-chained (`prevHash`, `hash`)
- **Tamper-evident verification**:
  - On startup, command log chain is verified before replay
  - Startup fails fast on chain mismatch/corruption
- **Replay on startup**:
  - `trades.csv` is cleared
  - Command log is replayed into engine state
  - Readiness flips to `READY` only after replay finishes
- **REST + WebSocket separation**:
  - REST serves current snapshots and command endpoints
  - WebSocket broadcasts event signals
- **UI snapshot reconstruction**:
  - UI never keeps authoritative trading state
  - On push, it refetches `/api/book` and `/api/trades`

```mermaid
flowchart LR
    UI[Browser UI] -->|GET snapshots| REST[REST API layer]
    UI -->|POST order/cancel| REST
    REST --> ME[Matching Engine + Order Book]
    ME -->|append ORDER/CANCEL| WAL[(commands.log)]
    ME -->|append trades| TR[(trades.csv)]
    ME -->|event messages| WS[WebSocket /ws]
    WS -->|push signal: refresh now| UI
    WAL -->|startup verify + replay| ME
```

### Analytics ETL Layer

This project includes a lightweight backend analytics pipeline that periodically aggregates trading activity and current order book state into a persisted snapshot.

**Purpose**

Raw command logs and trade history are useful for replay and audit, but operational systems typically require aggregated metrics for monitoring and analysis.  
This layer computes those aggregates in a structured, failure-isolated manner.

**How It Works**

A background job (`AnalyticsJob`) runs every 30 seconds and performs:

- **Extract**: snapshot current bids, asks, and executed trades.
- **Transform**: compute aggregate trading metrics.
- **Load**: persist a single CSV snapshot to `analytics.csv`.

The latest snapshot is exposed via:

```http
GET /api/analytics
```

Response format: `text/csv`.

**Snapshot Schema**

Each snapshot contains:

- `timestamp` - when the snapshot was generated
- `totalTrades` - number of executed trades
- `totalVolume` - cumulative traded quantity
- `avgTradePrice` - quantity-weighted average trade price
- `bestBid` - highest resting BUY price (nullable)
- `bestAsk` - lowest resting SELL price (nullable)
- `openOrders` - total resting orders in the book

Example:

```csv
timestamp,totalTrades,totalVolume,avgTradePrice,bestBid,bestAsk,openOrders
2026-02-20T17:35:58Z,24,120,170.95833,,100,2
```

**Design Properties**

- Snapshots are written using a temporary file plus move/replace strategy (atomic move where supported).
- Analytics failures do not stop the trading engine (best-effort policy).
- A snapshot is generated on startup after replay and before readiness flips to `READY`.
- Analytics are intentionally separated from matching logic:
  - `AnalyticsCalculator` (pure transformation logic)
  - `AnalyticsStore` (persistence layer)
  - `AnalyticsJob` (ETL orchestration)

This provides a minimal but production-style ETL pipeline without coupling analytics to core trading logic.

## Key Engineering Features

- Deterministic matching with price-time FIFO priority.
- Partial fills and multi-order execution across one or more price levels.
- Cancel semantics with safe handling of unknown or already-filled IDs (no mutation when cancel is invalid).
- Snapshot-based UI consistency (no client-side trading state authority).
- Append-only command log persistence with startup replay.
- Tamper-evident hash chain verification on command history.
- Dockerized deployment with persistent volume support.
- Operational endpoints for liveness/readiness/metrics.
- Modular frontend structure (`api.js`, `render.js`, `ws.js`, `app.js`) to separate transport, rendering, and orchestration concerns.

## Edge Case Handling

- **Multiple orders at same price**: executed FIFO by arrival order within that level.
- **Partial fills**: remaining quantity stays resting and can continue matching later.
- **Unknown cancel ID**: cancel returns not found; engine state and command log remain unchanged.
- **Canceling already filled order**: safely returns false; no duplicate cancel command is persisted.
- **UI consistency on refresh**: page rebuilds from `/api/book` and `/api/trades` snapshots, not local memory.
- **Server restart**: command log is verified then replayed to reconstruct deterministic engine state.

## Reproducible Demo

Prerequisites:

- Docker
- `curl`
- Optional: `jq` (for easier JSON parsing)

### 1) Start engine in Docker with persistent data

```bash
docker build -t trading-engine:dev .
mkdir -p docker-data

docker run -d --name trading-engine \
  -p 8080:8080 \
  -e DATA_DIR=/data \
  -v "$(pwd)/docker-data:/data" \
  trading-engine:dev
```

Windows PowerShell path variant:

```powershell
docker run -d --name trading-engine `
  -p 8080:8080 `
  -e DATA_DIR=/data `
  -v "${PWD}/docker-data:/data" `
  trading-engine:dev
```

### 2) Verify service status

```bash
BASE=http://localhost:8080
curl -s $BASE/health
curl -s $BASE/ready
curl -s $BASE/metrics
```

### 3) Demonstrate FIFO + partial fill

Create three SELL orders at the same price:

```bash
S1=$(curl -s -X POST "$BASE/api/order" -H "Content-Type: application/json" \
  -d '{"side":"SELL","price":101,"quantity":4}' | jq -r '.orderId')
S2=$(curl -s -X POST "$BASE/api/order" -H "Content-Type: application/json" \
  -d '{"side":"SELL","price":101,"quantity":4}' | jq -r '.orderId')
S3=$(curl -s -X POST "$BASE/api/order" -H "Content-Type: application/json" \
  -d '{"side":"SELL","price":101,"quantity":4}' | jq -r '.orderId')
echo "$S1 $S2 $S3"
```

Check FIFO queue order at that level:

```bash
curl -s "$BASE/api/book" | jq '.asks[] | select(.price==101) | {price, qty, count, orders}'
```

Submit a BUY for quantity `6` at `101` (fills first order and partially fills second):

```bash
curl -s -X POST "$BASE/api/order" -H "Content-Type: application/json" \
  -d '{"side":"BUY","price":101,"quantity":6}' | jq
```

Verify remaining queue at `101` is still FIFO (`S2` then `S3`):

```bash
curl -s "$BASE/api/book" | jq '.asks[] | select(.price==101) | .orders'
```

### 4) Test cancel behavior

Cancel known order ID:

```bash
curl -s -X POST "$BASE/api/cancel" -H "Content-Type: application/json" \
  -d "{\"orderId\":\"$S2\"}" | jq
```

Cancel unknown ID (safe failure):

```bash
curl -i -s -X POST "$BASE/api/cancel" -H "Content-Type: application/json" \
  -d '{"orderId":"does-not-exist"}'
```

Expected: HTTP `404` and no state mutation.

### 5) Verify snapshot authority in UI

1. Open `http://localhost:8080/ui` in two tabs.
2. Submit/cancel in one tab.
3. Observe the other tab updates after WebSocket push.
4. Reload either tab; state is reconstructed from HTTP snapshots (`/api/book`, `/api/trades`).

### 6) Optional: restart container to demonstrate recovery

```bash
docker restart trading-engine
curl -s "$BASE/ready"
curl -s "$BASE/api/book" | jq
```

Because `/data` is persisted, state is rebuilt from `commands.log` replay.

## Running Locally

Prerequisite: Java 21.

```bash
./gradlew :app:run
```

Run tests:

```bash
./gradlew test
```

Windows:

```bat
gradlew.bat :app:run
gradlew.bat test
```

## Docker (Standalone Runtime)

Use this mode for quick standalone runs outside the step-by-step demo flow.

Build image:

```bash
docker build -t trading-engine:dev .
```

Run with bind mount (host folder persistence):

```bash
mkdir -p docker-data
docker run --rm -p 8080:8080 \
  -e DATA_DIR=/data \
  -v "$(pwd)/docker-data:/data" \
  trading-engine:dev
```

Run with named volume:

```bash
docker volume create trading_engine_data
docker run --rm -p 8080:8080 \
  -e DATA_DIR=/data \
  -v trading_engine_data:/data \
  trading-engine:dev
```

## Kubernetes Deployment

Primary deployment is Kubernetes on **AWS EC2 + k3s**.  
The same manifests also work on standard Kubernetes distributions (for example, Minikube).
The cluster runs as a single-node k3s installation on AWS EC2 for simplicity. The manifests are standard Kubernetes YAML and portable across environments.

Manifests live in `k8s/`:
- `deployment.yaml` -> runs the trading engine pod
- `service.yaml` -> stable in-cluster endpoint (`trading-engine-svc:8080`)
- `pvc.yaml` -> durable storage for `/data`

### Reference Setup (AWS EC2 + k3s)

Build image on EC2:

```bash
docker build -t trading-engine:prod .
```

k3s uses containerd as its container runtime; locally built Docker images must therefore be imported into the k3s image store before use.

```bash
docker save trading-engine:prod | sudo k3s ctr images import -
```

Apply and verify:

```bash
sudo kubectl apply -f k8s/
sudo kubectl get pods
sudo kubectl logs -l app=trading-engine
```

Access UI:

```bash
sudo kubectl port-forward svc/trading-engine-svc 8080:8080 --address 0.0.0.0
```

Open `http://<EC2_PUBLIC_IP>:8080/ui`.

### Persistence and Integrity

- Engine state is stored at `/data` using a PVC (`commands.log` + `trades.csv`).
- On restart, state is rebuilt by command-log replay.
- If tampering is detected on startup, engine fails fast and pod can enter `CrashLoopBackOff`.

Diagnose:

```bash
sudo kubectl get pods
sudo kubectl logs -l app=trading-engine
sudo kubectl describe pod -l app=trading-engine
```

Demo/dev manual reset only:

```bash
sudo kubectl exec -it deploy/trading-engine -- rm -f /data/commands.log /data/trades.csv
sudo kubectl delete pod -l app=trading-engine
```

In production, restore `/data` from backup instead of deleting files.

### Local Kubernetes (Optional)

If you want local testing with Minikube, use the same `k8s/` manifests:

```bash
minikube start
eval $(minikube docker-env)
docker build -t trading-engine:prod .
kubectl apply -f k8s/
kubectl port-forward svc/trading-engine-svc 8080:8080
```

Open `http://localhost:8080/ui`.

### Why one replica?

This project currently uses `replicas: 1` because the engine is stateful and tied to one persisted `/data` volume.
Scaling would require externalized storage and leader election, which are intentionally out of scope for this design.

## AWS EC2 Deployment

Primary EC2 deployment path is the Kubernetes setup above (`k3s` on EC2).

## Testing

This project includes unit and integration tests for matching behavior, persistence, and recovery:

- FIFO and price-priority matching
- Partial fills and multi-level sweeps
- Cancel behavior and invalid ID handling
- Command log replay determinism
- Tamper-evident hash-chain validation and corruption detection
- WebSocket broadcaster behavior

Run all tests with:

```bash
./gradlew test
```

## What This Project Demonstrates

- Backend system design beyond UI concerns.
- Deterministic state reconstruction from an append-only command history.
- Snapshot reconciliation model (HTTP snapshots + WebSocket push signal).
- Lightweight ETL analytics pipeline (Extract -> Transform -> Load) generating persisted trading metrics.
- Containerized deployment with Kubernetes-managed lifecycle (Deployment, Service, PVC), liveness/readiness probes, and fail-fast integrity behavior surfaced as CrashLoopBackOff.
- Operational thinking: health/readiness/metrics, restart behavior, persistence guarantees, and failure isolation.
