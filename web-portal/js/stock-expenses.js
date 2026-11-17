import {
  deleteDoc,
  doc,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch
} from "https://www.gstatic.com/firebasejs/11.14.0/firebase-firestore.js";
import { db, EXPENSE_CATEGORIES, state } from "./state.js";
import {
  $,
  activeProfile,
  closeModal,
  escapeHtml,
  findPartyPhone,
  fmtQty,
  inr,
  isPlainNonGst,
  loadAppSettings,
  openModal,
  profileScoped,
  shareToWhatsApp,
  showToast,
  toMs
} from "./utils.js";

export function renderStock() {
  const q = ($("stock-search")?.value || "").trim().toLowerCase();
  const allItems = profileScoped(state.inventory);
  const lowCount = allItems.filter((it) => Number(it.stockQty || 0) <= Number(it.lowStockThreshold || 5) && Number(it.stockQty || 0) > 0).length;
  const outCount = allItems.filter((it) => Number(it.stockQty || 0) <= 0).length;

  document.querySelectorAll("[data-stock-filter]").forEach((btn) => {
    const f = btn.dataset.stockFilter;
    btn.classList.toggle("active", f === state.stockFilter);
    if (f === "all") btn.textContent = `All SKUs (${allItems.length})`;
    if (f === "low") btn.textContent = `Low Stock (${lowCount})`;
    if (f === "out") btn.textContent = `Out of Stock (${outCount})`;
  });

  const items = allItems.filter((it) => {
    const qty = Number(it.stockQty || 0);
    const thr = Number(it.lowStockThreshold || 5);
    if (state.stockFilter === "low" && !(qty > 0 && qty <= thr)) return false;
    if (state.stockFilter === "out" && qty > 0) return false;
    if (!q) return true;
    return [it.name, it.sku, it.hsnCode, it.category].some((v) => String(v || "").toLowerCase().includes(q));
  });

  const totalUnitsVal = allItems.reduce((s, it) => s + Number(it.stockQty || 0) * Number(it.purchasePrice || 0), 0);
  const potentialSaleVal = allItems.reduce((s, it) => s + Number(it.stockQty || 0) * Number(it.salePrice || 0), 0);
  if ($("stock-kpi-strip")) {
    $("stock-kpi-strip").innerHTML = `
      <div class="kpi-card"><div class="kpi-label">Total SKUs</div><div class="kpi-value mono">${allItems.length}</div><div class="kpi-sub">${lowCount} low • ${outCount} out</div></div>
      <div class="kpi-card"><div class="kpi-label">Stock Value (Cost)</div><div class="kpi-value mono">${inr(totalUnitsVal)}</div></div>
      <div class="kpi-card"><div class="kpi-label">Potential Sale Value</div><div class="kpi-value mono text-emerald">${inr(potentialSaleVal)}</div></div>
    `;
  }

  $("stock-items-table").innerHTML = items.length
    ? items
        .map((it) => {
          const qty = Number(it.stockQty || 0);
          const thr = Number(it.lowStockThreshold || 5);
          const stockBadge =
            qty <= 0
              ? `<span class="pill pill-unpaid">OUT</span>`
              : qty <= thr
                ? `<span class="pill pill-partial">LOW</span>`
                : `<span class="pill pill-paid">IN STOCK</span>`;

          return `
      <tr>
        <td>
          <div class="bold">${escapeHtml(it.name)}</div>
          <div class="muted">${escapeHtml(it.category || "General")}</div>
        </td>
        <td class="mono">${escapeHtml(it.sku)}<br/><span class="muted">HSN ${escapeHtml(it.hsnCode || "—")}</span></td>
        <td>${Number(it.gstRatePercent || 0)}%</td>
        <td class="right mono">${inr(it.salePrice)}<br/><span class="muted">Cost ${inr(it.purchasePrice || 0)}</span></td>
        <td class="right mono bold">${fmtQty(qty)} ${escapeHtml(it.unit)}<br/>${stockBadge}</td>
        <td class="right">
          <div class="row-actions">
            <button class="btn btn-sm btn-primary" data-action="purchase-sku" data-id="${escapeHtml(it.id)}">+ Purchase</button>
            <button class="btn btn-sm btn-ghost" data-action="adj-plus" data-id="${escapeHtml(it.id)}">+1</button>
            <button class="btn btn-sm btn-ghost" data-action="adj-minus" data-id="${escapeHtml(it.id)}">-1</button>
            <button class="btn btn-sm btn-ghost" data-action="edit-sku" data-id="${escapeHtml(it.id)}">Edit</button>
            <button class="btn btn-sm btn-ghost" data-action="wa-stock" data-id="${escapeHtml(it.id)}">WhatsApp</button>
            <button class="btn btn-sm btn-danger-soft" data-action="del-sku" data-id="${escapeHtml(it.id)}">Del</button>
          </div>
        </td>
      </tr>`;
        })
        .join("")
    : `<tr><td colspan="6" class="muted">No matching stock SKUs found.</td></tr>`;
}

