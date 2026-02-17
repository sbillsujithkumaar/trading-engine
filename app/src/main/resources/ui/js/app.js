import { UI } from "./config.js";
import {
  getHealth,
  getReady,
  getMetrics,
  getBook,
  getTrades,
  placeOrder,
  cancelOrder,
} from "./api.js";
import {
  setIndicator,
  showMsg,
  fmtTime,
  renderLevels,
  renderTrades,
} from "./render.js";
import { connectWebSocket } from "./ws.js";

export function boot(els) {
  els.hostVal.textContent = location.host;
  const expandedLevels = new Map();

  function parseConnectedClients(metricsText) {
    const match = String(metricsText ?? "").match(
      /(?:^|\n)\s*connected_clients\s+(\d+)(?:\s|$)/i,
    );
    if (!match) return null;
    const parsed = Number(match[1]);
    return Number.isFinite(parsed) ? parsed : null;
  }

  let refreshTimer = null;
  function requestRefresh(reason) {
    if (refreshTimer) return;
    refreshTimer = setTimeout(async () => {
      refreshTimer = null;
      await refreshSnapshots(reason);
    }, UI.WS_REFRESH_DEBOUNCE_MS);
  }

  async function refreshStatus() {
    const [healthRes, readyRes, metricsRes] = await Promise.allSettled([
      getHealth(),
      getReady(),
      getMetrics(),
    ]);

    if (healthRes.status === "fulfilled") {
      const h = healthRes.value;
      const ok = h.ok && h.text.trim().toUpperCase().startsWith("O");
      setIndicator(
        els.healthDot,
        els.healthVal,
        ok ? "ok" : "bad",
        ok ? "OK" : `FAIL (${h.text.trim()})`,
      );
    } else {
      setIndicator(els.healthDot, els.healthVal, "bad", "FAIL (unreachable)");
    }

    if (readyRes.status === "fulfilled") {
      const r = readyRes.value;
      const ready = r.ok && r.text.trim().toUpperCase().includes("READY");
      setIndicator(
        els.readyDot,
        els.readyVal,
        ready ? "ok" : "warn",
        ready ? "READY" : r.text.trim(),
      );
    } else {
      setIndicator(
        els.readyDot,
        els.readyVal,
        "bad",
        "NOT READY (unreachable)",
      );
    }

    if (!els.clientsVal) return;
    if (metricsRes.status !== "fulfilled") {
      els.clientsVal.textContent = "-";
      return;
    }

    const count = parseConnectedClients(metricsRes.value.text);
    els.clientsVal.textContent = count === null ? "-" : String(count);
  }

  function onOrderIdCopied(orderId) {
    if (!orderId) return;
    els.cancelId.value = orderId;
    showMsg(els.cancelMsg, true, "Order ID copied and prefilled for cancel.");
  }

  function isExpanded(key) {
    return expandedLevels.get(key) === true;
  }

  function setExpanded(key, value) {
    if (value) {
      expandedLevels.set(key, true);
      return;
    }
    expandedLevels.delete(key);
  }

  function pruneExpandedLevels(book) {
    const live = new Set();
    const bids = Array.isArray(book?.bids) ? book.bids : [];
    const asks = Array.isArray(book?.asks) ? book.asks : [];
    for (const lvl of bids) {
      const price = lvl?.price ?? lvl?.p ?? lvl?.[0];
      live.add(`bids:${price}`);
    }
    for (const lvl of asks) {
      const price = lvl?.price ?? lvl?.p ?? lvl?.[0];
      live.add(`asks:${price}`);
    }

    for (const key of expandedLevels.keys()) {
      if (!live.has(key)) expandedLevels.delete(key);
    }
  }

  async function refreshSnapshots(reason) {
    const started = Date.now();
    els.refreshVal.textContent = `${fmtTime(Date.now())} (${reason})`;

    const [bookRes, tradesRes] = await Promise.allSettled([
      getBook(),
      getTrades(UI.MAX_TRADES),
    ]);

    if (bookRes.status === "fulfilled") {
      const book = bookRes.value;
      pruneExpandedLevels(book);
      const bids = book.bids ?? book.bidLevels ?? book.buy ?? [];
      const asks = book.asks ?? book.askLevels ?? book.sell ?? [];
      renderLevels(
        els.bidsBody,
        bids,
        "bids",
        isExpanded,
        setExpanded,
        onOrderIdCopied,
      );
      renderLevels(
        els.asksBody,
        asks,
        "asks",
        isExpanded,
        setExpanded,
        onOrderIdCopied,
      );
    } else {
      els.bidsBody.innerHTML = `<tr><td class="left" colspan="3" style="color: var(--bad);">Book snapshot failed</td></tr>`;
      els.asksBody.innerHTML = `<tr><td class="left" colspan="3" style="color: var(--bad);">Book snapshot failed</td></tr>`;
    }

    if (tradesRes.status === "fulfilled") {
      const t = tradesRes.value;
      const rows = Array.isArray(t) ? t : (t.trades ?? []);
      renderTrades(els.tradesBody, rows, UI.MAX_TRADES);
    } else {
      els.tradesBody.innerHTML = `<tr><td class="left" colspan="4" style="color: var(--bad);">Trades snapshot failed</td></tr>`;
    }

    els.refreshVal.textContent = `${fmtTime(Date.now())} (${reason}, ${Date.now() - started}ms)`;
  }

  function validateOrder() {
    const price = Number(els.price.value);
    const qty = Number(els.qty.value);
    if (!Number.isFinite(price) || price <= 0)
      return "Price must be a positive number";
    if (!Number.isFinite(qty) || qty <= 0)
      return "Quantity must be a positive number";
    return null;
  }

  els.submitBtn.addEventListener("click", async () => {
    els.submitBtn.disabled = true;
    els.orderMsg.textContent = "";

    const err = validateOrder();
    if (err) {
      showMsg(els.orderMsg, false, err);
      els.submitBtn.disabled = false;
      return;
    }

    try {
      const res = await placeOrder({
        side: els.side.value,
        price: Number(els.price.value),
        quantity: Number(els.qty.value),
      });

      const orderId = res.orderId ?? res.id ?? null;
      showMsg(
        els.orderMsg,
        true,
        orderId
          ? `Order accepted. orderId=${orderId}`
          : `Order accepted. ${res.message ?? ""}`.trim(),
      );
      if (orderId) els.cancelId.value = orderId;

      await refreshSnapshots("order");
    } catch (e) {
      showMsg(els.orderMsg, false, `Order failed: ${e.message}`);
    } finally {
      els.submitBtn.disabled = false;
    }
  });

  els.cancelBtn.addEventListener("click", async () => {
    els.cancelBtn.disabled = true;
    els.cancelMsg.textContent = "";

    const id = els.cancelId.value.trim();
    if (!id) {
      showMsg(els.cancelMsg, false, "Please paste an orderId to cancel");
      els.cancelBtn.disabled = false;
      return;
    }

    try {
      const res = await cancelOrder({ orderId: id });
      showMsg(
        els.cancelMsg,
        true,
        `Cancel processed. ${res.message ?? ""}`.trim(),
      );
      await refreshSnapshots("cancel");
    } catch (e) {
      showMsg(els.cancelMsg, false, `Cancel failed: ${e.message}`);
    } finally {
      els.cancelBtn.disabled = false;
    }
  });

  connectWebSocket({
    onStatus: (state, label) =>
      setIndicator(els.wsDot, els.wsVal, state, label),
    onPush: () => {
      els.lastPushVal.textContent = fmtTime(Date.now());
      requestRefresh("ws");
    },
  });

  // Boot sequence
  refreshStatus();
  refreshSnapshots("boot");
  setInterval(refreshStatus, UI.STATUS_POLL_MS);
}
