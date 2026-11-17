import {
  deleteDoc,
  doc,
  serverTimestamp,
  setDoc,
  updateDoc
} from "https://www.gstatic.com/firebasejs/11.14.0/firebase-firestore.js";
import { db, INDIA_STATES, state } from "./state.js";
import {
  $,
  activeProfile,
  closeModal,
  escapeHtml,
  fmtPct,
  fmtQty,
  inr,
  isPlainNonGst,
  openModal,
  profileScoped,
  shareToWhatsApp,
  showToast,
  toMs
} from "./utils.js";

export function resolveLedgerRange(period) {
  if (period === "all") return { startMs: 0, endMs: Number.MAX_SAFE_INTEGER, label: "All Transactions" };
  const now = new Date();
  const y = now.getFullYear();
  const m = now.getMonth();
  const fyStart = m >= 3 ? y : y - 1;
  const fmt = (d) => d.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
  const sod = (yr, mo, dy) => new Date(yr, mo, dy, 0, 0, 0, 0).getTime();
  const eom = (yr, mo) => new Date(yr, mo + 1, 0, 23, 59, 59, 999).getTime();

  if (period === "month") {
    const start = sod(y, m, 1);
    const end = eom(y, m);
    return { startMs: start, endMs: end, label: new Date(start).toLocaleDateString("en-IN", { month: "long", year: "numeric" }) };
  }
  if (period === "quarter") {
    let q = "Q4";
    let sy = y;
    let sm = 0;
    let em = 2;
    if (m >= 3 && m <= 5) { q = "Q1"; sm = 3; em = 5; }
    else if (m >= 6 && m <= 8) { q = "Q2"; sm = 6; em = 8; }
    else if (m >= 9 && m <= 11) { q = "Q3"; sm = 9; em = 11; }
    const start = sod(sy, sm, 1);
    const end = eom(sy, em);
    return { startMs: start, endMs: end, label: `${q} (${fmt(new Date(start))} – ${fmt(new Date(end))})` };
  }
  if (period === "half_year") {
    const isH1 = m >= 3 && m <= 8;
    const start = isH1 ? sod(y, 3, 1) : sod(fyStart, 9, 1);
    const end = isH1 ? eom(y, 8) : eom(fyStart + 1, 2);
    return { startMs: start, endMs: end, label: `${isH1 ? "H1" : "H2"} (${fmt(new Date(start))} – ${fmt(new Date(end))})` };
  }
  if (period === "prev_year") {
    const py = fyStart - 1;
    const start = sod(py, 3, 1);
    const end = eom(py + 1, 2);
    return { startMs: start, endMs: end, label: `FY ${py}-${String((py + 1) % 100).padStart(2, "0")} (${fmt(new Date(start))} – ${fmt(new Date(end))})` };
  }
  const start = sod(fyStart, 3, 1);
  const end = eom(fyStart + 1, 2);
  return { startMs: start, endMs: end, label: `FY ${fyStart}-${String((fyStart + 1) % 100).padStart(2, "0")} (${fmt(new Date(start))} – ${fmt(new Date(end))})` };
}

export function computeProfitPct(profit, cost, rev) {
  if (cost > 0.0001) return (profit / cost) * 100;
  if (rev > 0.0001) return (profit / rev) * 100;
  return 0;
}