export function openItemModal(existingItem = null) {
  const settings = loadAppSettings();
  const plain = isPlainNonGst();
  const isEditing = Boolean(existingItem);
  openModal(`
    <h3>${isEditing ? `Edit SKU • ${escapeHtml(existingItem.name)}` : "Add Stock SKU"}</h3>
    <div class="form-grid">
      <div class="form-field"><label>Product Name *</label><input id="m-sku-name" class="input-text" value="${escapeHtml(existingItem?.name || "")}" /></div>
      <div class="form-field"><label>SKU Code *</label><input id="m-sku-code" class="input-text mono" value="${escapeHtml(existingItem?.sku || `SKU-${String(Date.now()).slice(-4)}`)}" /></div>
      <div class="form-field"><label>Category</label><input id="m-sku-cat" class="input-text" value="${escapeHtml(existingItem?.category || "General")}" /></div>
      <div class="form-field"><label>Unit</label><input id="m-sku-unit" class="input-text" value="${escapeHtml(existingItem?.unit || "PCS")}" placeholder="PCS / KG / BOX" /></div>
      <div class="form-field"><label>HSN Code</label><input id="m-sku-hsn" class="input-text mono" ${plain ? "disabled" : ""} value="${escapeHtml(plain ? "" : existingItem?.hsnCode || "8471")}" /></div>
      <div class="form-field"><label>GST Rate %</label><select id="m-sku-gst" class="input-select" ${plain ? "disabled" : ""}>
        ${[0, 5, 12, 18, 28].map((r) => `<option value="${r}" ${Number(existingItem?.gstRatePercent ?? settings.defaultGstSlabPercent ?? 18) === r ? "selected" : ""}>${r}%</option>`).join("")}
      </select></div>
      <div class="form-field"><label>Sale Price (₹)</label><input id="m-sku-sale" type="number" step="0.01" min="0" class="input-text mono" value="${Number(existingItem?.salePrice ?? 500)}" /></div>
      <div class="form-field"><label>Purchase Cost (₹)</label><input id="m-sku-buy" type="number" step="0.01" min="0" class="input-text mono" value="${Number(existingItem?.purchasePrice ?? 350)}" /></div>
      <div class="form-field"><label>Stock Quantity</label><input id="m-sku-qty" type="number" step="0.01" min="0" class="input-text mono" value="${Number(existingItem?.stockQty ?? 10)}" /></div>
      <div class="form-field"><label>Low-Stock Alert Threshold</label><input id="m-sku-thr" type="number" min="0" class="input-text mono" value="${Number(existingItem?.lowStockThreshold ?? settings.defaultLowStockThreshold ?? 5)}" /></div>
    </div>
    <p id="m-err" class="error-box hidden"></p>
    <div class="modal-actions">
      <button id="m-cancel" class="btn btn-ghost">Cancel</button>
      <button id="m-save" class="btn btn-primary">${isEditing ? "Update SKU" : "Save SKU"}</button>
    </div>
  `);
  $("m-cancel").onclick = closeModal;
  $("m-save").onclick = async () => {
    const name = $("m-sku-name").value.trim().slice(0, 100);
    const sku = ($("m-sku-code").value.trim() || `SKU-${String(Date.now()).slice(-4)}`).toUpperCase().slice(0, 40);
    if (!name) {
      $("m-err").textContent = "Product name is required.";
      $("m-err").classList.remove("hidden");
      return;
    }
    const uid = state.user.uid;
    const payload = {
      name,
      sku,
      hsnCode: plain ? "" : $("m-sku-hsn").value.trim().toUpperCase().slice(0, 16),
      category: ($("m-sku-cat").value.trim() || "General").slice(0, 40),
      unit: ($("m-sku-unit").value.trim() || "PCS").toUpperCase().slice(0, 15),
      salePrice: Math.max(0, Number($("m-sku-sale").value || 0)),
      purchasePrice: Math.max(0, Number($("m-sku-buy").value || 0)),
      gstRatePercent: plain ? 0 : Math.min(28, Math.max(0, Number($("m-sku-gst").value || 0))),
      stockQty: Math.max(0, Number($("m-sku-qty").value || 0)),
      lowStockThreshold: Math.max(0, Number($("m-sku-thr").value || 5)),
      updatedAt: serverTimestamp()
    };
    if (isEditing) {
      await updateDoc(doc(db, "users", uid, "inventory_items", existingItem.id), payload);
    } else {
      await setDoc(doc(db, "users", uid, "inventory_items", `sku_${Date.now()}`), {
        userId: uid,
        businessProfileId: state.activeProfileId || "main_1",
        ...payload,
        createdAt: serverTimestamp()
      });
    }
    closeModal();
    showToast(`${isEditing ? "Updated" : "Added"} ${name}!`);
  };
}

