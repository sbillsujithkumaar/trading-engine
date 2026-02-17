export function setIndicator(dotEl, textEl, state, label) {
  dotEl.classList.remove("ok", "bad", "warn");
  dotEl.classList.add(state);
  textEl.textContent = label;
}

export function showMsg(el, ok, text) {
  el.classList.remove("ok", "bad");
  el.classList.add(ok ? "ok" : "bad");
  el.textContent = text;
}

export function fmtTime(ts) {
  const d = new Date(ts);
  return isNaN(d.getTime()) ? String(ts) : d.toLocaleTimeString();
}

function safeText(x) {
  return x === null || x === undefined ? "" : String(x);
}

async function copyToClipboard(text) {
  if (!text) return false;
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    // Fallback below.
  }

  try {
    const ta = document.createElement("textarea");
    ta.value = text;
    ta.setAttribute("readonly", "");
    ta.style.position = "fixed";
    ta.style.opacity = "0";
    ta.style.left = "-9999px";
    document.body.appendChild(ta);
    ta.select();
    const ok = document.execCommand("copy");
    document.body.removeChild(ta);
    return ok;
  } catch {
    return false;
  }
}

function levelStateKey(sideKey, level) {
  const price = safeText(level.price ?? level.p ?? level[0]);
  return `${sideKey}:${price}`;
}

export function renderLevels(
  tbody,
  levels,
  sideKey,
  isExpanded,
  setExpanded,
  onCopyOrderId,
) {
  const visibleLimit = 5;

  function rerender() {
    renderLevels(tbody, levels, sideKey, isExpanded, setExpanded, onCopyOrderId);
  }

  tbody.innerHTML = "";
  if (!Array.isArray(levels) || levels.length === 0) {
    const tr = document.createElement("tr");
    tr.innerHTML = `<td class="left" colspan="3" style="color: var(--muted);">—</td>`;
    tbody.appendChild(tr);
    return;
  }

  for (const lvl of levels) {
    const price = safeText(lvl.price ?? lvl.p ?? lvl[0]);
    const qty = safeText(lvl.qty ?? lvl.quantity ?? lvl.q ?? lvl[1]);
    const levelOrders = Array.isArray(lvl.orders) ? lvl.orders : null;
    const count = safeText(
      lvl.count ?? (levelOrders ? levelOrders.length : null) ?? lvl.nOrders ?? lvl[2] ?? "-",
    );

    const levelRow = document.createElement("tr");
    levelRow.className = "level-row";
    levelRow.innerHTML = `<td class="left">${price}</td><td>${qty}</td><td>${count}</td>`;
    tbody.appendChild(levelRow);

    if (!levelOrders || levelOrders.length === 0) {
      continue;
    }

    const stateKey = levelStateKey(sideKey, lvl);
    const expanded = Boolean(isExpanded?.(stateKey));
    const hasOverflow = levelOrders.length > visibleLimit;
    const visibleOrders = expanded ? levelOrders : levelOrders.slice(0, visibleLimit);

    const detailsRow = document.createElement("tr");
    detailsRow.className = "order-details-row";

    const detailsCell = document.createElement("td");
    detailsCell.className = "order-details-cell";
    detailsCell.colSpan = 3;

    const ladder = document.createElement("div");
    ladder.className = "fifo-ladder";

    const meta = document.createElement("div");
    meta.className = "fifo-meta";
    meta.textContent = "Head → Tail";
    ladder.appendChild(meta);

    for (let i = 0; i < visibleOrders.length; i++) {
      const order = visibleOrders[i];
      const rank = i + 1;
      const orderId = safeText(order.orderId ?? order.id ?? order[0]);
      const orderQty = safeText(order.qty ?? order.quantity ?? order.q ?? order[1]);

      const row = document.createElement("div");
      row.className = `fifo-row${rank === 1 ? " fifo-row-head" : ""}`;

      const rankEl = document.createElement("span");
      rankEl.className = `fifo-rank${rank === 1 ? " fifo-rank-head" : ""}`;
      rankEl.textContent = `#${rank}`;

      const idEl = document.createElement("code");
      idEl.className = "order-id";
      idEl.textContent = orderId;

      const qtyEl = document.createElement("span");
      qtyEl.className = "order-qty";
      qtyEl.textContent = `qty ${orderQty}`;

      const copyBtn = document.createElement("button");
      copyBtn.type = "button";
      copyBtn.className = "copy-id-btn";
      copyBtn.textContent = "Copy";
      copyBtn.disabled = !orderId;
      copyBtn.addEventListener("click", async () => {
        const copied = await copyToClipboard(orderId);
        if (copied) onCopyOrderId?.(orderId);
      });

      row.appendChild(rankEl);
      row.appendChild(idEl);
      row.appendChild(qtyEl);
      row.appendChild(copyBtn);
      ladder.appendChild(row);
    }

    if (hasOverflow) {
      const toggleWrap = document.createElement("div");
      toggleWrap.className = "fifo-toggle-wrap";

      const toggle = document.createElement("button");
      toggle.type = "button";
      toggle.className = "fifo-toggle";
      toggle.textContent = expanded
        ? "Show less"
        : `Show ${levelOrders.length - visibleLimit} more`;
      toggle.addEventListener("click", () => {
        setExpanded?.(stateKey, !expanded);
        rerender();
      });

      toggleWrap.appendChild(toggle);
      ladder.appendChild(toggleWrap);
    }

    detailsCell.appendChild(ladder);
    detailsRow.appendChild(detailsCell);
    tbody.appendChild(detailsRow);
  }
}

export function renderTrades(tbody, rows, max = 25) {
  tbody.innerHTML = "";
  if (!Array.isArray(rows) || rows.length === 0) {
    const tr = document.createElement("tr");
    tr.innerHTML = `<td class="left" colspan="4" style="color: var(--muted);">—</td>`;
    tbody.appendChild(tr);
    return;
  }
  for (const t of rows.slice(0, max)) {
    const ts = t.ts ?? t.time ?? t.timestamp ?? "";
    const price = t.price ?? t.p ?? "";
    const qty = t.qty ?? t.q ?? t.quantity ?? "";
    const info = t.info ?? t.side ?? t.meta ?? "";
    const tr = document.createElement("tr");
    tr.innerHTML = `<td class="left">${fmtTime(ts)}</td><td>${safeText(price)}</td><td>${safeText(qty)}</td><td class="left">${safeText(info)}</td>`;
    tbody.appendChild(tr);
  }
}