export function computeLedgerAnalytics(period) {
  const range = resolveLedgerRange(period);
  const inRange = (ts) => ts >= range.startMs && ts <= range.endMs;

  const invList = profileScoped(state.invoices).filter((i) => inRange(toMs(i.createdAt)));
  const expList = profileScoped(state.expenses).filter((e) => inRange(toMs(e.createdAt)));
  const entryList = profileScoped(state.dashboardEntries).filter((d) => inRange(toMs(d.createdAt)));
  const stockList = profileScoped(state.inventory);
  const partyList = profileScoped(state.parties);

  const byId = new Map(stockList.map((s) => [s.id, s]));
  const bySku = new Map(stockList.filter((s) => s.sku).map((s) => [String(s.sku).toLowerCase(), s]));
  const byName = new Map(stockList.filter((s) => s.name).map((s) => [String(s.name).trim().toLowerCase(), s]));
  const findStock = (l) =>
    (l.itemId && byId.get(l.itemId)) ||
    (l.sku && bySku.get(String(l.sku).toLowerCase())) ||
    byName.get(String(l.name || "").trim().toLowerCase());

  const itemAcc = new Map();
  stockList.forEach((s) =>
    itemAcc.set(s.id, {
      item: s,
      name: s.name,
      sku: s.sku,
      hsnCode: s.hsnCode || "",
      unit: s.unit || "PCS",
      stockQty: Number(s.stockQty || 0),
      stockVal: Number(s.stockQty || 0) * Number(s.purchasePrice || 0),
      soldQty: 0,
      sales: 0,
      cost: 0
    })
  );

  const partyAcc = new Map();
  partyList.forEach((p) => {
    const netRaw = Number(p.openingDue || 0) + Number(p.totalBilled || 0) - Number(p.totalPaid || 0);
    partyAcc.set(p.id, {
      party: p,
      name: p.name,
      type: p.partyType || "customer",
      phone: p.phone || "",
      gstin: p.gstin || "",
      due: Math.max(0, netRaw),
      advance: netRaw < 0 ? Math.abs(netRaw) : 0,
      txCount: 0,
      sales: 0,
      cost: 0
    });
  });

  const partyByName = new Map(partyList.map((p) => [String(p.name || "").trim().toLowerCase(), p]));
  const txRows = [];
  let totalSales = 0;
  let totalCost = 0;

  invList.forEach((inv) => {
    const rawSub = Number(inv.subtotal || 0) || (inv.lineItems || []).reduce((s, l) => s + Number(l.taxableAmount || l.qty * l.unitPrice || 0), 0);
    const netRev = Math.max(0, rawSub - Number(inv.discountAmount || 0));
    const factor = rawSub > 0.0001 ? netRev / rawSub : 1;
    let invCost = 0;
    (inv.lineItems || []).forEach((l) => {
      const st = findStock(l);
      const unitCost = Number(st?.purchasePrice || 0);
      const lRev = Number(l.taxableAmount || l.qty * l.unitPrice || 0) * factor;
      const lCost = Number(l.qty || 0) * unitCost;
      invCost += lCost;
      const k = st ? st.id : `name:${String(l.name || "").trim().toLowerCase()}`;
      if (!itemAcc.has(k)) {
        itemAcc.set(k, {
          item: st,
          name: l.name,
          sku: l.sku || "",
          hsnCode: l.hsnCode || "",
          unit: l.unit || "PCS",
          stockQty: 0,
          stockVal: 0,
          soldQty: 0,
          sales: 0,
          cost: 0
        });
      }
      const ia = itemAcc.get(k);
      ia.soldQty += Number(l.qty || 0);
      ia.sales += lRev;
      ia.cost += lCost;
    });
    const invProfit = netRev - invCost;
    const invPct = computeProfitPct(invProfit, invCost, netRev);
    totalSales += netRev;
    totalCost += invCost;

    const matchedP =
      (inv.partyId && partyAcc.get(inv.partyId)?.party) ||
      partyByName.get(String(inv.partyName || "").trim().toLowerCase());
    const pk = matchedP ? matchedP.id : `walkin:${String(inv.partyName || "").trim().toLowerCase()}`;
    if (!partyAcc.has(pk)) {
      partyAcc.set(pk, {
        party: matchedP || null,
        name: inv.partyName || "Walk-in",
        type: "customer",
        phone: "",
        gstin: inv.partyGstin || "",
        due: Math.max(0, Number(inv.grandTotal || 0) - Number(inv.paidAmount || 0)),
        advance: 0,
        txCount: 0,
        sales: 0,
        cost: 0
      });
    }
    const pa = partyAcc.get(pk);
    pa.txCount += 1;
    pa.sales += netRev;
    pa.cost += invCost;

    txRows.push({
      ts: toMs(inv.createdAt),
      type: isPlainNonGst() ? "BILL" : "SALE",
      ref: inv.invoiceNumber,
      party: inv.partyName,
      items: (inv.lineItems || []).map((l) => `${l.name} (${fmtQty(l.qty)})`).join(", "),
      amount: Number(inv.grandTotal || 0),
      profit: invProfit,
      pct: invPct
    });
  });

  let totalExpenses = 0;
  expList.forEach((e) => {
    totalExpenses += Number(e.amount || 0);
    txRows.push({
      ts: toMs(e.createdAt),
      type: e.category === "purchases" ? "PURCHASE" : "EXPENSE",
      ref: e.referenceNo || e.category || "EXP",
      party: e.partyName || e.title,
      items: e.title,
      amount: Number(e.amount || 0),
      profit: null,
      pct: null
    });
  });

  entryList.forEach((en) => {
    const isExp = en.category === "expense";
    if (isExp) totalExpenses += Number(en.amount || 0);
    else totalSales += Number(en.amount || 0);
    txRows.push({
      ts: toMs(en.createdAt),
      type: `QUICK ${String(en.category || "sale").toUpperCase()}`,
      ref: en.referenceCode || "ENTRY",
      party: en.partyName || en.title,
      items: en.title,
      amount: Number(en.amount || 0),
      profit: null,
      pct: null
    });
  });

  const netProfit = totalCost > 0 ? totalSales - totalCost : totalSales - totalExpenses;
  const netPct = computeProfitPct(netProfit, totalCost > 0 ? totalCost : totalExpenses, totalSales);
  const stockValue = stockList.reduce((s, it) => s + Number(it.stockQty || 0) * Number(it.purchasePrice || 0), 0);

  return {
    period,
    rangeLabel: range.label,
    totalSales,
    totalCost,
    totalExpenses,
    netProfit,
    netPct,
    stockValue,
    invoices: invList,
    expenses: expList,
    entries: entryList,
    parties: Array.from(partyAcc.values()).map((p) => {
      const profit = p.sales - p.cost;
      return { ...p, profit, pct: computeProfitPct(profit, p.cost, p.sales), hasSales: p.sales > 0 || p.cost > 0 };
    }),
    items: Array.from(itemAcc.values()).map((it) => {
      const profit = it.sales - it.cost;
      return { ...it, profit, pct: it.soldQty > 0 ? computeProfitPct(profit, it.cost, it.sales) : 0, hasSold: it.soldQty > 0 };
    }),
    transactions: txRows.sort((a, b) => b.ts - a.ts)
  };
}