export function openPurchaseModal(existingItem = null) {
  const settings = loadAppSettings();
  const plain = isPlainNonGst();
  const suppliers = profileScoped(state.parties).filter((p) => p.partyType === "supplier");
  const stockItems = profileScoped(state.inventory);

  openModal(
    `
    <h3>Purchase / Stock-In ${existingItem ? `• ${escapeHtml(existingItem.name)}` : ""}</h3>
    <div class="form-grid">
      <div class="form-field span-2">
        <label>Existing Stock SKU (or leave blank for new item)</label>
        <select id="pur-sku-sel" class="input-select">
          <option value="">— Create New Stock Item —</option>
          ${stockItems.map((it) => `<option value="${escapeHtml(it.id)}" ${existingItem?.id === it.id ? "selected" : ""}>${escapeHtml(it.name)} (Current: ${fmtQty(it.stockQty)} ${escapeHtml(it.unit)})</option>`).join("")}
        </select>
      </div>
      <div class="form-field"><label>Item Name *</label><input id="pur-name" class="input-text" value="${escapeHtml(existingItem?.name || "")}" /></div>
      <div class="form-field"><label>SKU Code</label><input id="pur-sku" class="input-text mono" value="${escapeHtml(existingItem?.sku || `SKU-${String(Date.now()).slice(-4)}`)}" /></div>
      <div class="form-field"><label>Purchase Qty *</label><input id="pur-qty" type="number" min="0.01" step="0.01" class="input-text mono" value="10" /></div>
      <div class="form-field"><label>Unit Cost (₹) *</label><input id="pur-cost" type="number" min="0.01" step="0.01" class="input-text mono" value="${Number(existingItem?.purchasePrice || 100)}" /></div>
      <div class="form-field"><label>Sale Price (₹)</label><input id="pur-sale" type="number" min="0" step="0.01" class="input-text mono" value="${Number(existingItem?.salePrice || 150)}" /></div>
      <div class="form-field"><label>GST % (Input ITC)</label><select id="pur-gst" class="input-select" ${plain ? "disabled" : ""}>
        ${[0, 5, 12, 18, 28].map((r) => `<option value="${r}" ${Number(existingItem?.gstRatePercent ?? 18) === r ? "selected" : ""}>${r}%</option>`).join("")}
      </select></div>
      <div class="form-field"><label>HSN Code</label><input id="pur-hsn" class="input-text mono" ${plain ? "disabled" : ""} value="${escapeHtml(plain ? "" : existingItem?.hsnCode || "")}" /></div>
      <div class="form-field"><label>Supplier Party</label><select id="pur-sup" class="input-select">
        <option value="">Other / Walk-in Vendor</option>
        ${suppliers.map((s) => `<option value="${escapeHtml(s.id)}">${escapeHtml(s.name)}</option>`).join("")}
      </select></div>
      <div class="form-field"><label>Vendor Name (if not saved)</label><input id="pur-sup-name" class="input-text" placeholder="Optional supplier name" /></div>
      <div class="form-field"><label>Supplier Bill #</label><input id="pur-bill" class="input-text mono" value="PUR-${String(Date.now()).slice(-5)}" /></div>
      <div class="form-field"><label>Payment Mode</label><select id="pur-mode" class="input-select">
        <option value="upi">UPI</option><option value="cash">Cash</option><option value="bank">Bank Transfer</option><option value="card">Card</option>
      </select></div>
      <div class="form-field"><label>Payment Status</label><select id="pur-paid" class="input-select">
        <option value="settled">Paid Now</option><option value="pending">Payable Later (Credit)</option>
      </select></div>
      <label class="check-row span-2"><input id="pur-log-exp" type="checkbox" ${settings.autoRecordPurchaseInExpenses ? "checked" : ""} /> Auto-log Purchase in Expenses &amp; Claim GST Input Tax Credit (ITC)</label>
    </div>
    <p id="m-err" class="error-box hidden"></p>
    <div class="modal-actions">
      <button id="m-cancel" class="btn btn-ghost">Cancel</button>
      <button id="pur-save" class="btn btn-primary">Confirm Stock-In</button>
    </div>
    `,
    720
  );

  $("m-cancel").onclick = closeModal;
  $("pur-sku-sel").onchange = (e) => {
    const it = stockItems.find((x) => x.id === e.target.value);
    if (it) {
      $("pur-name").value = it.name || "";
      $("pur-sku").value = it.sku || "";
      $("pur-cost").value = Number(it.purchasePrice || 0);
      $("pur-sale").value = Number(it.salePrice || 0);
      if (!plain) {
        $("pur-gst").value = Number(it.gstRatePercent || 18);
        $("pur-hsn").value = it.hsnCode || "";
      }
    }
  };

  $("pur-save").onclick = async () => {
    const selectedId = $("pur-sku-sel").value;
    const matchedItem = stockItems.find((x) => x.id === selectedId) || null;
    const targetName = (matchedItem?.name || $("pur-name").value).trim().slice(0, 100);
    const qty = Number($("pur-qty").value || 0);
    const cost = Number($("pur-cost").value || 0);
    if (!targetName || qty <= 0 || cost <= 0) {
      $("m-err").textContent = "Item name, purchase quantity (>0), and unit cost (>0) are required.";
      $("m-err").classList.remove("hidden");
      return;
    }
    const uid = state.user.uid;
    const batch = writeBatch(db);
    const gstRate = plain ? 0 : Number($("pur-gst").value || 0);
    const hsnCode = plain ? "" : $("pur-hsn").value.trim().toUpperCase().slice(0, 16);

    if (matchedItem) {
      batch.update(doc(db, "users", uid, "inventory_items", matchedItem.id), {
        stockQty: Number(matchedItem.stockQty || 0) + qty,
        purchasePrice: cost,
        hsnCode: hsnCode || matchedItem.hsnCode || "",
        updatedAt: serverTimestamp()
      });
    } else {
      batch.set(doc(db, "users", uid, "inventory_items", `sku_${Date.now()}`), {
        userId: uid,
        businessProfileId: state.activeProfileId || "main_1",
        name: targetName,
        sku: ($("pur-sku").value.trim() || `SKU-${String(Date.now()).slice(-4)}`).toUpperCase().slice(0, 40),
        hsnCode,
        category: "General",
        unit: "PCS",
        salePrice: Math.max(cost, Number($("pur-sale").value || cost)),
        purchasePrice: cost,
        gstRatePercent: gstRate,
        stockQty: qty,
        lowStockThreshold: Number(settings.defaultLowStockThreshold || 5),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      });
    }

    const taxable = qty * cost;
    const gstItc = plain ? 0 : taxable * (gstRate / 100);
    const grandTotal = Math.max(0.01, taxable + gstItc);
    const supId = $("pur-sup").value;
    const supObj = suppliers.find((s) => s.id === supId) || null;
    const supName = (supObj?.name || $("pur-sup-name").value).trim().slice(0, 100);
    const isPaid = $("pur-paid").value === "settled";

    if ($("pur-log-exp").checked) {
      batch.set(doc(db, "users", uid, "expenses", `exp_${Date.now()}`), {
        userId: uid,
        businessProfileId: state.activeProfileId || "main_1",
        title: `Purchase: ${targetName} (${fmtQty(qty)})`.slice(0, 100),
        category: "purchases",
        paymentMethod: $("pur-mode").value,
        status: isPaid ? "settled" : "pending",
        partyId: supObj?.id || "",
        partyName: supName,
        referenceNo: $("pur-bill").value.trim().toUpperCase().slice(0, 40),
        amount: Math.round(grandTotal * 100) / 100,
        gstInputAmount: Math.round(gstItc * 100) / 100,
        notes: `${hsnCode ? `HSN ${hsnCode} • ` : ""}Rate ${cost} × ${qty}`.slice(0, 240),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      });
    }

    if (supObj) {
      batch.update(doc(db, "users", uid, "parties", supObj.id), {
        totalBilled: Number(supObj.totalBilled || 0) + grandTotal,
        totalPaid: Number(supObj.totalPaid || 0) + (isPaid ? grandTotal : 0),
        updatedAt: serverTimestamp()
      });
    }

    await batch.commit();
    closeModal();
    showToast(`Stock-In recorded for ${targetName} (+${fmtQty(qty)})!`);
  };
}

