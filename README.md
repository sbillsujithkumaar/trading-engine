## Where data lives

This service is server-authoritative.

- Clients (browser tabs, other computers) only send HTTP requests and receive WebSocket updates.
- The engine state (order book + trade history) runs on the server process.
- Persistence is stored on the server machine under:
  - `./data/commands.log` (source of truth; tamper-evident hash chain)
  - `./data/trades.csv` (derived state; rebuilt from replay)
