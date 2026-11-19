import {
  doc,
  serverTimestamp,
  writeBatch
} from "https://www.gstatic.com/firebasejs/11.14.0/firebase-firestore.js";
import { db, INDIA_STATES, state } from "./state.js";
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
  numberToIndianWords,
  openModal,
  profileScoped,
  shareToWhatsApp,
  showToast
} from "./utils.js";

export function renderBilling() {
  const q = ($("invoice-search")?.value || "").trim().toLowerCase();
  const statusFilter = state.billingStatusFilter || "all";
  const allScoped = profileScoped(state.invoices);

  const counts = {
    all: allScoped.length,
    paid: allScoped.filter((i) => i.status === "paid").length,
    partial: allScoped.filter((i) => i.status === "partial").length,
    unpaid: allScoped.filter((i) => i.status === "unpaid").length
  };

  document.querySelectorAll("[data-bill-status]").forEach((btn) => {
    const key = btn.dataset.billStatus;
    btn.classList.toggle("active", key === statusFilter);
    const labelMap = {
      all: `All (${counts.all})`,
      paid: `Paid (${counts.paid})`,
      partial: `Partial (${counts.partial})`,
      unpaid: `Unpaid (${counts.unpaid})`
    };
    btn.textContent = labelMap[key] || btn.textContent;
  });

  const invoices = allScoped.filter((i) => {
    if (statusFilter !== "all" && i.status !== statusFilter) return false;
    if (!q) return true;
    return [i.invoiceNumber, i.partyName, i.partyGstin, i.irn, i.ewbNumber]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(q));
  });

  $("billing-invoices-table").innerHTML = invoices.length
    ? invoices
        .map((inv) => {
          const bal = Math.max(0, Number(inv.grandTotal || 0) - Number(inv.paidAmount || 0));
          const comp = inv.irn || inv.ewbNumber
            ? '<span class="pill pill-info">Legacy GST references · verify on portal</span>' : "";

          const itemPreview = (inv.lineItems || [])
            .slice(0, 2)
            .map((l) => `${escapeHtml(l.name)} ×${fmtQty(l.qty)}`)
            .join(", ");
          const moreCount = Math.max(0, (inv.lineItems || []).length - 2);

          return `
        <tr>
          <td>
            <div class="mono bold">${escapeHtml(inv.invoiceNumber)}</div>
            <div class="muted">${escapeHtml((inv.invoiceType || "tax_invoice").replace("_", " ").toUpperCase())} • ${escapeHtml((inv.paymentMethod || "upi").toUpperCase())}</div>
          </td>
          <td>
            <div class="bold">${escapeHtml(inv.partyName)}</div>
            <div class="muted mono">${escapeHtml(inv.partyGstin || "Unregistered")}</div>
            ${itemPreview ? `<div class="muted">${itemPreview}${moreCount > 0 ? ` +${moreCount} more` : ""}</div>` : ""}
          </td>
          <td><span class="pill pill-${inv.status === "paid" ? "paid" : inv.status === "partial" ? "partial" : "unpaid"}">${escapeHtml(String(inv.status || "unpaid").toUpperCase())}</span></td>
          <td>${comp || `<span class="muted">Standard</span>`}</td>
          <td class="right mono bold">${inr(inv.grandTotal)}</td>
          <td class="right mono ${bal > 0 ? "text-loss" : ""}">${inr(bal)}</td>
          <td class="right">
            <div class="row-actions">
              <button class="btn btn-sm btn-ghost" data-action="print-inv" data-id="${escapeHtml(inv.id)}">Print PDF</button>
              <button class="btn btn-sm btn-ghost" data-action="wa-inv" data-id="${escapeHtml(inv.id)}">WhatsApp</button>
              <button class="btn btn-sm btn-ghost" data-action="edit-inv" data-id="${escapeHtml(inv.id)}">Edit</button>
              ${bal > 0 ? `<button class="btn btn-sm btn-primary" data-action="mark-paid" data-id="${escapeHtml(inv.id)}">Mark Paid</button>` : ""}
              ${!isPlainNonGst() && inv.taxMode !== "none" ? `
                <a class="btn btn-sm btn-secondary" href="https://einvoice1.gst.gov.in/" target="_blank" rel="noopener noreferrer">E-Invoice Portal</a>
                <a class="btn btn-sm btn-secondary" href="https://ewaybillgst.gov.in/" target="_blank" rel="noopener noreferrer">E-Way Bill Portal</a>` : ""}
              <button class="btn btn-sm btn-danger-soft" data-action="delete-inv" data-id="${escapeHtml(inv.id)}">Delete</button>
            </div>
          </td>
        </tr>`;
        })
        .join("")
    : `<tr><td colspan="7" class="muted">No matching invoices found.</td></tr>`;
}