export function bindStockTableEvents() {
  document.querySelectorAll("[data-stock-filter]").forEach((btn) => {
    btn.onclick = () => {
      state.stockFilter = btn.dataset.stockFilter;
      renderStock();
    };
  });

  $("stock-items-table").onclick = async (e) => {
    const btn = e.target.closest("[data-action]");
    if (!btn) return;
    const item = state.inventory.find((i) => i.id === btn.dataset.id);
    if (!item) return;
    const action = btn.dataset.action;

    if (action === "edit-sku") return openItemModal(item);
    if (action === "purchase-sku") return openPurchaseModal(item);

    if (action === "adj-plus" || action === "adj-minus") {
      const delta = action === "adj-plus" ? 1 : -1;
      const nextQty = Math.max(0, Number(item.stockQty || 0) + delta);
      await updateDoc(doc(db, "users", state.user.uid, "inventory_items", item.id), {
        stockQty: nextQty,
        updatedAt: serverTimestamp()
      });
      return showToast(`${item.name}: ${fmtQty(nextQty)} ${item.unit}`);
    }

    if (action === "wa-stock") {
      const biz = activeProfile().businessName || "Finboss Billing No.1 ERP";
      const text = [
        `*Product & Stock • ${biz}*`,
        `Item: *${item.name}* (${item.sku})`,
        item.hsnCode ? `HSN: ${item.hsnCode} • GST: ${Number(item.gstRatePercent || 0)}%` : "",
        `Sale Rate: *${inr(item.salePrice)}* / ${item.unit}`,
        `Available Stock: ${fmtQty(item.stockQty)} ${item.unit}`,
        `_Shared via Finboss Billing No.1 ERP_`
      ]
        .filter(Boolean)
        .join("\n");
      return shareToWhatsApp(text);
    }

    if (action === "del-sku") {
      openModal(`
        <h3>Delete SKU ${escapeHtml(item.name)}?</h3>
        <p class="muted">Removes this item from Stock Catalog.</p>
        <div class="modal-actions">
          <button id="m-cancel" class="btn btn-ghost">Cancel</button>
          <button id="m-del" class="btn btn-danger">Delete SKU</button>
        </div>
      `);
      $("m-cancel").onclick = closeModal;
      $("m-del").onclick = async () => {
        await deleteDoc(doc(db, "users", state.user.uid, "inventory_items", item.id));
        closeModal();
        showToast(`Deleted ${item.name}`);
      };
    }
  };
}

// --- EXPENSES & CASHBOOK ---