export function renderParties() {
  const q = ($("party-search")?.value || "").trim().toLowerCase();
  const analytics = computeLedgerAnalytics(state.ledgerPeriod);
  $("ledger-range-label").textContent = analytics.rangeLabel;
  const plBadge = `<span class="pill ${analytics.netProfit >= 0 ? "pill-profit" : "pill-loss"}">${analytics.netProfit >= 0 ? "Profit" : "Loss"} ${fmtPct(analytics.netPct)}</span>`;

  const scopedParties = profileScoped(state.parties);
  const totalToCollect = scopedParties
    .filter((p) => (p.partyType || "customer") === "customer")
    .reduce((s, p) => s + Math.max(0, Number(p.openingDue || 0) + Number(p.totalBilled || 0) - Number(p.totalPaid || 0)), 0);
  const totalToPay = scopedParties
    .filter((p) => p.partyType === "supplier")
    .reduce((s, p) => s + Math.max(0, Number(p.openingDue || 0) + Number(p.totalBilled || 0) - Number(p.totalPaid || 0)), 0);

  $("ledger-kpi-strip").innerHTML = `
    <div class="kpi-card"><div class="kpi-label">Period Sales</div><div class="kpi-value mono">${inr(analytics.totalSales)}</div></div>
    <div class="kpi-card"><div class="kpi-label">Net Profit / Loss</div><div class="kpi-value mono ${analytics.netProfit >= 0 ? "text-profit" : "text-loss"}">${inr(analytics.netProfit)}</div><div>${plBadge}</div></div>
    <div class="kpi-card"><div class="kpi-label">To Collect / To Pay</div><div class="kpi-value mono" style="font-size:18px;">${inr(totalToCollect)} <span class="muted">/ Pay ${inr(totalToPay)}</span></div></div>
    <div class="kpi-card"><div class="kpi-label">Stock Valuation</div><div class="kpi-value mono">${inr(analytics.stockValue)}</div></div>
  `;

  $("ledger-parties-wrap").classList.toggle("hidden", state.ledgerView !== "parties");
  $("ledger-items-wrap").classList.toggle("hidden", state.ledgerView !== "items");
  $("ledger-tx-wrap").classList.toggle("hidden", state.ledgerView !== "transactions");
  $("ledger-quick-wrap").classList.toggle("hidden", state.ledgerView !== "quick");

  document.querySelectorAll("[data-party-type]").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.partyType === state.partyTypeFilter);
  });

  const partyRows = analytics.parties.filter((p) => {
    if (state.partyTypeFilter !== "all" && p.type !== state.partyTypeFilter) return false;
    if (!q) return true;
    return [p.name, p.phone, p.gstin].some((v) => String(v || "").toLowerCase().includes(q));
  });

  $("parties-table").innerHTML = partyRows.length
    ? partyRows
        .map((p) => {
          const plCell = p.hasSales
            ? `<span class="pill ${p.profit >= 0 ? "pill-profit" : "pill-loss"}">${inr(p.profit)} (${fmtPct(p.pct)})</span>`
            : `<span class="muted">—</span>`;
          const dueLabel =
            p.advance > 0
              ? `<span class="pill pill-profit">Adv ${inr(p.advance)}</span>`
              : `<span class="mono bold">${inr(p.due)}</span>`;
          return `
        <tr>
          <td>
            <div class="bold">${escapeHtml(p.name)}</div>
            ${p.party?.state ? `<div class="muted">${escapeHtml(p.party.state)}</div>` : ""}
          </td>
          <td><span class="pill pill-info">${escapeHtml(String(p.type || "customer").toUpperCase())}</span></td>
          <td class="mono">${escapeHtml(p.phone || "—")}<br/><span class="muted">${escapeHtml(p.gstin || "")}</span></td>
          <td class="right mono">${inr(p.sales)}</td>
          <td class="right">${plCell}</td>
          <td class="right">${dueLabel}</td>
          <td class="right">
            ${
              p.party
                ? `<div class="row-actions">
                    <button class="btn btn-sm btn-ghost" data-action="ledger-party" data-id="${escapeHtml(p.party.id)}">Statement</button>
                    <button class="btn btn-sm btn-primary" data-action="pay-party" data-id="${escapeHtml(p.party.id)}">${p.type === "supplier" ? "Pay" : "Receive"}</button>
                    <button class="btn btn-sm btn-ghost" data-action="edit-party" data-id="${escapeHtml(p.party.id)}">Edit</button>
                    <button class="btn btn-sm btn-ghost" data-action="wa-party" data-id="${escapeHtml(p.party.id)}">WhatsApp</button>
                    <button class="btn btn-sm btn-danger-soft" data-action="del-party" data-id="${escapeHtml(p.party.id)}">Del</button>
                  </div>`
                : ""
            }
          </td>
        </tr>`;
        })
        .join("")
    : `<tr><td colspan="7" class="muted">No matching parties found.</td></tr>`;

  const itemRows = analytics.items.filter((it) => !q || [it.name, it.sku, it.hsnCode].some((v) => String(v || "").toLowerCase().includes(q)));
  $("ledger-items-table").innerHTML = itemRows.length
    ? itemRows
        .map((it) => {
          const plCell = it.hasSold
            ? `<span class="pill ${it.profit >= 0 ? "pill-profit" : "pill-loss"}">${inr(it.profit)} (${fmtPct(it.pct)})</span>`
            : `<span class="muted">No Sales</span>`;
          return `
        <tr>
          <td class="bold">${escapeHtml(it.name)}<br/><span class="muted mono">${escapeHtml(it.sku || "")} ${it.hsnCode ? `• HSN ${escapeHtml(it.hsnCode)}` : ""}</span></td>
          <td class="right mono">${fmtQty(it.stockQty)} ${escapeHtml(it.unit)}</td>
          <td class="right mono">${inr(it.stockVal)}</td>
          <td class="right mono">${fmtQty(it.soldQty)}</td>
          <td class="right mono">${inr(it.sales)}</td>
          <td class="right mono">${inr(it.cost)}</td>
          <td class="right">${plCell}</td>
        </tr>`;
        })
        .join("")
    : `<tr><td colspan="7" class="muted">No items yet.</td></tr>`;

  const txFiltered = analytics.transactions.filter((t) => !q || [t.party, t.items, t.ref, t.type].some((v) => String(v || "").toLowerCase().includes(q)));
  $("ledger-tx-table").innerHTML = txFiltered.length
    ? txFiltered
        .map((t) => {
          const plCell = t.profit !== null ? `<span class="pill ${t.profit >= 0 ? "pill-profit" : "pill-loss"}">${inr(t.profit)} (${fmtPct(t.pct)})</span>` : `<span class="muted">—</span>`;
          return `
        <tr>
          <td class="mono">${new Date(t.ts).toLocaleDateString("en-IN")}</td>
          <td><span class="pill pill-info">${escapeHtml(t.type)}</span> <span class="mono bold">${escapeHtml(t.ref)}</span></td>
          <td><strong>${escapeHtml(t.party)}</strong> — ${escapeHtml(t.items)}</td>
          <td class="right mono bold">${inr(t.amount)}</td>
          <td class="right">${plCell}</td>
        </tr>`;
        })
        .join("")
    : `<tr><td colspan="5" class="muted">No transactions in ${escapeHtml(analytics.rangeLabel)}.</td></tr>`;

  const quickEntries = profileScoped(state.dashboardEntries);
  $("quick-entries-table").innerHTML = quickEntries.length
    ? quickEntries
        .map(
          (en) => `
      <tr>
        <td class="bold">${escapeHtml(en.title)}</td>
        <td><span class="pill pill-info">${escapeHtml(String(en.category || "sale").toUpperCase())}</span></td>
        <td>${escapeHtml(en.partyName || "—")}</td>
        <td class="mono">${escapeHtml(en.referenceCode || "—")}</td>
        <td><span class="pill pill-${en.status === "paid" ? "paid" : "partial"}">${escapeHtml(String(en.status || "pending").toUpperCase())}</span></td>
        <td class="right mono bold">${inr(en.amount)}</td>
        <td class="right">
          <div class="row-actions">
            ${en.status === "pending" ? `<button class="btn btn-sm btn-primary" data-qe-action="paid" data-id="${escapeHtml(en.id)}">Mark Paid</button>` : ""}
            <button class="btn btn-sm btn-danger-soft" data-qe-action="del" data-id="${escapeHtml(en.id)}">Delete</button>
          </div>
        </td>
      </tr>`
        )
        .join("")
    : `<tr><td colspan="7" class="muted">No quick entries yet. Use "+ Quick Entry" to log fast sales, receivables, or cash expenses.</td></tr>`;
}