export function openInvoiceModal(existingInvoice = null, prefillDraft = null) {
  const settings = loadAppSettings();
  const prof = activeProfile();
  const plain = isPlainNonGst();
  const parties = profileScoped(state.parties);
  const stockItems = profileScoped(state.inventory);

  const isEditing = Boolean(existingInvoice);
  const invNum =
    existingInvoice?.invoiceNumber ||
    prefillDraft?.invoiceNumber ||
    `${settings.invoicePrefix || "BK-"}${String(Date.now()).slice(-6)}`;

  let selectedPartyId = existingInvoice?.partyId || prefillDraft?.partyId || "";
  let partyName = existingInvoice?.partyName || prefillDraft?.partyName || "";
  let partyGstin = plain ? "" : existingInvoice?.partyGstin || prefillDraft?.partyGstin || "";
  let partyAddress = existingInvoice?.partyAddress || "";
  let partyState = existingInvoice?.partyState || prof.state || "Maharashtra";
  let partyPincode = existingInvoice?.partyPincode || "";
  let invoiceType = existingInvoice?.invoiceType || settings.defaultInvoiceType || "tax_invoice";
  let taxMode = plain
    ? "none"
    : existingInvoice?.taxMode || "cgst_sgst";
  let paymentMethod = existingInvoice?.paymentMethod || prefillDraft?.paymentMethod || settings.defaultPaymentMethod || "upi";
  let discountAmount = Number(existingInvoice?.discountAmount ?? prefillDraft?.discountAmount ?? 0);
  let paidAmountOverride = existingInvoice
    ? Number(existingInvoice.paidAmount || 0)
    : prefillDraft?.paidAmount != null
      ? Number(prefillDraft.paidAmount)
      : null;
  let notes = existingInvoice?.notes || prefillDraft?.notes || "";

  let lines = (existingInvoice?.lineItems || prefillDraft?.lineItems || []).map((l) => ({
    itemId: l.itemId || l.matchedInventoryId || "",
    name: l.name || "",
    sku: l.sku || "",
    hsnCode: plain ? "" : l.hsnCode || "",
    unit: l.unit || "PCS",
    qty: Number(l.qty || 1),
    unitPrice: Number(l.unitPrice || 0),
    gstRatePercent: plain ? 0 : Number(l.gstRatePercent ?? settings.defaultGstSlabPercent ?? 18)
  }));

  if (!lines.length) {
    lines = [
      {
        itemId: "",
        name: "",
        sku: "",
        hsnCode: plain ? "" : "",
        unit: "PCS",
        qty: 1,
        unitPrice: 0,
        gstRatePercent: plain ? 0 : Number(settings.defaultGstSlabPercent ?? 18)
      }
    ];
  }

  const computeTotals = () => {
    let subtotal = 0;
    let gstTotal = 0;
    lines.forEach((l) => {
      const qty = Math.max(0.01, Number(l.qty || 0));
      const price = Math.max(0, Number(l.unitPrice || 0));
      const taxable = qty * price;
      const rate = plain || taxMode === "none" ? 0 : Math.min(28, Math.max(0, Number(l.gstRatePercent || 0)));
      const gst = taxable * (rate / 100);
      subtotal += taxable;
      gstTotal += gst;
    });
    const safeDiscount = Math.min(subtotal + gstTotal, Math.max(0, Number(discountAmount || 0)));
    const grandTotal = Math.max(0.01, subtotal + gstTotal - safeDiscount);
    const defaultPaid = paymentMethod === "credit" ? 0 : grandTotal;
    const paidAmount =
      paidAmountOverride !== null ? Math.min(grandTotal, Math.max(0, paidAmountOverride)) : defaultPaid;
    return { subtotal, gstTotal, safeDiscount, grandTotal, paidAmount };
  };

  const renderForm = () => {
    const { subtotal, gstTotal, grandTotal, paidAmount } = computeTotals();
    const partyOptions = parties
      .map(
        (p) =>
          `<option value="${escapeHtml(p.id)}" ${p.id === selectedPartyId ? "selected" : ""}>${escapeHtml(p.name)} (${escapeHtml((p.partyType || "customer").toUpperCase())})</option>`
      )
      .join("");

    const stockOptions = stockItems
      .map(
        (it) =>
          `<option value="${escapeHtml(it.id)}">${escapeHtml(it.name)} • ${inr(it.salePrice)} (Stk ${fmtQty(it.stockQty)} ${escapeHtml(it.unit)})</option>`
      )
      .join("");

    const linesHtml = lines
      .map((l, idx) => {
        const lineTaxable = Math.max(0, Number(l.qty || 0)) * Math.max(0, Number(l.unitPrice || 0));
        const lineRate = plain || taxMode === "none" ? 0 : Number(l.gstRatePercent || 0);
        const lineTotal = lineTaxable * (1 + lineRate / 100);
        return `
        <div class="line-item-row" data-line-idx="${idx}">
          <div class="form-field">
            <label>Pick Stock SKU (Optional)</label>
            <select class="input-select line-sku-select" data-idx="${idx}">
              <option value="">— Custom Item —</option>
              ${stockItems
                .map(
                  (it) =>
                    `<option value="${escapeHtml(it.id)}" ${it.id === l.itemId ? "selected" : ""}>${escapeHtml(it.name)} (Stk ${fmtQty(it.stockQty)})</option>`
                )
                .join("")}
            </select>
          </div>
          <div class="form-field">
            <label>Item / Service Name *</label>
            <input class="input-text line-name" data-idx="${idx}" value="${escapeHtml(l.name)}" placeholder="Item description" />
          </div>
          <div class="form-field">
            <label>HSN &amp; Unit</label>
            <div style="display:flex;gap:4px;">
              <input class="input-text line-hsn mono" data-idx="${idx}" value="${escapeHtml(l.hsnCode)}" placeholder="HSN" ${plain ? "disabled" : ""} style="flex:1;" />
              <input class="input-text line-unit" data-idx="${idx}" value="${escapeHtml(l.unit)}" placeholder="PCS" style="width:68px;" />
            </div>
          </div>
          <div class="form-field">
            <label>Qty × Rate (₹)</label>
            <div style="display:flex;gap:4px;">
              <input type="number" step="0.01" min="0.01" class="input-text line-qty mono" data-idx="${idx}" value="${l.qty}" style="width:76px;" />
              <input type="number" step="0.01" min="0" class="input-text line-price mono" data-idx="${idx}" value="${l.unitPrice}" style="flex:1;" />
            </div>
          </div>
          <div class="form-field">
            <label>GST % • Total</label>
            <div style="display:flex;gap:4px;align-items:center;">
              <select class="input-select line-gst" data-idx="${idx}" ${plain || taxMode === "none" ? "disabled" : ""} style="width:80px;">
                ${[0, 5, 12, 18, 28].map((r) => `<option value="${r}" ${Number(l.gstRatePercent) === r ? "selected" : ""}>${r}%</option>`).join("")}
              </select>
              <span class="mono bold" style="min-width:78px;text-align:right;">${inr(lineTotal)}</span>
              ${lines.length > 1 ? `<button type="button" class="btn btn-sm btn-danger-soft line-remove" data-idx="${idx}">✕</button>` : ""}
            </div>
          </div>
        </div>`;
      })
      .join("");

    const matchedPartyObj = parties.find((p) => p.id === selectedPartyId);
    let creditWarningHtml = "";
    if (settings.warnOnCreditLimitExceeded && matchedPartyObj && Number(matchedPartyObj.creditLimit || 0) > 0) {
      const currentDue =
        Number(matchedPartyObj.openingDue || 0) + Number(matchedPartyObj.totalBilled || 0) - Number(matchedPartyObj.totalPaid || 0);
      const projectedDue = currentDue + Math.max(0, grandTotal - paidAmount);
      if (projectedDue > Number(matchedPartyObj.creditLimit)) {
        creditWarningHtml = `<div class="warn-box">Credit Limit Alert: Projected Ledger due ${inr(projectedDue)} exceeds limit ${inr(matchedPartyObj.creditLimit)}</div>`;
      }
    }

    openModal(
      `
      <h3>${isEditing ? `Edit Invoice ${escapeHtml(existingInvoice.invoiceNumber)}` : `Create ${plain ? "Plain Non-GST Bill" : "GST Tax Invoice"}`}</h3>
      <div class="form-grid">
        <div class="form-field">
          <label>Invoice Number *</label>
          <input id="inv-num" class="input-text mono" value="${escapeHtml(invNum)}" />
        </div>
        <div class="form-field">
          <label>Bill Format</label>
          <select id="inv-type" class="input-select">
            <option value="tax_invoice" ${invoiceType === "tax_invoice" ? "selected" : ""}>${plain ? "Standard Bill" : "GST Tax Invoice"}</option>
            <option value="pos_bill" ${invoiceType === "pos_bill" ? "selected" : ""}>POS Quick Bill</option>
          </select>
        </div>
        <div class="form-field">
          <label>Select Saved Party</label>
          <select id="inv-party-sel" class="input-select">
            <option value="">Walk-in / Custom Customer</option>
            ${partyOptions}
          </select>
        </div>
        <div class="form-field">
          <label>Tax Mode</label>
          <select id="inv-tax-mode" class="input-select" ${plain ? "disabled" : ""}>
            <option value="cgst_sgst" ${taxMode === "cgst_sgst" ? "selected" : ""}>CGST + SGST (Intra-State)</option>
            <option value="igst" ${taxMode === "igst" ? "selected" : ""}>IGST (Inter-State)</option>
            <option value="none" ${taxMode === "none" ? "selected" : ""}>No GST (Plain)</option>
          </select>
        </div>
        <div class="form-field">
          <label>Customer / Party Name *</label>
          <input id="inv-party-name" class="input-text" value="${escapeHtml(partyName)}" placeholder="Customer Name" />
        </div>
        <div class="form-field">
          <label>Customer GSTIN</label>
          <input id="inv-party-gstin" class="input-text mono" ${plain ? "disabled" : ""} value="${escapeHtml(partyGstin)}" placeholder="${plain ? "Disabled in Plain mode" : "27ABCDE1234F1Z5"}" />
        </div>
        <div class="form-field">
          <label>Party State</label>
          <select id="inv-party-state" class="input-select">
            <option value="">Select State</option>
            ${INDIA_STATES.map((s) => `<option value="${s}" ${s.toLowerCase() === String(partyState || "").toLowerCase() ? "selected" : ""}>${s}</option>`).join("")}
          </select>
        </div>
        <div class="form-field">
          <label>Party PIN Code</label>
          <input id="inv-party-pin" class="input-text mono" value="${escapeHtml(partyPincode)}" maxlength="12" placeholder="400001" />
        </div>
        <div class="form-field span-2">
          <label>Billing Address</label>
          <input id="inv-party-addr" class="input-text" value="${escapeHtml(partyAddress)}" placeholder="Street / City / Industrial Area" />
        </div>
      </div>

      <div class="section-divider-row">
        <span class="bold">Line Items (${lines.length}/25)</span>
        <div style="display:flex;gap:8px;align-items:center;">
          ${stockItems.length ? `<select id="quick-add-sku" class="input-select" style="max-width:220px;"><option value="">+ Quick Add from Stock...</option>${stockOptions}</select>` : ""}
          <button type="button" id="add-line-btn" class="btn btn-sm btn-secondary">+ Add Line</button>
        </div>
      </div>
      <div id="invoice-lines-box" class="lines-container">${linesHtml}</div>

      <div class="form-grid" style="margin-top:12px;">
        <div class="form-field">
          <label>Discount (₹)</label>
          <input id="inv-discount" type="number" min="0" step="0.01" class="input-text mono" value="${discountAmount}" />
        </div>
        <div class="form-field">
          <label>Payment Mode</label>
          <select id="inv-pay-method" class="input-select">
            <option value="upi" ${paymentMethod === "upi" ? "selected" : ""}>UPI</option>
            <option value="cash" ${paymentMethod === "cash" ? "selected" : ""}>Cash</option>
            <option value="card" ${paymentMethod === "card" ? "selected" : ""}>Card</option>
            <option value="credit" ${paymentMethod === "credit" ? "selected" : ""}>Credit (Udhar)</option>
          </select>
        </div>
        <div class="form-field">
          <label>Paid Amount (₹)</label>
          <input id="inv-paid" type="number" min="0" step="0.01" class="input-text mono" value="${paidAmount.toFixed(2)}" />
        </div>
        <div class="form-field">
          <label>Notes / Terms</label>
          <input id="inv-notes" class="input-text" value="${escapeHtml(notes)}" placeholder="Optional remarks" />
        </div>
      </div>

      <div class="invoice-summary-bar">
        <div>Taxable: <strong class="mono">${inr(subtotal)}</strong></div>
        <div>GST: <strong class="mono">${inr(gstTotal)}</strong></div>
        <div>Grand Total: <strong class="mono text-emerald" style="font-size:16px;">${inr(grandTotal)}</strong></div>
      </div>
      ${creditWarningHtml}
      <p id="m-err" class="error-box hidden"></p>
      <div class="modal-actions">
        <button type="button" id="m-cancel" class="btn btn-ghost">Cancel</button>
        <button type="button" id="m-save-inv" class="btn btn-primary">${isEditing ? "Update Invoice" : "Save & Sync Invoice"}</button>
      </div>
      `,
      880
    );

    const syncTopFields = () => {
      partyName = $("inv-party-name").value;
      partyGstin = plain ? "" : $("inv-party-gstin").value.toUpperCase();
      partyAddress = $("inv-party-addr").value;
      partyState = $("inv-party-state").value;
      partyPincode = $("inv-party-pin").value;
      invoiceType = $("inv-type").value;
      taxMode = plain ? "none" : $("inv-tax-mode").value;
      paymentMethod = $("inv-pay-method").value;
      discountAmount = Number($("inv-discount").value || 0);
      notes = $("inv-notes").value;
    };

    const refreshSummaryOnly = () => {
      syncTopFields();
      const t = computeTotals();
      const bar = document.querySelector(".invoice-summary-bar");
      if (bar) {
        bar.innerHTML = `
          <div>Taxable: <strong class="mono">${inr(t.subtotal)}</strong></div>
          <div>GST: <strong class="mono">${inr(t.gstTotal)}</strong></div>
          <div>Grand Total: <strong class="mono text-emerald" style="font-size:16px;">${inr(t.grandTotal)}</strong></div>
        `;
      }
      if (paidAmountOverride === null) {
        $("inv-paid").value = t.paidAmount.toFixed(2);
      }
    };

    $("m-cancel").onclick = closeModal;

    $("inv-party-sel").onchange = (e) => {
      syncTopFields();
      selectedPartyId = e.target.value;
      const p = parties.find((x) => x.id === selectedPartyId);
      if (p) {
        partyName = p.name || "";
        partyGstin = plain ? "" : p.gstin || "";
        partyAddress = p.address || "";
        partyState = p.state || prof.state || "";
        partyPincode = p.pincode || "";
        if (!plain && settings.autoDetectGstModeFromState && p.state && prof.state) {
          taxMode = p.state.trim().toLowerCase() === prof.state.trim().toLowerCase() ? "cgst_sgst" : "igst";
        }
      }
      renderForm();
    };

    $("inv-tax-mode").onchange = () => {
      syncTopFields();
      renderForm();
    };

    $("inv-pay-method").onchange = () => {
      paidAmountOverride = null;
      refreshSummaryOnly();
    };

    $("inv-discount").oninput = () => {
      paidAmountOverride = null;
      refreshSummaryOnly();
    };

    $("inv-paid").oninput = () => {
      paidAmountOverride = Number($("inv-paid").value || 0);
    };

    if ($("quick-add-sku")) {
      $("quick-add-sku").onchange = (e) => {
        const skuId = e.target.value;
        if (!skuId) return;
        syncTopFields();
        const item = stockItems.find((it) => it.id === skuId);
        if (item && lines.length < 25) {
          const emptyIdx = lines.findIndex((l) => !l.name.trim() && Number(l.unitPrice || 0) === 0);
          const newLine = {
            itemId: item.id,
            name: item.name,
            sku: item.sku || "",
            hsnCode: plain ? "" : item.hsnCode || "",
            unit: item.unit || "PCS",
            qty: 1,
            unitPrice: Number(item.salePrice || 0),
            gstRatePercent: plain ? 0 : Number(item.gstRatePercent ?? 18)
          };
          if (emptyIdx >= 0) lines[emptyIdx] = newLine;
          else lines.push(newLine);
          paidAmountOverride = null;
          renderForm();
        }
      };
    }

    $("add-line-btn").onclick = () => {
      if (lines.length >= 25) return showToast("Maximum 25 line items per invoice.");
      syncTopFields();
      lines.push({
        itemId: "",
        name: "",
        sku: "",
        hsnCode: "",
        unit: "PCS",
        qty: 1,
        unitPrice: 0,
        gstRatePercent: plain ? 0 : Number(settings.defaultGstSlabPercent ?? 18)
      });
      renderForm();
    };

    document.querySelectorAll(".line-sku-select").forEach((sel) => {
      sel.onchange = (e) => {
        const idx = Number(sel.dataset.idx);
        const item = stockItems.find((it) => it.id === e.target.value);
        syncTopFields();
        if (item) {
          lines[idx] = {
            ...lines[idx],
            itemId: item.id,
            name: item.name,
            sku: item.sku || "",
            hsnCode: plain ? "" : item.hsnCode || "",
            unit: item.unit || "PCS",
            unitPrice: Number(item.salePrice || 0),
            gstRatePercent: plain ? 0 : Number(item.gstRatePercent ?? 18)
          };
        } else {
          lines[idx].itemId = "";
          lines[idx].sku = "";
        }
        paidAmountOverride = null;
        renderForm();
      };
    });

    document.querySelectorAll(".line-remove").forEach((btn) => {
      btn.onclick = () => {
        const idx = Number(btn.dataset.idx);
        syncTopFields();
        lines.splice(idx, 1);
        paidAmountOverride = null;
        renderForm();
      };
    });

    const bindLineInput = (cls, key, isNum = false) => {
      document.querySelectorAll(cls).forEach((el) => {
        el.oninput = () => {
          const idx = Number(el.dataset.idx);
          lines[idx][key] = isNum ? Number(el.value || 0) : el.value;
          paidAmountOverride = null;
          refreshSummaryOnly();
          const row = el.closest(".line-item-row");
          const totSpan = row?.querySelector(".line-item-row span.mono.bold");
          if (totSpan) {
            const sub = Math.max(0, Number(lines[idx].qty || 0)) * Math.max(0, Number(lines[idx].unitPrice || 0));
            const r = plain || taxMode === "none" ? 0 : Number(lines[idx].gstRatePercent || 0);
            totSpan.textContent = inr(sub * (1 + r / 100));
          }
        };
      });
    };
    bindLineInput(".line-name", "name", false);
    bindLineInput(".line-hsn", "hsnCode", false);
    bindLineInput(".line-unit", "unit", false);
    bindLineInput(".line-qty", "qty", true);
    bindLineInput(".line-price", "unitPrice", true);
    bindLineInput(".line-gst", "gstRatePercent", true);

    $("m-save-inv").onclick = async () => {
      syncTopFields();
      const cleanInvNo = ($("inv-num").value || "").trim().slice(0, 40);
      const finalPartyName = (partyName || "").trim().slice(0, 100) || "Walk-in Customer";
      const cleanLines = lines
        .filter((l) => l.name.trim())
        .map((l) => {
          const qty = Math.max(0.01, Number(l.qty || 1));
          const unitPrice = Math.max(0, Number(l.unitPrice || 0));
          const gstRatePercent = plain || taxMode === "none" ? 0 : Math.min(28, Math.max(0, Number(l.gstRatePercent || 0)));
          const taxableAmount = Math.round(qty * unitPrice * 100) / 100;
          const gstAmount = Math.round(taxableAmount * (gstRatePercent / 100) * 100) / 100;
          return {
            itemId: l.itemId || "",
            name: l.name.trim().slice(0, 100),
            sku: (l.sku || "").trim().slice(0, 40),
            hsnCode: plain ? "" : (l.hsnCode || "").trim().toUpperCase().slice(0, 16),
            unit: (l.unit || "PCS").trim().toUpperCase().slice(0, 15),
            qty,
            unitPrice,
            gstRatePercent,
            taxableAmount,
            gstAmount,
            lineTotal: Math.round((taxableAmount + gstAmount) * 100) / 100
          };
        });

      if (!cleanInvNo || !cleanLines.length) {
        $("m-err").textContent = "Invoice number and at least 1 line item with a name are required.";
        $("m-err").classList.remove("hidden");
        return;
      }

      const subtotal = Math.round(cleanLines.reduce((s, l) => s + l.taxableAmount, 0) * 100) / 100;
      const gstTotal = Math.round(cleanLines.reduce((s, l) => s + l.gstAmount, 0) * 100) / 100;
      const safeDiscount = Math.min(subtotal + gstTotal, Math.max(0, Math.round(discountAmount * 100) / 100));
      const grandTotal = Math.max(0.01, Math.round((subtotal + gstTotal - safeDiscount) * 100) / 100);
      const actualPaid = Math.min(grandTotal, Math.max(0, Number($("inv-paid").value || 0)));
      const status = actualPaid >= grandTotal - 0.01 ? "paid" : actualPaid > 0 ? "partial" : "unpaid";

      const btn = $("m-save-inv");
      btn.disabled = true;
      btn.textContent = "Syncing...";

      try {
        const uid = state.user.uid;
        const batch = writeBatch(db);
        const invId = existingInvoice?.id || `inv_${Date.now()}`;
        const invRef = doc(db, "users", uid, "invoices", invId);
        const cleanPin = String(partyPincode || "").replace(/[^0-9-]/g, "").slice(0, 12);

        if (existingInvoice) {
          batch.update(invRef, {
            invoiceNumber: cleanInvNo,
            partyId: selectedPartyId || "",
            partyName: finalPartyName,
            partyGstin: plain ? "" : partyGstin.trim().toUpperCase().slice(0, 30),
            partyAddress: partyAddress.trim().slice(0, 200),
            partyState: partyState.trim().slice(0, 60),
            partyPincode: cleanPin,
            invoiceType,
            taxMode: plain ? "none" : taxMode,
            paymentMethod,
            status,
            subtotal,
            gstTotal,
            discountAmount: safeDiscount,
            grandTotal,
            paidAmount: actualPaid,
            notes: notes.trim().slice(0, 240),
            lineItems: cleanLines,
            updatedAt: serverTimestamp()
          });
        } else {
          batch.set(invRef, {
            userId: uid,
            businessProfileId: existingInvoice?.businessProfileId || state.activeProfileId || "main_1",
            invoiceNumber: cleanInvNo,
            partyId: selectedPartyId || "",
            partyName: finalPartyName,
            partyGstin: plain ? "" : partyGstin.trim().toUpperCase().slice(0, 30),
            partyAddress: partyAddress.trim().slice(0, 200),
            partyState: partyState.trim().slice(0, 60),
            partyPincode: cleanPin,
            invoiceType,
            taxMode: plain ? "none" : taxMode,
            paymentMethod,
            status,
            subtotal,
            gstTotal,
            discountAmount: safeDiscount,
            grandTotal,
            paidAmount: actualPaid,
            notes: notes.trim().slice(0, 240) || "Created via Finboss Desktop Web",
            lineItems: cleanLines,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
          });
        }

        // Stock reconciliation
        if (settings.autoDeductStockOnSale) {
          const oldQty = {};
          (existingInvoice?.lineItems || []).forEach((l) => {
            if (l.itemId) oldQty[l.itemId] = (oldQty[l.itemId] || 0) + Number(l.qty || 0);
          });
          const newQty = {};
          cleanLines.forEach((l) => {
            if (l.itemId) newQty[l.itemId] = (newQty[l.itemId] || 0) + Number(l.qty || 0);
          });
          const invMap = new Map(state.inventory.map((it) => [it.id, it]));
          new Set([...Object.keys(oldQty), ...Object.keys(newQty)]).forEach((itemId) => {
            const stItem = invMap.get(itemId);
            if (!stItem) return;
            const deltaUsed = (newQty[itemId] || 0) - (oldQty[itemId] || 0);
            if (deltaUsed !== 0) {
              const newStock = Math.max(0, Number(stItem.stockQty || 0) - deltaUsed);
              batch.update(doc(db, "users", uid, "inventory_items", itemId), {
                stockQty: newStock,
                updatedAt: serverTimestamp()
              });
            }
          });
        }

        // Party Ledger reconciliation
        const partyMap = new Map(state.parties.map((p) => [p.id, p]));
        const oldPid = existingInvoice?.partyId || "";
        const newPid = selectedPartyId || "";
        if (oldPid && oldPid === newPid) {
          const p = partyMap.get(oldPid);
          if (p) {
            batch.update(doc(db, "users", uid, "parties", oldPid), {
              totalBilled: Math.max(0, Number(p.totalBilled || 0) - Number(existingInvoice.grandTotal || 0) + grandTotal),
              totalPaid: Math.max(0, Number(p.totalPaid || 0) - Number(existingInvoice.paidAmount || 0) + actualPaid),
              updatedAt: serverTimestamp()
            });
          }
        } else {
          if (oldPid && partyMap.has(oldPid)) {
            const oldP = partyMap.get(oldPid);
            batch.update(doc(db, "users", uid, "parties", oldPid), {
              totalBilled: Math.max(0, Number(oldP.totalBilled || 0) - Number(existingInvoice.grandTotal || 0)),
              totalPaid: Math.max(0, Number(oldP.totalPaid || 0) - Number(existingInvoice.paidAmount || 0)),
              updatedAt: serverTimestamp()
            });
          }
          if (newPid && partyMap.has(newPid)) {
            const newP = partyMap.get(newPid);
            batch.update(doc(db, "users", uid, "parties", newPid), {
              totalBilled: Math.max(0, Number(newP.totalBilled || 0) + grandTotal),
              totalPaid: Math.max(0, Number(newP.totalPaid || 0) + actualPaid),
              updatedAt: serverTimestamp()
            });
          }
        }

        await batch.commit();
        closeModal();
        showToast(`${isEditing ? "Updated" : "Saved"} invoice ${cleanInvNo} (${inr(grandTotal)})!`);
      } catch (err) {
        btn.disabled = false;
        btn.textContent = isEditing ? "Update Invoice" : "Save & Sync Invoice";
        $("m-err").textContent = err?.message || "Failed to save invoice.";
        $("m-err").classList.remove("hidden");
      }
    };
  };

  renderForm();
}