export function renderExpenses() {
  const expenses = profileScoped(state.expenses);
  const invoices = profileScoped(state.invoices);
  const entries = profileScoped(state.dashboardEntries);
  const q = ($("expense-search")?.value || "").trim().toLowerCase();

  document.querySelectorAll("[data-exp-mode]").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.expMode === state.expenseViewMode);
  });

  // Cashbook balances
  let cashNet = 0;
  let digitalNet = 0;
  const feed = [];

  invoices
    .filter((i) => Number(i.paidAmount || 0) > 0)
    .forEach((inv) => {
      const isCash = inv.paymentMethod === "cash";
      if (isCash) cashNet += Number(inv.paidAmount);
      else digitalNet += Number(inv.paidAmount);
      feed.push({
        ts: toMs(inv.createdAt),
        title: `Bill ${inv.invoiceNumber}`,
        subtitle: inv.partyName,
        mode: String(inv.paymentMethod || "upi").toUpperCase(),
        amount: Number(inv.paidAmount),
        isInflow: true
      });
    });

  entries
    .filter((en) => en.status === "paid" || en.category === "expense")
    .forEach((en) => {
      const isOut = en.category === "expense";
      if (isOut) cashNet -= Number(en.amount || 0);
      else cashNet += Number(en.amount || 0);
      feed.push({
        ts: toMs(en.createdAt),
        title: en.title,
        subtitle: en.partyName || "Quick Entry",
        mode: "CASHBOOK",
        amount: Number(en.amount || 0),
        isInflow: !isOut
      });
    });

  expenses
    .filter((e) => e.status === "settled")
    .forEach((e) => {
      const isCash = e.paymentMethod === "cash";
      if (isCash) cashNet -= Number(e.amount || 0);
      else digitalNet -= Number(e.amount || 0);
      feed.push({
        ts: toMs(e.createdAt),
        title: e.title,
        subtitle: e.partyName || e.category,
        mode: String(e.paymentMethod || "upi").toUpperCase(),
        amount: Number(e.amount || 0),
        isInflow: false
      });
    });

  feed.sort((a, b) => b.ts - a.ts);

  const totalExp = expenses.reduce((s, e) => s + Number(e.amount || 0), 0);
  const totalItc = expenses.reduce((s, e) => s + Number(e.gstInputAmount || 0), 0);
  const pendingExp = expenses.filter((e) => e.status === "pending").reduce((s, e) => s + Number(e.amount || 0), 0);

  if ($("expenses-kpi-strip")) {
    $("expenses-kpi-strip").innerHTML =
      state.expenseViewMode === "cashbook"
        ? `
          <div class="kpi-card"><div class="kpi-label">Cash-in-Hand</div><div class="kpi-value mono ${cashNet >= 0 ? "text-profit" : "text-loss"}">${inr(cashNet)}</div></div>
          <div class="kpi-card"><div class="kpi-label">Bank / UPI / Card Net</div><div class="kpi-value mono ${digitalNet >= 0 ? "text-profit" : "text-loss"}">${inr(digitalNet)}</div></div>
          <div class="kpi-card"><div class="kpi-label">Total Net Liquidity</div><div class="kpi-value mono">${inr(cashNet + digitalNet)}</div></div>
        `
        : `
          <div class="kpi-card"><div class="kpi-label">Total Operating Expenses</div><div class="kpi-value mono">${inr(totalExp)}</div></div>
          <div class="kpi-card"><div class="kpi-label">Pending Payables</div><div class="kpi-value mono text-amber">${inr(pendingExp)}</div></div>
          <div class="kpi-card"><div class="kpi-label">GST Input Tax Credit</div><div class="kpi-value mono text-emerald">${inr(totalItc)}</div></div>
        `;
  }

  $("expenses-list-wrap").classList.toggle("hidden", state.expenseViewMode !== "expenses");
  $("cashbook-feed-wrap").classList.toggle("hidden", state.expenseViewMode !== "cashbook");

  document.querySelectorAll("[data-exp-cat]").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.expCat === state.expenseCatFilter);
  });

  const filteredExp = expenses.filter((e) => {
    if (state.expenseCatFilter !== "all" && e.category !== state.expenseCatFilter) return false;
    if (!q) return true;
    return [e.title, e.partyName, e.referenceNo, e.category].some((v) => String(v || "").toLowerCase().includes(q));
  });

  $("expenses-table").innerHTML = filteredExp.length
    ? filteredExp
        .map(
          (e) => `
      <tr>
        <td>
          <div class="bold">${escapeHtml(e.title)}</div>
          ${e.gstInputAmount > 0 ? `<div class="muted">GST ITC: ${inr(e.gstInputAmount)}</div>` : ""}
        </td>
        <td>${escapeHtml((e.category || "other").toUpperCase())}</td>
        <td>${escapeHtml(e.partyName || e.referenceNo || "—")}</td>
        <td>${escapeHtml((e.paymentMethod || "upi").toUpperCase())}</td>
        <td><span class="pill pill-${e.status === "settled" ? "paid" : "partial"}">${escapeHtml(e.status === "pending" ? "DUE" : "PAID")}</span></td>
        <td class="right mono bold">${inr(e.amount)}</td>
        <td class="right">
          <div class="row-actions">
            ${e.status === "pending" ? `<button class="btn btn-sm btn-primary" data-action="settle-exp" data-id="${escapeHtml(e.id)}">Mark Paid</button>` : ""}
            ${e.status === "pending" ? `<button class="btn btn-sm btn-ghost" data-action="edit-exp" data-id="${escapeHtml(e.id)}">Edit</button>` : ""}
            <button class="btn btn-sm btn-ghost" data-action="wa-exp" data-id="${escapeHtml(e.id)}">WhatsApp</button>
            <button class="btn btn-sm btn-danger-soft" data-action="del-exp" data-id="${escapeHtml(e.id)}">Del</button>
          </div>
        </td>
      </tr>`
        )
        .join("")
    : `<tr><td colspan="7" class="muted">No matching expenses found.</td></tr>`;

  $("cashbook-table").innerHTML = feed.length
    ? feed
        .map(
          (row) => `
      <tr>
        <td class="mono">${new Date(row.ts).toLocaleDateString("en-IN")}</td>
        <td class="bold">${escapeHtml(row.title)}</td>
        <td>${escapeHtml(row.subtitle)}</td>
        <td><span class="pill pill-info">${escapeHtml(row.mode)}</span></td>
        <td class="right mono bold ${row.isInflow ? "text-profit" : "text-loss"}">${row.isInflow ? "+" : "-"} ${inr(row.amount)}</td>
      </tr>`
        )
        .join("")
    : `<tr><td colspan="5" class="muted">Cashbook has no settled inflows or outflows yet.</td></tr>`;
}

