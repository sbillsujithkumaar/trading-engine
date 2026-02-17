# Trading Engine

A Java (Gradle + Jetty) trading engine demo with:

- price-time priority matching
- HTTP ops/dev endpoints
- WebSocket market data updates
- crash recovery via write-ahead command replay
- tamper-evident command log hash chain
- Dockerized runtime with persistent volume support

## What We Have Achieved

### 1) Core matching + interfaces

- Matching engine processes BUY/SELL orders and cancels.
- HTTP endpoints:
  - `/health`
  - `/ready`
  - `/metrics`
  - `/api/order`
  - `/api/cancel`
  - `/api/book`
  - `/api/trades`
- WebSocket stream endpoint:
  - `/ws`
- Browser UI:
  - `/ui`

### 2) Reliability / crash recovery

- Accepted commands are written to an append-only WAL (`commands.log`).
- On startup:
  1. command log integrity is verified (`verifyChainOrThrow()`)
  2. `trades.csv` is cleared
  3. commands are replayed into the engine
  4. replay mode is disabled
  5. server starts and readiness is set to READY
- Replay preserves original order IDs so cancels still work after restart.
- `trades.csv` is treated as derived state and rebuilt deterministically from replay.

### 3) Tamper-evident command log

Each WAL record stores:

- `prevHash`
- `hash`

Hashing model:

- first record uses `GENESIS` as `prevHash`
- each next record links to the previous record hash
- `hash = SHA256(prevHash + "|" + stablePayload)`

At startup, chain verification fails fast if any record is modified.

## Where Data Lives

This service is server-authoritative.

- Clients (browser tabs, other computers) only send HTTP requests and receive WebSocket updates.
- The engine state (order book + trade history) runs on the server process.
- Persistence is stored on the server machine under:
  - `./data/commands.log` (source of truth; tamper-evident hash chain)
  - `./data/trades.csv` (derived state; rebuilt from replay)

## Run Locally

Prerequisite: Java 21.

```bash
./gradlew :app:run
```

Open:

- `http://localhost:8080/ui`

## Docker

### Build image

```bash
docker build -t trading-engine:dev .
```

### Run with local folder mount (recommended)

```bash
mkdir -p docker-data

docker run --rm -p 8080:8080 \
  -e DATA_DIR=/data \
  -v "$(pwd)/docker-data:/data" \
  trading-engine:dev
```

This maps the container's `/data` folder to your host folder so logs persist even if the container is deleted.

Windows path fallback example:

```bash
-v "C:/Users/ASUS/Downloads/Courses/Trading Engine/docker-data:/data"
```

### Run with named volume

```bash
docker volume create trading_engine_data

docker run --rm -p 8080:8080 \
  -e DATA_DIR=/data \
  -v trading_engine_data:/data \
  trading-engine:dev
```

Inspect named volume contents:

```bash
docker run --rm -v trading_engine_data:/data alpine ls -l /data
```

## Notes

- `DATA_DIR` controls persistence location. Default is `data` when not set.
- Dockerfile uses Java 21 images to match project bytecode compatibility.