export function openPrintInvoiceDialog(invoice) {
  const settings = loadAppSettings();
  if (!settings.askPrintOptionsBeforePrint) {
    renderAndPrintInvoice(invoice, settings);
    return;
  }
  const plain = isPlainNonGst();
  openModal(`
    <h3>Print / Export PDF • ${escapeHtml(invoice.invoiceNumber)}</h3>
    <div class="form-grid">
      <div class="form-field">
        <label>Paper / Roll Size</label>
        <select id="pr-size" class="input-select">
          <option value="a4" ${settings.defaultPaperSize === "a4" ? "selected" : ""}>A4 Standard (210 × 297 mm)</option>
          <option value="a5" ${settings.defaultPaperSize === "a5" ? "selected" : ""}>A5 Half-Sheet (148 × 210 mm)</option>
          <option value="thermal_80" ${settings.defaultPaperSize === "thermal_80" ? "selected" : ""}>Thermal Roll 80mm POS</option>
          <option value="thermal_58" ${settings.defaultPaperSize === "thermal_58" ? "selected" : ""}>Thermal Roll 58mm Compact</option>
        </select>
      </div>
      <div class="form-field">
        <label>Bill Design Style</label>
        <select id="pr-style" class="input-select">
          <option value="modern" ${settings.defaultPrintStyle === "modern" ? "selected" : ""}>Modern Executive (Color Accent)</option>
          <option value="tally" ${settings.defaultPrintStyle === "tally" ? "selected" : ""}>Classic Tally Grid (B2B Formal)</option>
          <option value="thermal" ${settings.defaultPrintStyle === "thermal" ? "selected" : ""}>Thermal POS Receipt</option>
        </select>
      </div>
      <label class="check-row"><input id="pr-hsn" type="checkbox" ${settings.showHsnOnInvoicePrint && !plain ? "checked" : ""} ${plain ? "disabled" : ""} /> Show HSN/SAC Column</label>
      <label class="check-row"><input id="pr-addr" type="checkbox" ${settings.showAddressAndStateOnPrint ? "checked" : ""} /> Show Full Address &amp; State</label>
      <label class="check-row span-2"><input id="pr-words" type="checkbox" ${settings.showAmountInWordsOnPrint ? "checked" : ""} /> Show Grand Total in Words (Rupees)</label>
    </div>
    <div class="modal-actions">
      <button id="m-cancel" class="btn btn-ghost">Cancel</button>
      <button id="pr-go" class="btn btn-primary">Open Print / Save PDF</button>
    </div>
  `);
  $("m-cancel").onclick = closeModal;
  $("pr-size").onchange = (e) => {
    if (e.target.value.startsWith("thermal")) $("pr-style").value = "thermal";
  };
  $("pr-go").onclick = () => {
    const opts = {
      ...settings,
      defaultPaperSize: $("pr-size").value,
      defaultPrintStyle: $("pr-style").value,
      showHsnOnInvoicePrint: $("pr-hsn").checked,
      showAddressAndStateOnPrint: $("pr-addr").checked,
      showAmountInWordsOnPrint: $("pr-words").checked
    };
    closeModal();
    renderAndPrintInvoice(invoice, opts);
  };
}