export function openExpenseModal(existingExp = null, prefill = null) {
  const plain = isPlainNonGst();
  const isEditing = Boolean(existingExp && existingExp.status === "pending");
  const suppliers = profileScoped(state.parties).filter((p) => p.partyType === "supplier");
  const src = existingExp || prefill || {};

  openModal(`
    <h3>${isEditing ? "Edit Pending Expense" : "Record Business Expense"}</h3>
    <div class="form-grid">
      <div class="form-field span-2"><label>Expense Title *</label><input id="m-e-title" class="input-text" value="${escapeHtml(src.title || src.expenseTitle || "")}" placeholder="Office Rent / Staff Salary / Purchase Bill" /></div>
      <div class="form-field"><label>Category</label><select id="m-e-cat" class="input-select">
        ${EXPENSE_CATEGORIES.map((c) => `<option value="${c.key}" ${(src.category || src.expenseCategory || "other") === c.key ? "selected" : ""}>${c.label}</option>`).join("")}
      </select></div>
      <div class="form-field"><label>Payment Mode</label><select id="m-e-mode" class="input-select">
        ${["upi", "cash", "bank", "card"].map((m) => `<option value="${m}" ${(src.paymentMethod || "upi") === m ? "selected" : ""}>${m.toUpperCase()}</option>`).join("")}
      </select></div>
      <div class="form-field"><label>Amount (₹) *</label><input id="m-e-amt" type="number" step="0.01" min="1" class="input-text mono" value="${Number(src.amount || src.expenseAmount || "") || ""}" /></div>
      <div class="form-field"><label>GST Input Credit (₹)</label><input id="m-e-gst" type="number" step="0.01" min="0" class="input-text mono" ${plain ? "disabled" : ""} value="${plain ? 0 : Number(src.gstInputAmount || 0)}" /></div>
      <div class="form-field"><label>Status</label><select id="m-e-status" class="input-select">
        <option value="settled" ${(src.status || "settled") === "settled" ? "selected" : ""}>Paid Now (Settled)</option>
        <option value="pending" ${src.status === "pending" ? "selected" : ""}>Payable Later (Due)</option>
      </select></div>
      <div class="form-field"><label>Supplier (Optional)</label><select id="m-e-party" class="input-select">
        <option value="">Other Vendor</option>
        ${suppliers.map((s) => `<option value="${escapeHtml(s.id)}" ${src.partyId === s.id ? "selected" : ""}>${escapeHtml(s.name)}</option>`).join("")}
      </select></div>
      <div class="form-field"><label>Vendor / Payee Name</label><input id="m-e-payee" class="input-text" value="${escapeHtml(src.partyName || "")}" /></div>
      <div class="form-field"><label>Ref / Bill #</label><input id="m-e-ref" class="input-text mono" value="${escapeHtml(src.referenceNo || `WEB-${String(Date.now()).slice(-6)}`)}" /></div>
      <div class="form-field span-2"><label>Notes</label><input id="m-e-notes" class="input-text" value="${escapeHtml(src.notes || "")}" /></div>
    </div>
    <p id="m-err" class="error-box hidden"></p>
    <div class="modal-actions">
      <button id="m-cancel" class="btn btn-ghost">Cancel</button>
      <button id="m-save" class="btn btn-primary">${isEditing ? "Update Expense" : "Save Expense"}</button>
    </div>
  `);
  $("m-cancel").onclick = closeModal;
  $("m-save").onclick = async () => {
    const title = $("m-e-title").value.trim().slice(0, 100);
    const amount = Number($("m-e-amt").value || 0);
    const gstInput = plain ? 0 : Math.min(amount, Math.max(0, Number($("m-e-gst").value || 0)));
    if (!title || amount <= 0) {
      $("m-err").textContent = "Title and positive amount are required.";
      $("m-err").classList.remove("hidden");
      return;
    }
    const supId = $("m-e-party").value;
    const sup = suppliers.find((s) => s.id === supId);
    const payload = {
      title,
      category: $("m-e-cat").value,
      paymentMethod: $("m-e-mode").value,
      status: $("m-e-status").value,
      partyId: sup?.id || "",
      partyName: (sup?.name || $("m-e-payee").value).trim().slice(0, 100),
      referenceNo: $("m-e-ref").value.trim().toUpperCase().slice(0, 40),
      amount,
      gstInputAmount: gstInput,
      notes: $("m-e-notes").value.trim().slice(0, 240),
      updatedAt: serverTimestamp()
    };
    const uid = state.user.uid;
    if (isEditing) {
      await updateDoc(doc(db, "users", uid, "expenses", existingExp.id), payload);
    } else {
      await setDoc(doc(db, "users", uid, "expenses", `exp_${Date.now()}`), {
        userId: uid,
        businessProfileId: state.activeProfileId || "main_1",
        ...payload,
        createdAt: serverTimestamp()
      });
    }
    closeModal();
    showToast(`${isEditing ? "Updated" : "Recorded"} expense ${title}!`);
  };
}