export function renderReports() {
  const analytics = computeLedgerAnalytics(state.ledgerPeriod);
  $("reports-range-label").textContent = analytics.rangeLabel;
  const invoices = analytics.invoices;
  const expenses = analytics.expenses;
  const plain = isPlainNonGst();

  const taxable = invoices.reduce((s, i) => s + Number(i.subtotal || 0), 0);
  const outGst = invoices.reduce((s, i) => s + Number(i.gstTotal || 0), 0);
  const inGst = expenses.reduce((s, e) => s + Number(e.gstInputAmount || 0), 0);
  const netGst = Math.max(0, outGst - inGst);
  const profit = analytics.netProfit;

  let cgst = 0;
  let sgst = 0;
  let igst = 0;
  let b2bTotal = 0;
  let b2bCount = 0;
  let b2cTotal = 0;
  const slabs = new Map();
  const hsnMap = new Map();

  invoices.forEach((inv) => {
    if (inv.taxMode === "cgst_sgst") {
      cgst += Number(inv.gstTotal || 0) / 2;
      sgst += Number(inv.gstTotal || 0) / 2;
    } else if (inv.taxMode === "igst") {
      igst += Number(inv.gstTotal || 0);
    }
    if (inv.partyGstin) {
      b2bCount += 1;
      b2bTotal += Number(inv.grandTotal || 0);
    } else {
      b2cTotal += Number(inv.grandTotal || 0);
    }
    (inv.lineItems || []).forEach((l) => {
      const rate = Math.round(Number(l.gstRatePercent || 0));
      const prev = slabs.get(rate) || { taxable: 0, tax: 0 };
      prev.taxable += Number(l.taxableAmount || 0);
      prev.tax += Number(l.gstAmount || 0);
      slabs.set(rate, prev);

      const hsnKey = (l.hsnCode || "UNSPECIFIED").toUpperCase();
      const hp = hsnMap.get(hsnKey) || { hsn: hsnKey, qty: 0, taxable: 0, tax: 0 };
      hp.qty += Number(l.qty || 0);
      hp.taxable += Number(l.taxableAmount || 0);
      hp.tax += Number(l.gstAmount || 0);
      hsnMap.set(hsnKey, hp);
    });
  });

  $("rep-taxable-sales").textContent = inr(taxable);
  $("rep-output-gst").textContent = inr(outGst);
  $("rep-input-gst").textContent = inr(inGst);
  $("rep-net-gst").textContent = inr(netGst);

  $("rep-pl-summary").innerHTML = `
    <div class="kpi-card"><div class="kpi-label">Total Tax Invoices</div><div class="kpi-value mono">${invoices.length}</div><div class="kpi-sub">${b2bCount} B2B (${inr(b2bTotal)}) • B2C ${inr(b2cTotal)}</div></div>
    <div class="kpi-card"><div class="kpi-label">GST Split (CGST / SGST / IGST)</div><div class="kpi-value mono" style="font-size:15px;">${plain ? "Plain Non-GST" : `${inr(cgst)} / ${inr(sgst)} / ${inr(igst)}`}</div></div>
    <div class="kpi-card"><div class="kpi-label">Gross Revenue (Incl. GST)</div><div class="kpi-value mono">${inr(taxable + outGst)}</div></div>
    <div class="kpi-card"><div class="kpi-label">Net Profit / Loss</div><div class="kpi-value mono ${profit >= 0 ? "text-profit" : "text-loss"}">${inr(profit)} (${fmtPct(analytics.netPct)})</div></div>
  `;

  const slabEntries = Array.from(slabs.entries()).sort((a, b) => a[0] - b[0]);
  const maxSlabTaxable = Math.max(1, ...slabEntries.map(([, v]) => v.taxable));
  $("rep-slabs-wrap").innerHTML = slabEntries.length
    ? slabEntries
        .map(
          ([rate, v]) => `
        <div class="slab-row">
          <div style="display:flex;justify-content:space-between;">
            <span class="bold">${rate}% GST Slab</span>
            <span class="mono muted">Taxable ${inr(v.taxable)} • Tax ${inr(v.tax)}</span>
          </div>
          <div class="progress-track"><div class="progress-fill" style="width:${Math.max(4, Math.round((v.taxable / maxSlabTaxable) * 100))}%"></div></div>
        </div>`
        )
        .join("")
    : `<p class="muted">No GST invoice slabs in ${escapeHtml(analytics.rangeLabel)}.</p>`;

  const hsnRows = Array.from(hsnMap.values()).sort((a, b) => b.taxable - a.taxable);
  $("rep-hsn-table").innerHTML = hsnRows.length
    ? hsnRows
        .map(
          (h) => `
      <tr>
        <td class="mono bold">${escapeHtml(h.hsn)}</td>
        <td class="right mono">${fmtQty(h.qty)}</td>
        <td class="right mono">${inr(h.taxable)}</td>
        <td class="right mono">${inr(h.tax)}</td>
        <td class="right mono bold">${inr(h.taxable + h.tax)}</td>
      </tr>`
        )
        .join("")
    : `<tr><td colspan="5" class="muted">No HSN line items recorded in this period.</td></tr>`;

  const collectedPeriod = invoices.reduce((s, i) => s + Number(i.paidAmount || 0), 0);
  const pendingExpPeriod = expenses.filter((e) => e.status === "pending").reduce((s, e) => s + Number(e.amount || 0), 0);
  if ($("rep-sales-expense-summary")) {
    $("rep-sales-expense-summary").innerHTML = `
      <div class="kpi-card"><div class="kpi-label">Period Sales (Taxable)</div><div class="kpi-value mono">${inr(analytics.totalSales)}</div></div>
      <div class="kpi-card"><div class="kpi-label">Collected on Bills</div><div class="kpi-value mono text-emerald">${inr(collectedPeriod)}</div></div>
      <div class="kpi-card"><div class="kpi-label">Operating Expenses</div><div class="kpi-value mono">${inr(analytics.totalExpenses)}</div><div class="kpi-sub">Pending Payables: ${inr(pendingExpPeriod)}</div></div>
      <div class="kpi-card"><div class="kpi-label">Net Cash Margin</div><div class="kpi-value mono ${analytics.totalSales - analytics.totalExpenses >= 0 ? "text-profit" : "text-loss"}">${inr(analytics.totalSales - analytics.totalExpenses)}</div></div>
    `;
  }

  const scopedStock = profileScoped(state.inventory);
  const totalQty = scopedStock.reduce((s, it) => s + Number(it.stockQty || 0), 0);
  const retailVal = scopedStock.reduce((s, it) => s + Number(it.stockQty || 0) * Number(it.salePrice || 0), 0);
  const lowCount = scopedStock.filter((it) => Number(it.stockQty || 0) <= Number(it.lowStockThreshold || 5)).length;
  if ($("rep-stock-val-summary")) {
    $("rep-stock-val-summary").innerHTML = `
      <div class="kpi-card"><div class="kpi-label">Total Stock SKUs</div><div class="kpi-value mono">${scopedStock.length}</div><div class="kpi-sub">${fmtQty(totalQty)} total units</div></div>
      <div class="kpi-card"><div class="kpi-label">Cost Valuation</div><div class="kpi-value mono">${inr(analytics.stockValue)}</div></div>
      <div class="kpi-card"><div class="kpi-label">Estimated Retail Value</div><div class="kpi-value mono text-emerald">${inr(retailVal)}</div></div>
      <div class="kpi-card"><div class="kpi-label">Low / Out Stock Alerts</div><div class="kpi-value mono ${lowCount > 0 ? "text-amber" : "text-emerald"}">${lowCount}</div></div>
    `;
  }

  const focus = state.reportFocus || "all";
  document.querySelectorAll("[data-report-focus]").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.reportFocus === focus);
  });
  const showAll = focus === "all";
  $("rep-gst-kpi-grid")?.classList.toggle("hidden", !showAll && focus !== "gst");
  $("rep-pl-card")?.classList.toggle("hidden", !showAll && focus !== "pl");
  $("rep-sales-expense-card")?.classList.toggle("hidden", !showAll && focus !== "sales_exp");
  $("rep-stock-val-card")?.classList.toggle("hidden", !showAll && focus !== "stock_val");
  $("rep-slabs-card")?.classList.toggle("hidden", !showAll && focus !== "gst");
  $("rep-hsn-card")?.classList.toggle("hidden", !showAll && focus !== "gst");
}

