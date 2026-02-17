async function readText(res) {
  const t = await res.text();
  return t;
}

export async function httpText(url) {
  const res = await fetch(url, { cache: "no-store" });
  return { ok: res.ok, status: res.status, text: await readText(res) };
}

export async function httpJson(url) {
  const res = await fetch(url, { cache: "no-store" });
  const text = await readText(res);
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${text}`);
  try {
    return JSON.parse(text);
  } catch {
    throw new Error(`Invalid JSON from ${url}: ${text}`);
  }
}

export async function postJson(url, payload) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const text = await readText(res);
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${text}`);
  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

// Convenience wrappers (keeps app.js clean)
export const getHealth = () => httpText("/health");
export const getReady = () => httpText("/ready");
export const getMetrics = () => httpText("/metrics");
export const getBook = () => httpJson("/api/book");
export const getTrades = (limit = 50) => httpJson(`/api/trades?limit=${encodeURIComponent(limit)}`);
export const placeOrder = (p) => postJson("/api/order", p);
export const cancelOrder = (p) => postJson("/api/cancel", p);
