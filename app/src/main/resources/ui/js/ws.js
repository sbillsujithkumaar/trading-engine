export function connectWebSocket({ onStatus, onPush }) {
  const scheme = location.protocol === "https:" ? "wss" : "ws";
  const wsUrl = `${scheme}://${location.host}/ws`;

  let ws;
  try {
    ws = new WebSocket(wsUrl);
  } catch {
    onStatus?.("bad", "WS init failed");
    return;
  }

  onStatus?.("warn", "Connecting…");

  ws.onopen = () => onStatus?.("ok", "Connected");
  ws.onmessage = () => onPush?.();
  ws.onclose = () => {
    onStatus?.("warn", "Disconnected (retrying)");
    setTimeout(() => connectWebSocket({ onStatus, onPush }), 800);
  };
  ws.onerror = () => {
    onStatus?.("warn", "Error (retrying)");
    try {
      ws.close();
    } catch {}
  };

  return ws;
}
