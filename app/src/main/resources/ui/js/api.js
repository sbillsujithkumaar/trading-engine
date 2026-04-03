// await = pause here until it finishes and gives me the result (or throws error)
// ' cache: "no-store" ' means 'don't save this response, always ask the server fresh'

// Reads a raw server response as text
async function readText(res) {
  const t = await res.text();
  return t;
}

// Takes in: a URL string e.g. "/api/book"
// Returns: a { ok, status, text } object. e.g. { ok: true, status: 200, text: "..." }
export async function httpText(url) {
  const res = await fetch(url, { cache: "no-store" });
  return { ok: res.ok, status: res.status, text: await readText(res) };
}

// Takes in: a URL string
// Returns: the parsed JSON response if successful, or throws an error with the response text if not.
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

// Takes in: a URL string and a payload object to send as JSON in the request body e.g. { orderId: "..." }
// Returns: the parsed JSON response if successful, or throws an error with the response text if not.
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

// ----------------------------------------------------------------------
// Convenience wrappers

// CREATE
// Posts into api/order with a payload e.g. { side: "BUY", price: 123.45, quantity: 100 }
export const placeOrder = (p) => postJson("/api/order", p);

// READ
// Returns the health status text from the server, e.g. "OK" or "NOT OK". Throws an error if the request fails.
export const getHealth = () => httpText("/health");
// Returns the readiness status text from the server, e.g. "READY" or "NOT READY". Throws an error if the request fails.
export const getReady = () => httpText("/ready");
// Returns the raw metrics text from the server. Throws an error if the request fails.
export const getMetrics = () => httpText("/metrics");
// Returns the book snapshot as a JSON object. Throws an error if the request fails or the response is not valid JSON.
export const getBook = () => httpJson("/api/book");
// Returns an array of recent trades as JSON objects. Throws an error if the request fails or the response is not valid JSON.
export const getTrades = (limit = 50) =>
  httpJson(`/api/trades?limit=${encodeURIComponent(limit)}`);

// UPDATE
// (Trading engine doesn't have any updates, but if it did, they would go here)

// DELETE
// Posts into api/cancel with a payload e.g. { orderId: "..."}
export const cancelOrder = (p) => postJson("/api/cancel", p);