export function printLedgerStatement() {
  const prof = activeProfile();
  const a = computeLedgerAnalytics(state.ledgerPeriod);
  const partyRows = a.parties
    .map((p) => `<tr><td>${escapeHtml(p.name)}</td><td class="right mono">${inr(p.sales)}</td><td class="right mono" style="color:${p.profit >= 0 ? "#15803D" : "#DC2626"}">${p.hasSales ? `${inr(p.profit)} (${fmtPct(p.pct)})` : "—"}</td><td class="right mono">${inr(p.due)}</td></tr>`)
    .join("");
  const itemRows = a.items
    .map((it) => `<tr><td>${escapeHtml(it.name)}</td><td class="right mono">${fmtQty(it.stockQty)} ${escapeHtml(it.unit)}</td><td class="right mono">${fmtQty(it.soldQty)}</td><td class="right mono">${inr(it.sales)}</td><td class="right mono" style="color:${it.profit >= 0 ? "#15803D" : "#DC2626"}">${it.hasSold ? `${inr(it.profit)} (${fmtPct(it.pct)})` : "—"}</td></tr>`)
    .join("");
  const txRows = a.transactions
    .map((t) => `<tr><td class="mono">${new Date(t.ts).toLocaleDateString("en-IN")}</td><td>${escapeHtml(t.type)} ${escapeHtml(t.ref)}</td><td>${escapeHtml(t.party)} — ${escapeHtml(t.items)}</td><td class="right mono">${inr(t.amount)}</td><td class="right mono" style="color:${(t.profit ?? 0) >= 0 ? "#15803D" : "#DC2626"}">${t.pct !== null ? fmtPct(t.pct) : "—"}</td></tr>`)
    .join("");

  $("print-sheet").innerHTML = `
    <div style="border:1px solid #334155;padding:20px;font-size:12px;">
      <div style="border-bottom:2px solid #0B253C;padding-bottom:10px;margin-bottom:12px;">
        <h2 style="margin:0;">${escapeHtml(prof.businessName || "Finboss Billing No.1 ERP")} — Ledger &amp; P&amp;L Statement</h2>
        <div>Period: ${escapeHtml(a.rangeLabel)} • Net P&amp;L: <strong style="color:${a.netProfit >= 0 ? "#15803D" : "#DC2626"}">${inr(a.netProfit)} (${fmtPct(a.netPct)})</strong> • Stock Value: ${inr(a.stockValue)}</div>
      </div>
      <h4>Party-Wise Profit / Loss (%)</h4>
      <table style="width:100%;border-collapse:collapse;margin-bottom:14px;"><thead><tr><th>Party</th><th class="right">Sales</th><th class="right">Profit / Loss</th><th class="right">Due</th></tr></thead><tbody>${partyRows}</tbody></table>
      <h4>Item &amp; Stock Profit / Loss (%)</h4>
      <table style="width:100%;border-collapse:collapse;margin-bottom:14px;"><thead><tr><th>Item</th><th class="right">Stock</th><th class="right">Sold</th><th class="right">Sales</th><th class="right">Profit / Loss</th></tr></thead><tbody>${itemRows}</tbody></table>
      <h4>Transactions (${a.transactions.length})</h4>
      <table style="width:100%;border-collapse:collapse;"><thead><tr><th>Date</th><th>Voucher</th><th>Party &amp; Items</th><th class="right">Amount</th><th class="right">P&amp;L %</th></tr></thead><tbody>${txRows}</tbody></table>
    </div>
  `;
  window.print();
}