export function bindExpenseEvents() {
  document.querySelectorAll("[data-exp-mode]").forEach((btn) => {
    btn.onclick = () => {
      state.expenseViewMode = btn.dataset.expMode;
      renderExpenses();
    };
  });
  document.querySelectorAll("[data-exp-cat]").forEach((btn) => {
    btn.onclick = () => {
      state.expenseCatFilter = btn.dataset.expCat;
      renderExpenses();
    };
  });

  $("expenses-table").onclick = async (e) => {
    const btn = e.target.closest("[data-action]");
    if (!btn) return;
    const exp = state.expenses.find((x) => x.id === btn.dataset.id);
    if (!exp) return;
    const action = btn.dataset.action;

    if (action === "edit-exp") return openExpenseModal(exp);
    if (action === "settle-exp") {
      await updateDoc(doc(db, "users", state.user.uid, "expenses", exp.id), {
        status: "settled",
        paymentMethod: exp.paymentMethod || "upi",
        updatedAt: serverTimestamp()
      });
      return showToast(`Marked "${exp.title}" as PAID.`);
    }
    if (action === "wa-exp") {
      const biz = activeProfile().businessName || "Finboss Billing No.1 ERP";
      const text = [
        `*Expense Voucher • ${biz}*`,
        `Title: *${exp.title}*`,
        `Category: ${(exp.category || "").toUpperCase()} • Mode: ${(exp.paymentMethod || "").toUpperCase()}`,
        `Amount: *${inr(exp.amount)}* (${(exp.status || "").toUpperCase()})`,
        exp.gstInputAmount > 0 ? `GST ITC: ${inr(exp.gstInputAmount)}` : "",
        `_Shared via Finboss Billing No.1 ERP_`
      ]
        .filter(Boolean)
        .join("\n");
      return shareToWhatsApp(text);
    }
    if (action === "del-exp") {
      await deleteDoc(doc(db, "users", state.user.uid, "expenses", exp.id));
      return showToast(`Deleted expense "${exp.title}".`);
    }
  };
}