function renderAndPrintInvoice(invoice, opts) {
  const prof = activeProfile();
  const plain = isPlainNonGst();
  const size = opts.defaultPaperSize || "a4";
  const isThermal = size.startsWith("thermal");
  const maxW = size === "thermal_58" ? "58mm" : size === "thermal_80" ? "80mm" : size === "a5" ? "148mm" : "100%";
  const due = Math.max(0, Number(invoice.grandTotal || 0) - Number(invoice.paidAmount || 0));

  const cgst = invoice.taxMode === "cgst_sgst" ? Number(invoice.gstTotal || 0) / 2 : 0;
  const sgst = invoice.taxMode === "cgst_sgst" ? Number(invoice.gstTotal || 0) / 2 : 0;
  const igst = invoice.taxMode === "igst" ? Number(invoice.gstTotal || 0) : 0;

  const rows = (invoice.lineItems || [])
    .map(
      (l, i) => `<tr>
        <td>${i + 1}. ${escapeHtml(l.name)}</td>
        ${!plain && opts.showHsnOnInvoicePrint && !isThermal ? `<td class="mono">${escapeHtml(l.hsnCode || "—")}</td>` : ""}
        <td class="right mono">${fmtQty(l.qty)} ${escapeHtml(l.unit || "")}</td>
        <td class="right mono">${inr(l.unitPrice)}</td>
        ${!plain && !isThermal ? `<td class="right mono">${l.gstRatePercent || 0}%</td>` : ""}
        <td class="right mono bold">${inr(l.lineTotal)}</td>
      </tr>`
    )
    .join("");

  $("print-sheet").innerHTML = `
    <div style="width:${maxW};margin:0 auto;border:${opts.defaultPrintStyle === "tally" ? "2px solid #000" : "1px solid #334155"};padding:${isThermal ? "8px" : "20px"};font-size:${isThermal ? "11px" : "12px"};font-family:Inter,sans-serif;">
      <div style="display:flex;justify-content:space-between;align-items:flex-start;border-bottom:2px solid #0B253C;padding-bottom:10px;gap:10px;">
        <div>
          <h2 style="margin:0;color:#0B253C;">${escapeHtml(prof.businessName || "Finboss Enterprise")}</h2>
          ${!plain ? `<div>GSTIN: ${escapeHtml(prof.gstin || "Unregistered")}</div>` : `<div>Cash Memo / Non-GST Estimate</div>`}
          ${opts.showAddressAndStateOnPrint ? `<div>${escapeHtml([prof.address, prof.state, prof.pincode].filter(Boolean).join(", "))}</div>` : ""}
        </div>
        <div style="text-align:right;">
          <h3 style="margin:0;">${plain ? "BILL / CASH MEMO" : "TAX INVOICE"}</h3>
          <div class="mono bold">${escapeHtml(invoice.invoiceNumber)}</div>
          <div class="mono">${new Date(invoice.createdAt?.seconds ? invoice.createdAt.seconds * 1000 : Date.now()).toLocaleDateString("en-IN")}</div>
        </div>
      </div>

      <div style="margin:10px 0;padding:8px;background:#F8FAFC;border:1px solid #E2E8F0;">
        <strong>Billed To:</strong> ${escapeHtml(invoice.partyName)}
        ${!plain && invoice.partyGstin ? ` • <span class="mono">GSTIN: ${escapeHtml(invoice.partyGstin)}</span>` : ""}
        ${opts.showAddressAndStateOnPrint && (invoice.partyAddress || invoice.partyState) ? `<div>${escapeHtml([invoice.partyAddress, invoice.partyState, invoice.partyPincode].filter(Boolean).join(", "))}</div>` : ""}
      </div>

      <table style="width:100%;border-collapse:collapse;">
        <thead>
          <tr>
            <th>Item</th>
            ${!plain && opts.showHsnOnInvoicePrint && !isThermal ? `<th>HSN</th>` : ""}
            <th class="right">Qty</th>
            <th class="right">Rate</th>
            ${!plain && !isThermal ? `<th class="right">GST</th>` : ""}
            <th class="right">Total</th>
          </tr>
        </thead>
        <tbody>${rows}</tbody>
      </table>

      <div style="margin-top:12px;border-top:1px solid #CBD5E1;padding-top:10px;display:flex;justify-content:space-between;align-items:flex-end;gap:12px;">
        <div style="font-size:11px;max-width:60%;">
          ${opts.showAmountInWordsOnPrint && !isThermal ? `<div><strong>In Words:</strong> ${escapeHtml(numberToIndianWords(invoice.grandTotal))}</div>` : ""}
          ${invoice.notes ? `<div style="margin-top:4px;"><strong>Note:</strong> ${escapeHtml(invoice.notes)}</div>` : ""}
          ${opts.invoiceTermsText ? `<div style="margin-top:4px;color:#475569;">${escapeHtml(opts.invoiceTermsText)}</div>` : ""}
          <div style="margin-top:6px;font-weight:700;">${escapeHtml(opts.invoiceFooterNote || "Thank you for your business!")}</div>
        </div>
        <div style="text-align:right;min-width:190px;">
          <div>Taxable: <span class="mono">${inr(invoice.subtotal)}</span></div>
          ${cgst > 0 ? `<div>CGST: <span class="mono">${inr(cgst)}</span> • SGST: <span class="mono">${inr(sgst)}</span></div>` : ""}
          ${igst > 0 ? `<div>IGST: <span class="mono">${inr(igst)}</span></div>` : ""}
          ${Number(invoice.discountAmount || 0) > 0 ? `<div>Discount: -<span class="mono">${inr(invoice.discountAmount)}</span></div>` : ""}
          <div class="mono bold" style="font-size:15px;margin-top:4px;">Total: ${inr(invoice.grandTotal)}</div>
          <div>Paid: ${inr(invoice.paidAmount)} • Balance: ${inr(due)}</div>
        </div>
      </div>
    </div>
  `;
  window.print();
}