export function openPartyModal(existingParty = null) {
  const isEditing = Boolean(existingParty);
  const prof = activeProfile();
  openModal(`
    <h3>${isEditing ? `Edit Party • ${escapeHtml(existingParty.name)}` : "Add Customer / Supplier"}</h3>
    <div class="form-grid">
      <div class="form-field"><label>Party Name *</label><input id="m-p-name" class="input-text" value="${escapeHtml(existingParty?.name || "")}" /></div>
      <div class="form-field"><label>Type</label><select id="m-p-type" class="input-select">
        <option value="customer" ${(existingParty?.partyType || "customer") === "customer" ? "selected" : ""}>Customer</option>
        <option value="supplier" ${existingParty?.partyType === "supplier" ? "selected" : ""}>Supplier</option>
      </select></div>
      <div class="form-field"><label>Phone</label><input id="m-p-phone" class="input-text" value="${escapeHtml(existingParty?.phone || "")}" placeholder="+919876543210" /></div>
      <div class="form-field"><label>GSTIN</label><input id="m-p-gstin" class="input-text mono" value="${escapeHtml(existingParty?.gstin || "")}" /></div>
      <div class="form-field span-2"><label>Address</label><input id="m-p-addr" class="input-text" value="${escapeHtml(existingParty?.address || "")}" /></div>
      <div class="form-field"><label>State</label><select id="m-p-state" class="input-select">
        <option value="">Select State</option>
        ${INDIA_STATES.map((s) => `<option value="${s}" ${s.toLowerCase() === String(existingParty?.state || prof.state || "").toLowerCase() ? "selected" : ""}>${s}</option>`).join("")}
      </select></div>
      <div class="form-field"><label>PIN Code</label><input id="m-p-pin" class="input-text mono" value="${escapeHtml(existingParty?.pincode || "")}" maxlength="12" /></div>
      <div class="form-field"><label>Opening Due (₹)</label><input id="m-p-open" type="number" min="0" class="input-text mono" value="${Number(existingParty?.openingDue || 0)}" /></div>
      <div class="form-field"><label>Credit Limit (₹)</label><input id="m-p-limit" type="number" min="0" class="input-text mono" value="${Number(existingParty?.creditLimit ?? 50000)}" /></div>
      <div class="form-field span-2"><label>Notes</label><input id="m-p-notes" class="input-text" value="${escapeHtml(existingParty?.notes || "")}" /></div>
    </div>
    <p id="m-err" class="error-box hidden"></p>
    <div class="modal-actions">
      <button id="m-cancel" class="btn btn-ghost">Cancel</button>
      <button id="m-save" class="btn btn-primary">${isEditing ? "Update Party" : "Save Party"}</button>
    </div>
  `);
  $("m-cancel").onclick = closeModal;
  $("m-save").onclick = async () => {
    const name = $("m-p-name").value.trim().slice(0, 100);
    if (!name) {
      $("m-err").textContent = "Party name is required.";
      $("m-err").classList.remove("hidden");
      return;
    }
    const uid = state.user.uid;
    const payload = {
      name,
      partyType: $("m-p-type").value === "supplier" ? "supplier" : "customer",
      phone: $("m-p-phone").value.trim().slice(0, 25),
      gstin: $("m-p-gstin").value.trim().toUpperCase().slice(0, 30),
      address: $("m-p-addr").value.trim().slice(0, 200),
      state: $("m-p-state").value.trim().slice(0, 60),
      pincode: $("m-p-pin").value.replace(/[^0-9-]/g, "").slice(0, 12),
      openingDue: Math.max(0, Number($("m-p-open").value || 0)),
      creditLimit: Math.max(0, Number($("m-p-limit").value || 0)),
      notes: $("m-p-notes").value.trim().slice(0, 200),
      updatedAt: serverTimestamp()
    };
    if (isEditing) {
      await updateDoc(doc(db, "users", uid, "parties", existingParty.id), payload);
    } else {
      await setDoc(doc(db, "users", uid, "parties", `pty_${Date.now()}`), {
        userId: uid,
        businessProfileId: state.activeProfileId || "main_1",
        ...payload,
        totalBilled: 0,
        totalPaid: 0,
        createdAt: serverTimestamp()
      });
    }
    closeModal();
    showToast(`${isEditing ? "Updated" : "Added"} party ${name}!`);
  };
}