export function openReceivePaymentModal(defaultMode = null, preselectedId = "") {
  const pendingInvoices = profileScoped(state.invoices).filter(
    (inv) => inv.status !== "paid" && Number(inv.grandTotal || 0) - Number(inv.paidAmount || 0) > 0
  );
  const allParties = profileScoped(state.parties);
  let mode = defaultMode || (pendingInvoices.length > 0 ? "invoice" : "party");

  const renderForm = () => {
    const invOptions = pendingInvoices
      .map((inv) => {
        const due = Math.max(0, Number(inv.grandTotal || 0) - Number(inv.paidAmount || 0));
        return `<option value="${escapeHtml(inv.id)}" data-due="${due}" ${inv.id === preselectedId ? "selected" : ""}>${escapeHtml(inv.invoiceNumber)} • ${escapeHtml(inv.partyName)} (Due ${inr(due)})</option>`;
      })
      .join("");
    const partyOptions = allParties
      .map((p) => {
        const due = Math.max(0, Number(p.openingDue || 0) + Number(p.totalBilled || 0) - Number(p.totalPaid || 0));
        return `<option value="${escapeHtml(p.id)}" data-due="${due}" ${p.id === preselectedId ? "selected" : ""}>${escapeHtml(p.name)} [${(p.partyType || "customer").toUpperCase()}] (Bal ${inr(due)})</option>`;
      })
      .join("");

    const pickedParty = allParties.find((p) => p.id === preselectedId) || allParties[0];
    const pickedInv = pendingInvoices.find((i) => i.id === preselectedId) || pendingInvoices[0];
    const defaultDue =
      mode === "invoice"
        ? pickedInv
          ? Math.max(0, Number(pickedInv.grandTotal || 0) - Number(pickedInv.paidAmount || 0))
          : 0
        : pickedParty
          ? Math.max(0, Number(pickedParty.openingDue || 0) + Number(pickedParty.totalBilled || 0) - Number(pickedParty.totalPaid || 0))
          : 0;

    openModal(`
      <h3>Receive / Settle Payment</h3>
      <div style="display:flex;gap:8px;margin-top:8px;">
        <button id="rp-mode-inv" class="btn ${mode === "invoice" ? "btn-primary" : "btn-ghost"}">Pending Bills (${pendingInvoices.length})</button>
        <button id="rp-mode-pty" class="btn ${mode === "party" ? "btn-primary" : "btn-ghost"}">Party Ledger (${allParties.length})</button>
      </div>
      <div class="form-grid">
        ${
          mode === "invoice"
            ? `<div class="form-field span-2"><label>Select Pending Invoice</label><select id="rp-target" class="input-select">${invOptions || `<option value="">No pending invoices</option>`}</select></div>`
            : `<div class="form-field span-2"><label>Select Customer / Supplier</label><select id="rp-target" class="input-select">${partyOptions || `<option value="">No parties found</option>`}</select></div>`
        }
        <div class="form-field"><label>Amount (₹)</label><input id="rp-amount" type="number" class="input-text mono" value="${defaultDue > 0 ? defaultDue : ""}" min="1" step="0.01" /></div>
        <div class="form-field"><label>Payment Mode</label><select id="rp-method" class="input-select">
          <option value="upi">UPI</option><option value="cash">Cash</option><option value="card">Card</option>
        </select></div>
      </div>
      <p id="m-err" class="error-box hidden"></p>
      <div class="modal-actions">
        <button id="m-cancel" class="btn btn-ghost">Cancel</button>
        <button id="rp-save" class="btn btn-primary">Save Payment</button>
      </div>
    `);

    $("m-cancel").onclick = closeModal;
    $("rp-mode-inv").onclick = () => { mode = "invoice"; renderForm(); };
    $("rp-mode-pty").onclick = () => { mode = "party"; renderForm(); };
    $("rp-target").onchange = (e) => {
      const opt = e.target.selectedOptions[0];
      if (opt && opt.dataset.due) $("rp-amount").value = opt.dataset.due;
    };
    $("rp-save").onclick = async () => {
      const targetId = $("rp-target").value;
      const amt = Number($("rp-amount").value || 0);
      const method = $("rp-method").value;
      if (!targetId || amt <= 0) {
        $("m-err").textContent = "Please select a record and enter a positive amount.";
        $("m-err").classList.remove("hidden");
        return;
      }

      const bizName = activeProfile().businessName || "Finboss Billing No.1 ERP";
      const uid = state.user.uid;
      let payerName = "";
      let payerPhone = "";
      let refLabel = "";
      let remaining = 0;

      if (mode === "invoice") {
        const inv = pendingInvoices.find((i) => i.id === targetId);
        if (!inv) return;
        const grand = Number(inv.grandTotal || 0);
        const alreadyPaid = Number(inv.paidAmount || 0);
        const applied = Math.min(amt, Math.max(0, grand - alreadyPaid));
        const newPaid = Math.min(grand, alreadyPaid + applied);
        const newStatus = newPaid + 0.01 >= grand ? "paid" : "partial";
        const batch = writeBatch(db);
        batch.update(doc(db, "users", uid, "invoices", inv.id), {
          paidAmount: newPaid,
          status: newStatus,
          paymentMethod: method,
          updatedAt: serverTimestamp()
        });
        if (inv.partyId) {
          const pty = state.parties.find((p) => p.id === inv.partyId);
          if (pty) {
            batch.update(doc(db, "users", uid, "parties", pty.id), {
              totalBilled: Number(pty.totalBilled || 0),
              totalPaid: Number(pty.totalPaid || 0) + applied,
              updatedAt: serverTimestamp()
            });
          }
        }
        await batch.commit();
        payerName = inv.partyName;
        payerPhone = findPartyPhone(inv.partyId, inv.partyName);
        refLabel = `Invoice ${inv.invoiceNumber}`;
        remaining = Math.max(0, grand - newPaid);
      } else {
        const pty = allParties.find((p) => p.id === targetId);
        if (!pty) return;
        await updateDoc(doc(db, "users", uid, "parties", pty.id), {
          totalBilled: Number(pty.totalBilled || 0),
          totalPaid: Number(pty.totalPaid || 0) + amt,
          updatedAt: serverTimestamp()
        });
        payerName = pty.name;
        payerPhone = pty.phone || "";
        refLabel = pty.partyType === "supplier" ? "Supplier Payment" : "Customer Ledger Receipt";
        const currentDue = Math.max(0, Number(pty.openingDue || 0) + Number(pty.totalBilled || 0) - Number(pty.totalPaid || 0));
        remaining = Math.max(0, currentDue - amt);
      }

      const receiptText = [
        `*Payment Receipt • ${bizName}*`,
        `Party: *${payerName}*`,
        `Reference: ${refLabel}`,
        `Amount: *${inr(amt)}* (${method.toUpperCase()})`,
        `Remaining Balance: ${inr(remaining)}`,
        `_Finboss Billing No.1 ERP_`
      ].join("\n");

      openModal(`
        <h3>Payment Recorded!</h3>
        <div class="qr-checkout-box" style="text-align:left;justify-items:start;">
          <div class="bold">${escapeHtml(payerName)} • ${escapeHtml(refLabel)}</div>
          <div>Recorded: <strong class="text-emerald">${inr(amt)}</strong> (${escapeHtml(method.toUpperCase())})</div>
          <div>Remaining Balance: <strong>${inr(remaining)}</strong></div>
        </div>
        <div class="modal-actions">
          <button id="m-done" class="btn btn-ghost">Done</button>
          <button id="rp-wa" class="btn btn-primary">Share Receipt on WhatsApp</button>
        </div>
      `);
      $("m-done").onclick = closeModal;
      $("rp-wa").onclick = () => shareToWhatsApp(receiptText, payerPhone);
      showToast(`Recorded ${inr(amt)} payment for ${payerName}!`);
    };
  };

  renderForm();
}