export function bindBillingTableEvents() {
  document.querySelectorAll("[data-bill-status]").forEach((btn) => {
    btn.onclick = () => {
      state.billingStatusFilter = btn.dataset.billStatus;
      renderBilling();
    };
  });

  $("billing-invoices-table").onclick = async (e) => {
    const btn = e.target.closest("[data-action]");
    if (!btn) return;
    const inv = state.invoices.find((i) => i.id === btn.dataset.id);
    if (!inv) return;
    const action = btn.dataset.action;

    if (action === "print-inv") return openPrintInvoiceDialog(inv);
    if (action === "edit-inv") return openInvoiceModal(inv);

    if (action === "wa-inv") {
      const biz = activeProfile().businessName || "Finboss Billing No.1 ERP";
      const due = Math.max(0, Number(inv.grandTotal || 0) - Number(inv.paidAmount || 0));
      const lines = (inv.lineItems || [])
        .map((l, idx) => `${idx + 1}. ${l.name} (${fmtQty(l.qty)} ${l.unit}) = ${inr(l.lineTotal)}`)
        .join("\n");
      const text = [
        `*${biz}*`,
        `${isPlainNonGst() ? "Bill" : "Tax Invoice"}: *${inv.invoiceNumber}*`,
        `Customer: ${inv.partyName}`,
        inv.partyGstin ? `GSTIN: ${inv.partyGstin}` : "",
        lines,
        `Grand Total: *${inr(inv.grandTotal)}*`,
        `Paid: ${inr(inv.paidAmount)} • Due: ${inr(due)}`,
        `_Shared via Finboss Billing No.1 ERP_`
      ]
        .filter(Boolean)
        .join("\n");
      return shareToWhatsApp(text, findPartyPhone(inv.partyId, inv.partyName));
    }

    if (action === "mark-paid") {
      const uid = state.user.uid;
      const grand = Number(inv.grandTotal || 0);
      const already = Number(inv.paidAmount || 0);
      const delta = Math.max(0, grand - already);
      const batch = writeBatch(db);
      batch.update(doc(db, "users", uid, "invoices", inv.id), {
        paidAmount: grand,
        status: "paid",
        paymentMethod: "upi",
        updatedAt: serverTimestamp()
      });
      if (inv.partyId && delta > 0) {
        const p = state.parties.find((x) => x.id === inv.partyId);
        if (p) {
          batch.update(doc(db, "users", uid, "parties", p.id), {
            totalBilled: Number(p.totalBilled || 0),
            totalPaid: Number(p.totalPaid || 0) + delta,
            updatedAt: serverTimestamp()
          });
        }
      }
      await batch.commit();
      return showToast(`Marked ${inv.invoiceNumber} as PAID!`);
    }

    if (action === "delete-inv") {
      openModal(`
        <h3>Delete Invoice ${escapeHtml(inv.invoiceNumber)}?</h3>
        <p class="muted">Stock quantities and linked Party Ledger billed/paid totals will be automatically reversed.</p>
        <div class="modal-actions">
          <button id="m-cancel" class="btn btn-ghost">Cancel</button>
          <button id="m-confirm-del" class="btn btn-danger">Delete &amp; Reverse</button>
        </div>
      `);
      $("m-cancel").onclick = closeModal;
      $("m-confirm-del").onclick = async () => {
        const uid = state.user.uid;
        const batch = writeBatch(db);
        const invMap = new Map(state.inventory.map((it) => [it.id, it]));
        const usedMap = {};
        (inv.lineItems || []).forEach((l) => {
          if (l.itemId) usedMap[l.itemId] = (usedMap[l.itemId] || 0) + Number(l.qty || 0);
        });
        Object.entries(usedMap).forEach(([itemId, qty]) => {
          const item = invMap.get(itemId);
          if (item) {
            batch.update(doc(db, "users", uid, "inventory_items", itemId), {
              stockQty: Number(item.stockQty || 0) + qty,
              updatedAt: serverTimestamp()
            });
          }
        });
        if (inv.partyId) {
          const p = state.parties.find((x) => x.id === inv.partyId);
          if (p) {
            batch.update(doc(db, "users", uid, "parties", p.id), {
              totalBilled: Math.max(0, Number(p.totalBilled || 0) - Number(inv.grandTotal || 0)),
              totalPaid: Math.max(0, Number(p.totalPaid || 0) - Number(inv.paidAmount || 0)),
              updatedAt: serverTimestamp()
            });
          }
        }
        batch.delete(doc(db, "users", uid, "invoices", inv.id));
        await batch.commit();
        closeModal();
        showToast(`Deleted invoice ${inv.invoiceNumber} & restored stock/ledger.`);
      };
    }
  };
}