export function openPartyLedgerModal(party) {
  const invs = profileScoped(state.invoices).filter(
    (i) => i.partyId === party.id || String(i.partyName || "").trim().toLowerCase() === String(party.name || "").trim().toLowerCase()
  );
  const exps = profileScoped(state.expenses).filter((e) => e.partyId === party.id);
  const net = Number(party.openingDue || 0) + Number(party.totalBilled || 0) - Number(party.totalPaid || 0);
  const due = Math.max(0, net);
  const rows = [
    ...invs.map((i) => ({ ts: toMs(i.createdAt), ref: i.invoiceNumber, desc: `Invoice (${i.status.toUpperCase()})`, amt: i.grandTotal, paid: i.paidAmount })),
    ...exps.map((e) => ({ ts: toMs(e.createdAt), ref: e.referenceNo || "EXP", desc: e.title, amt: e.amount, paid: e.status === "settled" ? e.amount : 0 }))
  ].sort((a, b) => b.ts - a.ts);

  openModal(
    `
    <h3>Party Ledger Statement • ${escapeHtml(party.name)}</h3>
    <div class="report-summary-grid">
      <div class="kpi-card"><div class="kpi-label">Opening Due</div><div class="kpi-value mono">${inr(party.openingDue)}</div></div>
      <div class="kpi-card"><div class="kpi-label">Total Billed</div><div class="kpi-value mono">${inr(party.totalBilled)}</div></div>
      <div class="kpi-card"><div class="kpi-label">Total Paid</div><div class="kpi-value mono text-emerald">${inr(party.totalPaid)}</div></div>
      <div class="kpi-card"><div class="kpi-label">Net Balance Due</div><div class="kpi-value mono ${due > 0 ? "text-loss" : "text-profit"}">${inr(due)}</div></div>
    </div>
    <div class="table-wrap" style="margin-top:14px;">
      <table>
        <thead><tr><th>Date</th><th>Voucher</th><th>Details</th><th class="right">Amount</th><th class="right">Paid</th></tr></thead>
        <tbody>
          ${
            rows.length
              ? rows.map((r) => `<tr><td class="mono">${new Date(r.ts).toLocaleDateString("en-IN")}</td><td class="mono bold">${escapeHtml(r.ref)}</td><td>${escapeHtml(r.desc)}</td><td class="right mono">${inr(r.amt)}</td><td class="right mono">${inr(r.paid)}</td></tr>`).join("")
              : `<tr><td colspan="5" class="muted">No itemized vouchers yet.</td></tr>`
          }
        </tbody>
      </table>
    </div>
    <div class="modal-actions">
      <button id="m-cancel" class="btn btn-ghost">Close</button>
      <button id="m-wa-rem" class="btn btn-primary">Send Balance Reminder on WhatsApp</button>
    </div>
    `,
    760
  );
  $("m-cancel").onclick = closeModal;
  $("m-wa-rem").onclick = () => {
    const text = `*Ledger Reminder • ${activeProfile().businessName || "Finboss Billing No.1 ERP"}*\nDear *${party.name}*,\nNet Balance Due: *${inr(due)}*\nTotal Billed: ${inr(party.totalBilled || 0)} | Paid: ${inr(party.totalPaid || 0)}\n_Shared via Finboss Billing No.1 ERP_`;
    shareToWhatsApp(text, party.phone);
  };
}

export function openQuickEntryModal() {
  openModal(`
    <h3>Add Quick Entry (Overview / Cashbook)</h3>
    <div class="form-grid">
      <div class="form-field span-2"><label>Description / Title *</label><input id="qe-title" class="input-text" placeholder="e.g. Counter Cash Sale / Freight Paid" /></div>
      <div class="form-field"><label>Category</label><select id="qe-cat" class="input-select">
        <option value="sale">Sale (Inflow)</option>
        <option value="receivable">Pending Receivable</option>
        <option value="expense">Quick Expense (Outflow)</option>
      </select></div>
      <div class="form-field"><label>Amount (₹) *</label><input id="qe-amt" type="number" min="1" step="0.01" class="input-text mono" /></div>
      <div class="form-field"><label>Party Name</label><input id="qe-party" class="input-text" placeholder="Optional" /></div>
      <div class="form-field"><label>Status</label><select id="qe-status" class="input-select">
        <option value="paid">Paid / Settled</option>
        <option value="pending">Pending</option>
      </select></div>
      <div class="form-field span-2"><label>Reference Code</label><input id="qe-ref" class="input-text mono" value="QE-${String(Date.now()).slice(-5)}" /></div>
    </div>
    <p id="m-err" class="error-box hidden"></p>
    <div class="modal-actions">
      <button id="m-cancel" class="btn btn-ghost">Cancel</button>
      <button id="qe-save" class="btn btn-primary">Save Quick Entry</button>
    </div>
  `);
  $("m-cancel").onclick = closeModal;
  $("qe-save").onclick = async () => {
    const title = $("qe-title").value.trim().slice(0, 100);
    const amount = Number($("qe-amt").value || 0);
    if (!title || amount <= 0) {
      $("m-err").textContent = "Enter a valid title and positive amount.";
      $("m-err").classList.remove("hidden");
      return;
    }
    const uid = state.user.uid;
    await setDoc(doc(db, "users", uid, "dashboard_entries", `qe_${Date.now()}`), {
      userId: uid,
      businessProfileId: state.activeProfileId || "main_1",
      title,
      partyName: $("qe-party").value.trim().slice(0, 100),
      category: $("qe-cat").value,
      amount,
      status: $("qe-status").value,
      referenceCode: $("qe-ref").value.trim().slice(0, 30),
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
    closeModal();
    showToast(`Saved quick entry ${title}!`);
  };
}

export function bindPartyTableEvents(openReceivePaymentFn) {
  document.querySelectorAll("[data-party-type]").forEach((btn) => {
    btn.onclick = () => {
      state.partyTypeFilter = btn.dataset.partyType;
      renderParties();
    };
  });

  $("parties-table").onclick = async (e) => {
    const btn = e.target.closest("[data-action]");
    if (!btn) return;
    const party = state.parties.find((x) => x.id === btn.dataset.id);
    if (!party) return;
    const action = btn.dataset.action;

    if (action === "edit-party") return openPartyModal(party);
    if (action === "ledger-party") return openPartyLedgerModal(party);
    if (action === "pay-party") return openReceivePaymentFn("party", party.id);

    if (action === "wa-party") {
      const biz = activeProfile().businessName || "Finboss Billing No.1 ERP";
      const due = Math.max(0, Number(party.openingDue || 0) + Number(party.totalBilled || 0) - Number(party.totalPaid || 0));
      const text = [
        `*Party Ledger • ${biz}*`,
        `Party: *${party.name}*`,
        party.gstin ? `GSTIN: ${party.gstin}` : "",
        `Total Billed: ${inr(party.totalBilled || 0)}`,
        `Total Paid: ${inr(party.totalPaid || 0)}`,
        `Net Balance Due: *${inr(due)}*`,
        `_Shared via Finboss Billing No.1 ERP_`
      ]
        .filter(Boolean)
        .join("\n");
      return shareToWhatsApp(text, party.phone);
    }

    if (action === "del-party") {
      openModal(`
        <h3>Delete Party ${escapeHtml(party.name)}?</h3>
        <p class="muted">This deletes the party profile from Party Ledger (existing invoices stay intact).</p>
        <div class="modal-actions">
          <button id="m-cancel" class="btn btn-ghost">Cancel</button>
          <button id="m-del" class="btn btn-danger">Delete Party</button>
        </div>
      `);
      $("m-cancel").onclick = closeModal;
      $("m-del").onclick = async () => {
        await deleteDoc(doc(db, "users", state.user.uid, "parties", party.id));
        closeModal();
        showToast(`Deleted ${party.name}`);
      };
    }
  };

  $("quick-entries-table").onclick = async (e) => {
    const btn = e.target.closest("[data-qe-action]");
    if (!btn) return;
    const entry = state.dashboardEntries.find((x) => x.id === btn.dataset.id);
    if (!entry) return;
    if (btn.dataset.qeAction === "paid") {
      await updateDoc(doc(db, "users", state.user.uid, "dashboard_entries", entry.id), {
        status: "paid",
        updatedAt: serverTimestamp()
      });
      showToast(`Marked "${entry.title}" as PAID.`);
    } else if (btn.dataset.qeAction === "del") {
      await deleteDoc(doc(db, "users", state.user.uid, "dashboard_entries", entry.id));
      showToast(`Deleted "${entry.title}".`);
    }
  };
}
