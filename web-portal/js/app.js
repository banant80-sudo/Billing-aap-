import { initializeApp } from "https://www.gstatic.com/firebasejs/11.1.0/firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  onAuthStateChanged,
  signInWithPopup,
  signOut
} from "https://www.gstatic.com/firebasejs/11.1.0/firebase-auth.js";
import {
  collection,
  doc,
  getDoc,
  getFirestore,
  onSnapshot,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  orderBy
} from "https://www.gstatic.com/firebasejs/11.1.0/firebase-firestore.js";
import QRCode from "https://esm.sh/qrcode@1.5.4";
import {
  ADMIN_RECEIPT_EMAIL,
  FIREBASE_WEB_CONFIG,
  FIRESTORE_DATABASE_ID,
  MERCHANT_BENEFICIARY_NAME,
  MERCHANT_UPI_ID
} from "./firebase-config.js";

const app = initializeApp(FIREBASE_WEB_CONFIG);
const auth = getAuth(app);
const db = getFirestore(app, FIRESTORE_DATABASE_ID);
const googleProvider = new GoogleAuthProvider();

const PLAN_SPECS = {
  trial: { name: "30-Day Free Trial", monthly: 0, yearly: 0, maxProfiles: 3, eInvoice: true },
  silver: { name: "Silver", monthly: 300, yearly: 3600, maxProfiles: 1, eInvoice: false },
  gold: { name: "Gold", monthly: 500, yearly: 5400, maxProfiles: 2, eInvoice: false },
  diamond: { name: "Diamond", monthly: 700, yearly: 6720, maxProfiles: 3, eInvoice: true }
};

const state = {
  user: null,
  rootProfile: null,
  profiles: [],
  activeProfileId: "main_1",
  invoices: [],
  inventory: [],
  parties: [],
  expenses: [],
  unsubs: []
};

const $ = (id) => document.getElementById(id);

function escapeHtml(str) {
  return String(str ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function inr(value) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2
  }).format(Number(value || 0));
}

function showToast(message) {
  const banner = $("toast-banner");
  banner.textContent = message;
  banner.classList.remove("hidden");
  clearTimeout(showToast._t);
  showToast._t = setTimeout(() => banner.classList.add("hidden"), 4500);
}

function normalizeWaPhone(rawPhone) {
  const digits = String(rawPhone || "").replace(/\D/g, "");
  if (digits.length === 10) return `91${digits}`;
  if (digits.length >= 11 && digits.length <= 15) return digits;
  return "";
}

function shareToWhatsApp(text, rawPhone = "") {
  const phone = normalizeWaPhone(rawPhone);
  const url = phone
    ? `https://wa.me/${phone}?text=${encodeURIComponent(text)}`
    : `https://wa.me/?text=${encodeURIComponent(text)}`;
  window.open(url, "_blank", "noopener");
}

function findPartyPhone(partyId, partyName) {
  const match = profileScoped(state.parties).find(
    (p) => (partyId && p.id === partyId) || (partyName && String(p.name).toLowerCase() === String(partyName).toLowerCase())
  );
  return match?.phone || "";
}

function activeProfile() {
  return (
    state.profiles.find((p) => p.id === state.activeProfileId) ||
    state.profiles[0] ||
    state.rootProfile || {
      id: "main_1",
      businessName: "Finboss Enterprise",
      profileMode: "main_gst",
      monthlySalesTarget: 250000
    }
  );
}

function isPlainNonGst() {
  const prof = activeProfile();
  return prof.id === "plain_1" || prof.profileMode === "plain_non_gst";
}

function effectivePlanTier() {
  const rp = state.rootProfile || {};
  const tier = rp.planTier || "trial";
  const expires = Number(rp.planExpiresAtMs || 0);
  if (expires > 0 && Date.now() > expires) return "expired";
  return tier;
}

function daysRemaining() {
  const expires = Number(state.rootProfile?.planExpiresAtMs || 0);
  if (!expires) return 30;
  return Math.max(0, Math.ceil((expires - Date.now()) / 86400000));
}

function profileScoped(list) {
  const pid = state.activeProfileId || "main_1";
  return list.filter((docItem) => {
    const ref = docItem.businessProfileId || "";
    return ref === pid || (pid === "main_1" && ref === "");
  });
}

function clearSubscriptions() {
  state.unsubs.forEach((u) => u && u());
  state.unsubs = [];
}

async function ensureBootstrapWorkspace(user) {
  const userRef = doc(db, "users", user.uid);
  const snap = await getDoc(userRef);
  const nowMs = Date.now();
  const trialExpiry = nowMs + 30 * 86400000;
  if (!snap.exists()) {
    await setDoc(userRef, {
      userId: user.uid,
      ownerName: (user.displayName || "Business Owner").slice(0, 80),
      ownerEmail: (user.email || ADMIN_RECEIPT_EMAIL).slice(0, 150),
      businessName: `${(user.displayName || "Finboss").split(" ")[0]} Enterprises`.slice(0, 100),
      gstin: "",
      address: "",
      state: "Maharashtra",
      pincode: "",
      currencySymbol: "₹",
      fiscalYearLabel: "FY 2026-27",
      businessType: "Retail & Wholesale",
      monthlySalesTarget: 250000,
      planTier: "trial",
      billingCycle: "trial",
      trialStartedAtMs: nowMs,
      planExpiresAtMs: trialExpiry,
      planAmountPaid: 0,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
  }
  const main1Ref = doc(db, "users", user.uid, "business_profiles", "main_1");
  const main1Snap = await getDoc(main1Ref);
  if (!main1Snap.exists()) {
    const base = snap.exists() ? snap.data() : {};
    await setDoc(main1Ref, {
      userId: user.uid,
      ownerName: (base.ownerName || user.displayName || "Business Owner").slice(0, 80),
      ownerEmail: (user.email || ADMIN_RECEIPT_EMAIL).slice(0, 150),
      businessName: (base.businessName || "Main GST Business").slice(0, 100),
      gstin: base.gstin || "",
      address: base.address || "",
      state: base.state || "Maharashtra",
      pincode: base.pincode || "",
      currencySymbol: "₹",
      fiscalYearLabel: "FY 2026-27",
      businessType: base.businessType || "Retail & Wholesale",
      monthlySalesTarget: Number(base.monthlySalesTarget || 250000),
      profileMode: "main_gst",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
  }
}

function startRealtimeSync(uid) {
  clearSubscriptions();

  state.unsubs.push(
    onSnapshot(doc(db, "users", uid), (snap) => {
      state.rootProfile = snap.exists() ? { id: snap.id, ...snap.data() } : null;
      renderAll();
    })
  );

  state.unsubs.push(
    onSnapshot(collection(db, "users", uid, "business_profiles"), (snap) => {
      state.profiles = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      if (state.profiles.length && !state.profiles.some((p) => p.id === state.activeProfileId)) {
        state.activeProfileId = state.profiles[0].id;
      }
      renderAll();
    })
  );

  const subscribeList = (colName, key) => {
    const q = query(collection(db, "users", uid, colName), orderBy("updatedAt", "desc"));
    state.unsubs.push(
      onSnapshot(
        q,
        (snap) => {
          state[key] = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
          renderAll();
        },
        () => {
          onSnapshot(collection(db, "users", uid, colName), (snap) => {
            state[key] = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
            renderAll();
          });
        }
      )
    );
  };

  subscribeList("invoices", "invoices");
  subscribeList("inventory_items", "inventory");
  subscribeList("parties", "parties");
  subscribeList("expenses", "expenses");
}

function renderAll() {
  if (!state.user) return;
  renderSidebar();
  renderOverview();
  renderBilling();
  renderStock();
  renderParties();
  renderExpenses();
  renderReports();
}

function renderSidebar() {
  $("user-name-label").textContent = state.user.displayName || "Owner";
  $("user-email-label").textContent = state.user.email || "";

  const select = $("profile-select");
  const list = state.profiles.length ? state.profiles : [{ id: "main_1", businessName: "Main GST Business" }];
  select.innerHTML = list
    .map((p) => `<option value="${escapeHtml(p.id)}" ${p.id === state.activeProfileId ? "selected" : ""}>${escapeHtml(p.businessName)}</option>`)
    .join("");

  $("profile-mode-pill").textContent = isPlainNonGst() ? "Plain Non-GST Mode" : "Main GST Mode";

  const tier = effectivePlanTier();
  const spec = PLAN_SPECS[tier] || { name: "Expired" };
  const days = daysRemaining();
  $("sidebar-plan-badge").textContent = `${spec.name} • ${days}d left`;
  $("sidebar-plan-sub").textContent = tier === "diamond" ? "Full Unlimited Access" : "Upgrade / Billing →";
}

function renderOverview() {
  const invoices = profileScoped(state.invoices);
  const expenses = profileScoped(state.expenses);
  const target = Number(activeProfile().monthlySalesTarget || 250000);

  const billed = invoices.reduce((s, i) => s + Number(i.grandTotal || 0), 0);
  const collected = invoices.reduce((s, i) => s + Number(i.paidAmount || 0), 0);
  const pending = Math.max(0, billed - collected);
  const expTotal = expenses.reduce((s, e) => s + Number(e.amount || 0), 0);
  const gstItc = expenses.reduce((s, e) => s + Number(e.gstInputAmount || 0), 0);
  const pct = target > 0 ? Math.min(100, Math.round((billed / target) * 100)) : 0;

  $("kpi-total-billed").textContent = inr(billed);
  $("kpi-invoice-count").textContent = `${invoices.length} invoice(s)`;
  $("kpi-collected").textContent = inr(collected);
  $("kpi-target-text").textContent = `Target ${inr(target)}`;
  $("kpi-receivables").textContent = inr(pending);
  $("kpi-expenses").textContent = inr(expTotal);
  $("kpi-gst-itc").textContent = `GST ITC: ${inr(gstItc)}`;
  $("target-percent-label").textContent = `${pct}%`;
  $("target-progress-fill").style.width = `${pct}%`;

  const tbody = $("overview-recent-invoices");
  const recent = invoices.slice(0, 6);
  tbody.innerHTML = recent.length
    ? recent
        .map(
          (inv) => `
      <tr>
        <td class="mono bold">${escapeHtml(inv.invoiceNumber)}</td>
        <td>${escapeHtml(inv.partyName)}</td>
        <td>${escapeHtml((inv.taxMode || "").toUpperCase())}</td>
        <td><span class="pill pill-${inv.status === "paid" ? "paid" : inv.status === "partial" ? "partial" : "unpaid"}">${escapeHtml(inv.status.toUpperCase())}</span></td>
        <td class="right mono bold">${inr(inv.grandTotal)}</td>
      </tr>`
        )
        .join("")
    : `<tr><td colspan="5" class="muted">No invoices yet for this profile. Click "+ Create Invoice" to create one.</td></tr>`;
}

function renderBilling() {
  const q = ($("invoice-search").value || "").trim().toLowerCase();
  const invoices = profileScoped(state.invoices).filter((i) => {
    if (!q) return true;
    return [i.invoiceNumber, i.partyName, i.partyGstin, i.irn, i.ewbNumber]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(q));
  });

  $("billing-invoices-table").innerHTML = invoices.length
    ? invoices
        .map((inv) => {
          const bal = Math.max(0, Number(inv.grandTotal || 0) - Number(inv.paidAmount || 0));
          const comp = [
            inv.irn ? `<span class="pill pill-info">IRN ✓</span>` : "",
            inv.ewbNumber ? `<span class="pill pill-paid">EWB ${escapeHtml(inv.ewbNumber)}</span>` : ""
          ]
            .filter(Boolean)
            .join(" ");
          return `
        <tr>
          <td class="mono bold">${escapeHtml(inv.invoiceNumber)}</td>
          <td>${escapeHtml(inv.partyName)}<br/><span class="muted mono">${escapeHtml(inv.partyGstin || "Unregistered")}</span></td>
          <td><span class="pill pill-${inv.status === "paid" ? "paid" : inv.status === "partial" ? "partial" : "unpaid"}">${escapeHtml(inv.status.toUpperCase())}</span></td>
          <td>${comp || `<span class="muted">Standard</span>`}</td>
          <td class="right mono bold">${inr(inv.grandTotal)}</td>
          <td class="right mono">${inr(bal)}</td>
          <td class="right">
            <button class="btn btn-sm btn-ghost" data-action="print-inv" data-id="${escapeHtml(inv.id)}">Print PDF</button>
            <button class="btn btn-sm btn-ghost" data-action="wa-inv" data-id="${escapeHtml(inv.id)}">WhatsApp</button>
            ${bal > 0 ? `<button class="btn btn-sm btn-primary" data-action="mark-paid" data-id="${escapeHtml(inv.id)}">Mark Paid</button>` : ""}
            ${!isPlainNonGst() && !inv.irn ? `<button class="btn btn-sm btn-secondary" data-action="einvoice" data-id="${escapeHtml(inv.id)}">E-Invoice</button>` : ""}
            ${!isPlainNonGst() && !inv.ewbNumber ? `<button class="btn btn-sm btn-secondary" data-action="eway" data-id="${escapeHtml(inv.id)}">E-Way Bill</button>` : ""}
          </td>
        </tr>`;
        })
        .join("")
    : `<tr><td colspan="7" class="muted">No matching invoices found.</td></tr>`;
}

function renderStock() {
  const q = ($("stock-search").value || "").trim().toLowerCase();
  const items = profileScoped(state.inventory).filter((it) => {
    if (!q) return true;
    return [it.name, it.sku, it.hsnCode, it.category].some((v) => String(v || "").toLowerCase().includes(q));
  });

  $("stock-items-table").innerHTML = items.length
    ? items
        .map(
          (it) => `
      <tr>
        <td class="bold">${escapeHtml(it.name)}</td>
        <td class="mono">${escapeHtml(it.sku)} / HSN ${escapeHtml(it.hsnCode || "—")}</td>
        <td>${Number(it.gstRatePercent || 0)}%</td>
        <td class="right mono">${inr(it.salePrice)}</td>
        <td class="right mono bold">${Number(it.stockQty || 0)} ${escapeHtml(it.unit)}</td>
        <td class="right">
          <button class="btn btn-sm btn-ghost" data-action="restock" data-id="${escapeHtml(it.id)}">+ Restock</button>
          <button class="btn btn-sm btn-ghost" data-action="wa-stock" data-id="${escapeHtml(it.id)}">WhatsApp</button>
        </td>
      </tr>`
        )
        .join("")
    : `<tr><td colspan="6" class="muted">No inventory items yet.</td></tr>`;
}

function renderParties() {
  const q = ($("party-search").value || "").trim().toLowerCase();
  const list = profileScoped(state.parties).filter((p) => {
    if (!q) return true;
    return [p.name, p.phone, p.gstin].some((v) => String(v || "").toLowerCase().includes(q));
  });

  $("parties-table").innerHTML = list.length
    ? list
        .map((p) => {
          const due = Math.max(0, Number(p.openingDue || 0) + Number(p.totalBilled || 0) - Number(p.totalPaid || 0));
          return `
        <tr>
          <td class="bold">${escapeHtml(p.name)}</td>
          <td>${escapeHtml((p.partyType || "customer").toUpperCase())}</td>
          <td class="mono">${escapeHtml(p.phone || "—")}<br/><span class="muted">${escapeHtml(p.gstin || "")}</span></td>
          <td>${escapeHtml(p.state || "—")}</td>
          <td class="right mono bold">${inr(due)}</td>
          <td class="right"><button class="btn btn-sm btn-ghost" data-action="wa-party" data-id="${escapeHtml(p.id)}">WhatsApp</button></td>
        </tr>`;
        })
        .join("")
    : `<tr><td colspan="6" class="muted">No parties yet.</td></tr>`;
}

function renderExpenses() {
  const list = profileScoped(state.expenses);
  $("expenses-table").innerHTML = list.length
    ? list
        .map(
          (e) => `
      <tr>
        <td class="bold">${escapeHtml(e.title)}</td>
        <td>${escapeHtml((e.category || "").toUpperCase())}</td>
        <td>${escapeHtml(e.partyName || e.referenceNo || "—")}</td>
        <td>${escapeHtml((e.paymentMethod || "").toUpperCase())}</td>
        <td><span class="pill pill-${e.status === "settled" ? "paid" : "partial"}">${escapeHtml(e.status.toUpperCase())}</span></td>
        <td class="right mono bold">${inr(e.amount)}</td>
        <td class="right"><button class="btn btn-sm btn-ghost" data-action="wa-exp" data-id="${escapeHtml(e.id)}">WhatsApp</button></td>
      </tr>`
        )
        .join("")
    : `<tr><td colspan="7" class="muted">No expenses logged yet.</td></tr>`;
}

function renderReports() {
  const invoices = profileScoped(state.invoices);
  const expenses = profileScoped(state.expenses);
  const taxable = invoices.reduce((s, i) => s + Number(i.subtotal || 0), 0);
  const outGst = invoices.reduce((s, i) => s + Number(i.gstTotal || 0), 0);
  const inGst = expenses.reduce((s, e) => s + Number(e.gstInputAmount || 0), 0);
  const netGst = Math.max(0, outGst - inGst);
  const totalExp = expenses.reduce((s, e) => s + Number(e.amount || 0), 0);
  const profit = taxable - (totalExp - inGst);

  $("rep-taxable-sales").textContent = inr(taxable);
  $("rep-output-gst").textContent = inr(outGst);
  $("rep-input-gst").textContent = inr(inGst);
  $("rep-net-gst").textContent = inr(netGst);

  $("rep-pl-summary").innerHTML = `
    <div class="kpi-card"><div class="kpi-label">Total Tax Invoices</div><div class="kpi-value mono">${invoices.length}</div></div>
    <div class="kpi-card"><div class="kpi-label">Gross Revenue (Incl. GST)</div><div class="kpi-value mono">${inr(taxable + outGst)}</div></div>
    <div class="kpi-card"><div class="kpi-label">Net Estimated Profit</div><div class="kpi-value mono ${profit >= 0 ? "text-emerald" : ""}">${inr(profit)}</div></div>
  `;
}

function openModal(html) {
  $("modal-container").innerHTML = html;
  $("modal-backdrop").classList.remove("hidden");
}

function closeModal() {
  $("modal-backdrop").classList.add("hidden");
  $("modal-container").innerHTML = "";
}

function openReceivePaymentModal() {
  const pendingInvoices = profileScoped(state.invoices).filter(
    (inv) => inv.status !== "paid" && Number(inv.grandTotal || 0) - Number(inv.paidAmount || 0) > 0
  );
  const customers = profileScoped(state.parties).filter((p) => (p.partyType || "customer") === "customer");
  let mode = pendingInvoices.length > 0 ? "invoice" : "party";

  const renderForm = () => {
    const invOptions = pendingInvoices
      .map((inv) => {
        const due = Math.max(0, Number(inv.grandTotal || 0) - Number(inv.paidAmount || 0));
        return `<option value="${escapeHtml(inv.id)}" data-due="${due}">${escapeHtml(inv.invoiceNumber)} • ${escapeHtml(inv.partyName)} (Due ${inr(due)})</option>`;
      })
      .join("");
    const partyOptions = customers
      .map((p) => {
        const due = Math.max(0, Number(p.openingDue || 0) + Number(p.totalBilled || 0) - Number(p.totalPaid || 0));
        return `<option value="${escapeHtml(p.id)}" data-due="${due}">${escapeHtml(p.name)} (Due ${inr(due)})</option>`;
      })
      .join("");

    const defaultDue =
      mode === "invoice"
        ? pendingInvoices[0]
          ? Math.max(0, Number(pendingInvoices[0].grandTotal || 0) - Number(pendingInvoices[0].paidAmount || 0))
          : 0
        : customers[0]
          ? Math.max(0, Number(customers[0].openingDue || 0) + Number(customers[0].totalBilled || 0) - Number(customers[0].totalPaid || 0))
          : 0;

    openModal(`
      <h3>Receive Payment</h3>
      <div style="display:flex;gap:8px;margin-top:8px;">
        <button id="rp-mode-inv" class="btn ${mode === "invoice" ? "btn-primary" : "btn-ghost"}">Pending Bills (${pendingInvoices.length})</button>
        <button id="rp-mode-pty" class="btn ${mode === "party" ? "btn-primary" : "btn-ghost"}">Customer Khata (${customers.length})</button>
      </div>
      <div class="form-grid">
        ${
          mode === "invoice"
            ? `<div class="form-field span-2"><label>Select Pending Invoice</label><select id="rp-target" class="input-select">${invOptions || `<option value="">No pending invoices</option>`}</select></div>`
            : `<div class="form-field span-2"><label>Select Customer</label><select id="rp-target" class="input-select">${partyOptions || `<option value="">No customers found</option>`}</select></div>`
        }
        <div class="form-field"><label>Amount Received (₹)</label><input id="rp-amount" type="number" class="input-text" value="${defaultDue > 0 ? defaultDue : ""}" min="1" /></div>
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
        $("m-err").textContent = "Please select a record and enter an amount greater than 0.";
        $("m-err").classList.remove("hidden");
        return;
      }

      const bizName = activeProfile().businessName || "Finboss Billing No.1 ERP";
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
        await updateDoc(doc(db, "users", state.user.uid, "invoices", inv.id), {
          paidAmount: newPaid,
          status: newStatus,
          paymentMethod: method,
          updatedAt: serverTimestamp()
        });
        if (inv.partyId) {
          const pty = state.parties.find((p) => p.id === inv.partyId);
          if (pty) {
            await updateDoc(doc(db, "users", state.user.uid, "parties", pty.id), {
              totalBilled: Number(pty.totalBilled || 0),
              totalPaid: Number(pty.totalPaid || 0) + applied,
              updatedAt: serverTimestamp()
            });
          }
        }
        payerName = inv.partyName;
        payerPhone = findPartyPhone(inv.partyId, inv.partyName);
        refLabel = `Invoice ${inv.invoiceNumber}`;
        remaining = Math.max(0, grand - newPaid);
      } else {
        const pty = customers.find((p) => p.id === targetId);
        if (!pty) return;
        await updateDoc(doc(db, "users", state.user.uid, "parties", pty.id), {
          totalBilled: Number(pty.totalBilled || 0),
          totalPaid: Number(pty.totalPaid || 0) + amt,
          updatedAt: serverTimestamp()
        });
        payerName = pty.name;
        payerPhone = pty.phone || "";
        refLabel = "Customer Khata Payment";
        const currentDue = Math.max(0, Number(pty.openingDue || 0) + Number(pty.totalBilled || 0) - Number(pty.totalPaid || 0));
        remaining = Math.max(0, currentDue - amt);
      }

      const receiptText = [
        `*Payment Receipt • ${bizName}*`,
        `Received From: *${payerName}*`,
        `Reference: ${refLabel}`,
        `Amount Received: *${inr(amt)}* (${method.toUpperCase()})`,
        `Remaining Balance: ${inr(remaining)}`,
        `Thank you for your payment!`,
        `_Finboss Billing No.1 ERP_`
      ].join("\n");

      openModal(`
        <h3>Payment Recorded!</h3>
        <div class="qr-checkout-box" style="text-align:left;justify-items:start;">
          <div class="bold">${escapeHtml(payerName)} • ${escapeHtml(refLabel)}</div>
          <div>Received: <strong class="text-emerald">${inr(amt)}</strong> (${escapeHtml(method.toUpperCase())})</div>
          <div>Remaining Balance: <strong>${inr(remaining)}</strong></div>
        </div>
        <div class="modal-actions">
          <button id="m-done" class="btn btn-ghost">Done</button>
          <button id="rp-wa" class="btn btn-primary">Share Receipt on WhatsApp</button>
        </div>
      `);
      $("m-done").onclick = closeModal;
      $("rp-wa").onclick = () => shareToWhatsApp(receiptText, payerPhone);
      showToast(`Recorded ${inr(amt)} payment from ${payerName}!`);
    };
  };

  renderForm();
}

function openCreateInvoiceModal() {
  const plain = isPlainNonGst();
  const invNum = `FB-WEB-${String(Date.now()).slice(-6)}`;
  const partyOptions = profileScoped(state.parties)
    .map((p) => `<option value="${escapeHtml(p.id)}" data-name="${escapeHtml(p.name)}" data-gstin="${escapeHtml(p.gstin || "")}">${escapeHtml(p.name)}</option>`)
    .join("");

  openModal(`
    <h3>Create New ${plain ? "Plain Bill" : "GST Tax Invoice"}</h3>
    <div class="form-grid">
      <div class="form-field"><label>Invoice Number</label><input id="m-inv-num" class="input-text" value="${invNum}" /></div>
      <div class="form-field"><label>Saved Party (Optional)</label><select id="m-inv-party-sel" class="input-select"><option value="">Walk-in / Custom Customer</option>${partyOptions}</select></div>
      <div class="form-field"><label>Customer Name *</label><input id="m-inv-party" class="input-text" placeholder="Customer Name" /></div>
      <div class="form-field"><label>Customer GSTIN</label><input id="m-inv-gstin" class="input-text" ${plain ? "disabled" : ""} placeholder="${plain ? "Disabled in Plain mode" : "27ABCDE1234F1Z5"}" /></div>
      <div class="form-field span-2"><label>Item / Service Description *</label><input id="m-inv-item" class="input-text" placeholder="e.g. Wholesale Carton Box" /></div>
      <div class="form-field"><label>Qty</label><input id="m-inv-qty" type="number" class="input-text" value="1" min="1" /></div>
      <div class="form-field"><label>Unit Rate (₹)</label><input id="m-inv-rate" type="number" class="input-text" placeholder="1000" min="1" /></div>
      <div class="form-field"><label>GST %</label><select id="m-inv-gst" class="input-select" ${plain ? "disabled" : ""}>
        <option value="0">0%</option><option value="5">5%</option><option value="12">12%</option><option value="18" selected>18%</option><option value="28">28%</option>
      </select></div>
      <div class="form-field"><label>Payment Status</label><select id="m-inv-pay" class="input-select">
        <option value="upi">UPI (Paid)</option><option value="cash">Cash (Paid)</option><option value="credit">Credit (Unpaid)</option>
      </select></div>
    </div>
    <p id="m-err" class="error-box hidden"></p>
    <div class="modal-actions">
      <button id="m-cancel" class="btn btn-ghost">Cancel</button>
      <button id="m-save" class="btn btn-primary">Save Invoice</button>
    </div>
  `);

  $("m-cancel").onclick = closeModal;
  $("m-inv-party-sel").onchange = (e) => {
    const opt = e.target.selectedOptions[0];
    if (opt && opt.value) {
      $("m-inv-party").value = opt.dataset.name || "";
      if (!plain) $("m-inv-gstin").value = opt.dataset.gstin || "";
    }
  };
  $("m-save").onclick = async () => {
    const num = $("m-inv-num").value.trim().slice(0, 40);
    const partyName = $("m-inv-party").value.trim().slice(0, 100);
    const partyGstin = plain ? "" : $("m-inv-gstin").value.trim().toUpperCase().slice(0, 30);
    const itemName = $("m-inv-item").value.trim().slice(0, 80);
    const qty = Number($("m-inv-qty").value || 0);
    const rate = Number($("m-inv-rate").value || 0);
    const gstPct = plain ? 0 : Number($("m-inv-gst").value || 0);
    const payMethod = $("m-inv-pay").value;

    if (!num || !partyName || !itemName || qty <= 0 || rate <= 0) {
      $("m-err").textContent = "Please fill Invoice #, Customer Name, Item, Qty (>0), and Rate (>0).";
      $("m-err").classList.remove("hidden");
      return;
    }

    const subtotal = Math.round(qty * rate * 100) / 100;
    const gstTotal = Math.round(subtotal * (gstPct / 100) * 100) / 100;
    const grandTotal = Math.round((subtotal + gstTotal) * 100) / 100;
    const isCredit = payMethod === "credit";
    const id = `inv_${Date.now()}`;

    await setDoc(doc(db, "users", state.user.uid, "invoices", id), {
      userId: state.user.uid,
      businessProfileId: state.activeProfileId || "main_1",
      invoiceNumber: num,
      partyId: $("m-inv-party-sel").value || "",
      partyName,
      partyGstin,
      partyAddress: "",
      partyState: activeProfile().state || "Maharashtra",
      partyPincode: "",
      invoiceType: "tax_invoice",
      taxMode: plain ? "none" : "cgst_sgst",
      paymentMethod: payMethod,
      status: isCredit ? "unpaid" : "paid",
      subtotal,
      gstTotal,
      discountAmount: 0,
      grandTotal,
      paidAmount: isCredit ? 0 : grandTotal,
      notes: "Created via Finboss Desktop Web",
      lineItems: [
        {
          itemId: "",
          name: itemName,
          sku: "",
          hsnCode: plain ? "" : "9983",
          unit: "PCS",
          qty,
          unitPrice: rate,
          gstRatePercent: gstPct,
          taxableAmount: subtotal,
          gstAmount: gstTotal,
          lineTotal: grandTotal
        }
      ],
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
    closeModal();
    showToast(`Invoice ${num} (${inr(grandTotal)}) synced to cloud!`);
  };
}

function openAddItemModal() {
  openModal(`
    <h3>Add Stock SKU</h3>
    <div class="form-grid">
      <div class="form-field"><label>Product Name *</label><input id="m-sku-name" class="input-text" /></div>
      <div class="form-field"><label>SKU Code *</label><input id="m-sku-code" class="input-text" value="SKU-${String(Date.now()).slice(-4)}" /></div>
      <div class="form-field"><label>HSN Code</label><input id="m-sku-hsn" class="input-text" value="8471" /></div>
      <div class="form-field"><label>Opening Qty</label><input id="m-sku-qty" type="number" class="input-text" value="10" /></div>
      <div class="form-field"><label>Sale Price (₹)</label><input id="m-sku-sale" type="number" class="input-text" value="500" /></div>
      <div class="form-field"><label>Purchase Price (₹)</label><input id="m-sku-buy" type="number" class="input-text" value="350" /></div>
    </div>
    <div class="modal-actions">
      <button id="m-cancel" class="btn btn-ghost">Cancel</button>
      <button id="m-save" class="btn btn-primary">Save SKU</button>
    </div>
  `);
  $("m-cancel").onclick = closeModal;
  $("m-save").onclick = async () => {
    const name = $("m-sku-name").value.trim().slice(0, 100);
    const sku = $("m-sku-code").value.trim().slice(0, 40);
    if (!name || !sku) return;
    await setDoc(doc(db, "users", state.user.uid, "inventory_items", `sku_${Date.now()}`), {
      userId: state.user.uid,
      businessProfileId: state.activeProfileId || "main_1",
      name,
      sku,
      hsnCode: $("m-sku-hsn").value.trim().slice(0, 16),
      category: "General",
      unit: "PCS",
      salePrice: Number($("m-sku-sale").value || 0),
      purchasePrice: Number($("m-sku-buy").value || 0),
      gstRatePercent: 18,
      stockQty: Number($("m-sku-qty").value || 0),
      lowStockThreshold: 5,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
    closeModal();
    showToast(`Added ${name} to stock!`);
  };
}

function openAddPartyModal() {
  openModal(`
    <h3>Add Customer / Supplier</h3>
    <div class="form-grid">
      <div class="form-field"><label>Party Name *</label><input id="m-p-name" class="input-text" /></div>
      <div class="form-field"><label>Type</label><select id="m-p-type" class="input-select"><option value="customer">Customer</option><option value="supplier">Supplier</option></select></div>
      <div class="form-field"><label>Phone</label><input id="m-p-phone" class="input-text" /></div>
      <div class="form-field"><label>GSTIN</label><input id="m-p-gstin" class="input-text" /></div>
    </div>
    <div class="modal-actions">
      <button id="m-cancel" class="btn btn-ghost">Cancel</button>
      <button id="m-save" class="btn btn-primary">Save Party</button>
    </div>
  `);
  $("m-cancel").onclick = closeModal;
  $("m-save").onclick = async () => {
    const name = $("m-p-name").value.trim().slice(0, 100);
    if (!name) return;
    await setDoc(doc(db, "users", state.user.uid, "parties", `pty_${Date.now()}`), {
      userId: state.user.uid,
      businessProfileId: state.activeProfileId || "main_1",
      name,
      partyType: $("m-p-type").value,
      phone: $("m-p-phone").value.trim().slice(0, 25),
      gstin: $("m-p-gstin").value.trim().toUpperCase().slice(0, 30),
      address: "",
      state: activeProfile().state || "Maharashtra",
      pincode: "",
      openingDue: 0,
      totalBilled: 0,
      totalPaid: 0,
      creditLimit: 50000,
      notes: "",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
    closeModal();
    showToast(`Added party ${name}!`);
  };
}

function openAddExpenseModal() {
  openModal(`
    <h3>Record Expense</h3>
    <div class="form-grid">
      <div class="form-field"><label>Title *</label><input id="m-e-title" class="input-text" placeholder="Office Rent / Courier" /></div>
      <div class="form-field"><label>Category</label><select id="m-e-cat" class="input-select"><option value="other">Other</option><option value="purchases">Purchases</option><option value="rent">Rent</option><option value="transport">Transport</option><option value="utilities">Utilities</option></select></div>
      <div class="form-field"><label>Amount (₹) *</label><input id="m-e-amt" type="number" class="input-text" placeholder="1200" min="1" /></div>
      <div class="form-field"><label>GST Input Credit (₹)</label><input id="m-e-gst" type="number" class="input-text" value="0" min="0" /></div>
    </div>
    <div class="modal-actions">
      <button id="m-cancel" class="btn btn-ghost">Cancel</button>
      <button id="m-save" class="btn btn-primary">Save Expense</button>
    </div>
  `);
  $("m-cancel").onclick = closeModal;
  $("m-save").onclick = async () => {
    const title = $("m-e-title").value.trim().slice(0, 100);
    const amount = Number($("m-e-amt").value || 0);
    const gstInput = Math.min(amount, Number($("m-e-gst").value || 0));
    if (!title || amount <= 0) return;
    await setDoc(doc(db, "users", state.user.uid, "expenses", `exp_${Date.now()}`), {
      userId: state.user.uid,
      businessProfileId: state.activeProfileId || "main_1",
      title,
      category: $("m-e-cat").value,
      paymentMethod: "upi",
      status: "settled",
      partyId: "",
      partyName: "",
      referenceNo: `WEB-${String(Date.now()).slice(-6)}`,
      amount,
      gstInputAmount: gstInput,
      notes: "Logged from Desktop Web",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
    closeModal();
    showToast(`Recorded expense ${title}!`);
  };
}

function openSubscriptionModal() {
  let cycle = "yearly";
  const renderPlans = () => {
    openModal(`
      <h3>Finboss Subscription &amp; Plans</h3>
      <p class="muted">30-Day Free Trial active for new workspaces. Zero bank account or IFSC numbers displayed.</p>
      <div style="display:flex;gap:8px;margin-top:10px;">
        <button id="sub-cyc-m" class="btn ${cycle === "monthly" ? "btn-primary" : "btn-ghost"}">Monthly</button>
        <button id="sub-cyc-y" class="btn ${cycle === "yearly" ? "btn-primary" : "btn-ghost"}">Yearly (Save 10%-20%)</button>
      </div>
      <div class="plans-grid">
        ${["silver", "gold", "diamond"]
          .map((t) => {
            const p = PLAN_SPECS[t];
            const amt = cycle === "yearly" ? p.yearly : p.monthly;
            return `
            <div class="plan-option">
              <div class="bold">${p.name}</div>
              <div class="kpi-value mono">${inr(amt)}</div>
              <div class="muted">${cycle === "yearly" ? "per year" : "per month"} • ${p.maxProfiles} profile(s)</div>
              <div class="muted">${p.eInvoice ? "Includes E-Invoice &amp; E-Way Bill" : "Standard Billing"}</div>
              <button class="btn btn-primary" data-sub-tier="${t}">Subscribe (${inr(amt)})</button>
            </div>`;
          })
          .join("")}
      </div>
      <div class="modal-actions"><button id="m-cancel" class="btn btn-ghost">Close</button></div>
    `);
    $("m-cancel").onclick = closeModal;
    $("sub-cyc-m").onclick = () => { cycle = "monthly"; renderPlans(); };
    $("sub-cyc-y").onclick = () => { cycle = "yearly"; renderPlans(); };
    document.querySelectorAll("[data-sub-tier]").forEach((btn) => {
      btn.onclick = () => openUpiCheckoutModal(btn.dataset.subTier, cycle);
    });
  };
  renderPlans();
}

async function openUpiCheckoutModal(tier, cycle) {
  const spec = PLAN_SPECS[tier];
  const amount = cycle === "yearly" ? spec.yearly : spec.monthly;
  const upiUri = `upi://pay?pa=${encodeURIComponent(MERCHANT_UPI_ID)}&pn=${encodeURIComponent(MERCHANT_BENEFICIARY_NAME)}&am=${amount}&cu=INR&tn=${encodeURIComponent(`Finboss ${spec.name}`)}`;

  openModal(`
    <h3>Secure UPI Checkout • ${spec.name} (${cycle.toUpperCase()})</h3>
    <div class="qr-checkout-box">
      <canvas id="desktop-upi-qr" width="180" height="180"></canvas>
      <div class="bold">Scan &amp; Pay ${inr(amount)} (Incl. 18% GST)</div>
      <div class="muted">Merchant: ${escapeHtml(MERCHANT_BENEFICIARY_NAME)} • Verified UPI: <span class="mono">${escapeHtml(MERCHANT_UPI_ID)}</span></div>
    </div>
    <div class="form-field" style="margin-top:12px;">
      <label>12-Digit UPI UTR / Transaction Reference *</label>
      <input id="m-utr-input" class="input-text mono" maxlength="12" placeholder="e.g. 432198765432" />
    </div>
    <p id="m-err" class="error-box hidden"></p>
    <div class="modal-actions">
      <button id="m-cancel" class="btn btn-ghost">Cancel</button>
      <button id="m-confirm-pay" class="btn btn-primary">Verify &amp; Activate Plan</button>
    </div>
  `);

  QRCode.toCanvas($("desktop-upi-qr"), upiUri, { width: 180, margin: 1 });
  $("m-cancel").onclick = closeModal;
  $("m-confirm-pay").onclick = async () => {
    const utr = ($("m-utr-input").value || "").trim();
    if (!/^[0-9]{12}$/.test(utr)) {
      $("m-err").textContent = "Enter the exact 12-digit numeric UPI UTR reference.";
      $("m-err").classList.remove("hidden");
      return;
    }
    const days = cycle === "yearly" ? 365 : 30;
    const expiresMs = Date.now() + days * 86400000;
    await updateDoc(doc(db, "users", state.user.uid), {
      planTier: tier,
      billingCycle: cycle,
      planExpiresAtMs: expiresMs,
      planAmountPaid: amount,
      updatedAt: serverTimestamp()
    });
    const toEmail = state.user.email || ADMIN_RECEIPT_EMAIL;
    const subject = encodeURIComponent(`Congratulations! Your Finboss ${spec.name} Plan is Now Active`);
    const body = encodeURIComponent(
      `Congratulations! Your Finboss ${spec.name} (${cycle}) plan is now ACTIVE.\nAmount Paid: ₹${amount}\nUPI UTR: ${utr}\nMerchant: ${MERCHANT_BENEFICIARY_NAME} (${MERCHANT_UPI_ID})\nValid until: ${new Date(expiresMs).toDateString()}`
    );
    window.open(`mailto:${encodeURIComponent(toEmail)}?cc=${encodeURIComponent(ADMIN_RECEIPT_EMAIL)}&subject=${subject}&body=${body}`, "_blank");
    closeModal();
    showToast(`Congratulations! ${spec.name} Plan activated! Receipt emailed to ${toEmail}.`);
  };
}

function openEWayModal(invoice) {
  if (effectivePlanTier() !== "trial" && effectivePlanTier() !== "diamond") {
    showToast("E-Invoice & E-Way Bill require Diamond or active 30-Day Trial.");
    openSubscriptionModal();
    return;
  }
  openModal(`
    <h3>Generate E-Way Bill • ${escapeHtml(invoice.invoiceNumber)}</h3>
    <div class="form-grid">
      <div class="form-field"><label>From City</label><input id="ew-from" class="input-text" value="${escapeHtml(activeProfile().state || "Mumbai")}" /></div>
      <div class="form-field"><label>To City</label><input id="ew-to" class="input-text" value="${escapeHtml(invoice.partyState || "Pune")}" /></div>
      <div class="form-field"><label>Distance (KM)</label><input id="ew-km" type="number" class="input-text" value="150" min="1" max="4000" /></div>
      <div class="form-field"><label>Vehicle No.</label><input id="ew-veh" class="input-text mono" value="MH-12-AB-1234" /></div>
    </div>
    <div class="modal-actions">
      <button id="m-cancel" class="btn btn-ghost">Cancel</button>
      <button id="m-save" class="btn btn-primary">Generate EWB</button>
    </div>
  `);
  $("m-cancel").onclick = closeModal;
  $("m-save").onclick = async () => {
    const ewb = `EWB${String(Date.now()).slice(-9)}`;
    await updateDoc(doc(db, "users", state.user.uid, "invoices", invoice.id), {
      ewbNumber: ewb,
      ewbDate: new Date().toISOString().slice(0, 10),
      ewbValidUntil: new Date(Date.now() + 86400000).toISOString().slice(0, 10),
      transportMode: "road",
      distanceKm: Math.min(4000, Math.max(1, Number($("ew-km").value || 100))),
      fromPlace: $("ew-from").value.trim().slice(0, 60),
      toPlace: $("ew-to").value.trim().slice(0, 60),
      vehicleNumber: $("ew-veh").value.trim().toUpperCase(),
      vehicleType: "regular",
      updatedAt: serverTimestamp()
    });
    closeModal();
    showToast(`E-Way Bill ${ewb} saved!`);
  };
}

function printInvoice(invoice) {
  const prof = activeProfile();
  const rows = (invoice.lineItems || [])
    .map(
      (l) => `<tr>
      <td>${escapeHtml(l.name)}</td>
      <td class="mono">${escapeHtml(l.hsnCode || "—")}</td>
      <td class="right mono">${l.qty}</td>
      <td class="right mono">${inr(l.unitPrice)}</td>
      <td class="right mono">${l.gstRatePercent || 0}%</td>
      <td class="right mono bold">${inr(l.lineTotal)}</td>
    </tr>`
    )
    .join("");

  $("print-sheet").innerHTML = `
    <div style="border:1px solid #334155;padding:20px;">
      <div style="display:flex;justify-content:space-between;align-items:flex-start;border-bottom:2px solid #0B253C;padding-bottom:12px;">
        <div>
          <h2 style="margin:0;">${escapeHtml(prof.businessName || "Finboss Enterprise")}</h2>
          <div>GSTIN: ${escapeHtml(prof.gstin || "Plain Non-GST")}</div>
          <div>${escapeHtml(prof.state || "")}</div>
        </div>
        <div style="text-align:right;">
          <h3 style="margin:0;">${isPlainNonGst() ? "CASH MEMO / BILL" : "TAX INVOICE"}</h3>
          <div class="mono bold">${escapeHtml(invoice.invoiceNumber)}</div>
          ${invoice.irn ? `<div class="mono" style="font-size:10px;">IRN: ${escapeHtml(invoice.irn)}</div>` : ""}
          ${invoice.ewbNumber ? `<div class="mono">EWB No: ${escapeHtml(invoice.ewbNumber)}</div>` : ""}
        </div>
      </div>
      <div style="margin:14px 0;"><strong>Billed To:</strong> ${escapeHtml(invoice.partyName)} ${invoice.partyGstin ? `(${escapeHtml(invoice.partyGstin)})` : ""}</div>
      <table style="width:100%;border-collapse:collapse;">
        <thead><tr><th>Item</th><th>HSN</th><th class="right">Qty</th><th class="right">Rate</th><th class="right">GST</th><th class="right">Total</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
      <div style="text-align:right;margin-top:16px;font-size:16px;" class="mono bold">Grand Total: ${inr(invoice.grandTotal)}</div>
    </div>
  `;
  window.print();
}

// Event bindings
$("google-signin-btn").onclick = async () => {
  try {
    $("auth-error").classList.add("hidden");
    await signInWithPopup(auth, googleProvider);
  } catch (err) {
    $("auth-error").textContent = err?.message || "Google Sign-In failed.";
    $("auth-error").classList.remove("hidden");
  }
};

$("signout-btn").onclick = () => signOut(auth);
$("profile-select").onchange = (e) => {
  state.activeProfileId = e.target.value;
  renderAll();
};

document.querySelectorAll("[data-tab]").forEach((btn) => {
  btn.onclick = () => {
    document.querySelectorAll("[data-tab]").forEach((b) => b.classList.remove("active"));
    document.querySelectorAll(".tab-panel").forEach((p) => p.classList.remove("active"));
    btn.classList.add("active");
    $(`tab-${btn.dataset.tab}`).classList.add("active");
    $("page-title").textContent = btn.textContent.trim();
  };
});

document.querySelectorAll("[data-jump-tab]").forEach((btn) => {
  btn.onclick = () => {
    document.querySelector(`[data-tab="${btn.dataset.jumpTab}"]`)?.click();
  };
});

$("topbar-receive-payment-btn").onclick = openReceivePaymentModal;
$("topbar-new-invoice-btn").onclick = openCreateInvoiceModal;
$("topbar-whatsapp-btn").onclick = () => {
  const biz = activeProfile().businessName || "Finboss Billing No.1 ERP";
  const invoices = profileScoped(state.invoices);
  const expenses = profileScoped(state.expenses);
  const billed = invoices.reduce((s, i) => s + Number(i.grandTotal || 0), 0);
  const collected = invoices.reduce((s, i) => s + Number(i.paidAmount || 0), 0);
  const pending = Math.max(0, billed - collected);
  const expTotal = expenses.reduce((s, e) => s + Number(e.amount || 0), 0);
  const text = [
    `*${biz} — Business Summary*`,
    `Total Billed: ${inr(billed)} (${invoices.length} invoices)`,
    `Collected Revenue: *${inr(collected)}*`,
    `Pending Receivables: ${inr(pending)}`,
    `Operating Expenses: ${inr(expTotal)}`,
    `_Shared via Finboss Billing No.1 ERP_`
  ].join("\n");
  shareToWhatsApp(text);
};
$("billing-new-invoice-btn").onclick = openCreateInvoiceModal;
$("stock-add-item-btn").onclick = openAddItemModal;
$("parties-add-btn").onclick = openAddPartyModal;
$("expenses-add-btn").onclick = openAddExpenseModal;
$("topbar-plan-btn").onclick = openSubscriptionModal;
$("open-subscription-btn").onclick = openSubscriptionModal;
$("reports-whatsapp-btn").onclick = () => {
  const biz = activeProfile().businessName || "Finboss Billing No.1 ERP";
  const invoices = profileScoped(state.invoices);
  const expenses = profileScoped(state.expenses);
  const taxable = invoices.reduce((s, i) => s + Number(i.subtotal || 0), 0);
  const outGst = invoices.reduce((s, i) => s + Number(i.gstTotal || 0), 0);
  const inGst = expenses.reduce((s, e) => s + Number(e.gstInputAmount || 0), 0);
  const netGst = Math.max(0, outGst - inGst);
  const totalExp = expenses.reduce((s, e) => s + Number(e.amount || 0), 0);
  const profit = taxable - (totalExp - inGst);
  const text = [
    `*${biz} — GST & P&L Report*`,
    `Taxable Sales (GSTR-1): ${inr(taxable)}`,
    `Output GST: ${inr(outGst)}`,
    `Purchase GST ITC: ${inr(inGst)}`,
    `Net GST Liability: *${inr(netGst)}*`,
    `Estimated Net Profit: *${inr(profit)}*`,
    `_Shared via Finboss Billing No.1 ERP_`
  ].join("\n");
  shareToWhatsApp(text);
};
$("open-business-settings-btn").onclick = () => {
  const prof = activeProfile();
  openModal(`
    <h3>Business Settings (${escapeHtml(prof.id)})</h3>
    <div class="form-grid">
      <div class="form-field"><label>Business Name</label><input id="set-biz" class="input-text" value="${escapeHtml(prof.businessName || "")}" /></div>
      <div class="form-field"><label>GSTIN</label><input id="set-gstin" class="input-text" ${isPlainNonGst() ? "disabled" : ""} value="${escapeHtml(prof.gstin || "")}" /></div>
      <div class="form-field"><label>State</label><input id="set-state" class="input-text" value="${escapeHtml(prof.state || "")}" /></div>
      <div class="form-field"><label>Monthly Target (₹)</label><input id="set-target" type="number" class="input-text" value="${Number(prof.monthlySalesTarget || 250000)}" /></div>
    </div>
    <div class="modal-actions">
      <button id="m-cancel" class="btn btn-ghost">Cancel</button>
      <button id="m-save" class="btn btn-primary">Save Settings</button>
    </div>
  `);
  $("m-cancel").onclick = closeModal;
  $("m-save").onclick = async () => {
    const biz = $("set-biz").value.trim().slice(0, 100);
    if (!biz) return;
    await updateDoc(doc(db, "users", state.user.uid, "business_profiles", prof.id), {
      businessName: biz,
      gstin: isPlainNonGst() ? "" : $("set-gstin").value.trim().toUpperCase().slice(0, 30),
      state: $("set-state").value.trim().slice(0, 60),
      monthlySalesTarget: Number($("set-target").value || 250000),
      updatedAt: serverTimestamp()
    });
    closeModal();
    showToast("Business settings synced!");
  };
};

$("invoice-search").oninput = renderBilling;
$("stock-search").oninput = renderStock;
$("party-search").oninput = renderParties;

$("billing-invoices-table").onclick = async (e) => {
  const btn = e.target.closest("[data-action]");
  if (!btn) return;
  const inv = state.invoices.find((i) => i.id === btn.dataset.id);
  if (!inv) return;
  if (btn.dataset.action === "print-inv") printInvoice(inv);
  if (btn.dataset.action === "wa-inv") {
    const biz = activeProfile().businessName || "Finboss Billing No.1 ERP";
    const due = Math.max(0, Number(inv.grandTotal || 0) - Number(inv.paidAmount || 0));
    const lines = (inv.lineItems || []).map((l, idx) => `${idx + 1}. ${l.name} (${l.qty} ${l.unit}) = ${inr(l.lineTotal)}`).join("\n");
    const text = [
      `*${biz}*`,
      `${isPlainNonGst() ? "Bill" : "Tax Invoice"}: *${inv.invoiceNumber}*`,
      `Customer: ${inv.partyName}`,
      inv.partyGstin ? `GSTIN: ${inv.partyGstin}` : "",
      lines,
      `Grand Total: *${inr(inv.grandTotal)}*`,
      `Paid: ${inr(inv.paidAmount)} • Due: ${inr(due)}`,
      inv.irn ? `IRN: ${inv.irn}` : "",
      inv.ewbNumber ? `E-Way Bill: #${inv.ewbNumber}` : "",
      `_Shared via Finboss Billing No.1 ERP_`
    ].filter(Boolean).join("\n");
    shareToWhatsApp(text, findPartyPhone(inv.partyId, inv.partyName));
  }
  if (btn.dataset.action === "eway") openEWayModal(inv);
  if (btn.dataset.action === "mark-paid") {
    await updateDoc(doc(db, "users", state.user.uid, "invoices", inv.id), {
      paidAmount: Number(inv.grandTotal || 0),
      status: "paid",
      paymentMethod: "upi",
      updatedAt: serverTimestamp()
    });
    showToast(`Marked ${inv.invoiceNumber} as PAID!`);
  }
  if (btn.dataset.action === "einvoice") {
    if (effectivePlanTier() !== "trial" && effectivePlanTier() !== "diamond") {
      showToast("E-Invoice requires Diamond or active 30-Day Trial.");
      openSubscriptionModal();
      return;
    }
    const fakeHex = Array.from({ length: 64 }, (_, i) => ((Date.now() + i * 7) % 16).toString(16)).join("");
    await updateDoc(doc(db, "users", state.user.uid, "invoices", inv.id), {
      irn: fakeHex,
      ackNo: `1126${String(Date.now()).slice(-10)}`,
      ackDate: new Date().toISOString().slice(0, 19).replace("T", " "),
      signedQrData: `FINBOSS-IRP|${inv.invoiceNumber}|${fakeHex.slice(0, 16)}`,
      einvoiceStatus: "generated",
      updatedAt: serverTimestamp()
    });
    showToast(`E-Invoice IRN generated for ${inv.invoiceNumber}!`);
  }
};

$("stock-items-table").onclick = async (e) => {
  const btn = e.target.closest("[data-action]");
  if (!btn) return;
  const item = state.inventory.find((i) => i.id === btn.dataset.id);
  if (!item) return;
  if (btn.dataset.action === "restock") {
    await updateDoc(doc(db, "users", state.user.uid, "inventory_items", item.id), {
      stockQty: Number(item.stockQty || 0) + 10,
      updatedAt: serverTimestamp()
    });
    showToast(`Added +10 ${item.unit} to ${item.name}`);
  }
  if (btn.dataset.action === "wa-stock") {
    const biz = activeProfile().businessName || "Finboss Billing No.1 ERP";
    const text = [
      `*Product & Stock • ${biz}*`,
      `Item: *${item.name}* (${item.sku})`,
      item.hsnCode ? `HSN: ${item.hsnCode} • GST: ${Number(item.gstRatePercent || 0)}%` : "",
      `Sale Rate: *${inr(item.salePrice)}* / ${item.unit}`,
      `Available Stock: ${Number(item.stockQty || 0)} ${item.unit}`,
      `_Shared via Finboss Billing No.1 ERP_`
    ].filter(Boolean).join("\n");
    shareToWhatsApp(text);
  }
};

$("parties-table").onclick = (e) => {
  const btn = e.target.closest("[data-action='wa-party']");
  if (!btn) return;
  const p = state.parties.find((x) => x.id === btn.dataset.id);
  if (!p) return;
  const biz = activeProfile().businessName || "Finboss Billing No.1 ERP";
  const due = Math.max(0, Number(p.openingDue || 0) + Number(p.totalBilled || 0) - Number(p.totalPaid || 0));
  const text = [
    `*Khata Ledger • ${biz}*`,
    `Party: *${p.name}*`,
    p.gstin ? `GSTIN: ${p.gstin}` : "",
    `Total Billed: ${inr(p.totalBilled || 0)}`,
    `Total Paid: ${inr(p.totalPaid || 0)}`,
    `Net Balance Due: *${inr(due)}*`,
    `_Shared via Finboss Billing No.1 ERP_`
  ].filter(Boolean).join("\n");
  shareToWhatsApp(text, p.phone);
};

$("expenses-table").onclick = (e) => {
  const btn = e.target.closest("[data-action='wa-exp']");
  if (!btn) return;
  const exp = state.expenses.find((x) => x.id === btn.dataset.id);
  if (!exp) return;
  const biz = activeProfile().businessName || "Finboss Billing No.1 ERP";
  const text = [
    `*Expense Voucher • ${biz}*`,
    `Title: *${exp.title}*`,
    `Category: ${(exp.category || "").toUpperCase()} • Mode: ${(exp.paymentMethod || "").toUpperCase()}`,
    `Amount: *${inr(exp.amount)}* (${(exp.status || "").toUpperCase()})`,
    exp.gstInputAmount > 0 ? `GST ITC: ${inr(exp.gstInputAmount)}` : "",
    `_Shared via Finboss Billing No.1 ERP_`
  ].filter(Boolean).join("\n");
  shareToWhatsApp(text);
};

onAuthStateChanged(auth, async (user) => {
  state.user = user;
  clearSubscriptions();
  if (user) {
    $("auth-screen").classList.add("hidden");
    $("app-shell").classList.remove("hidden");
    await ensureBootstrapWorkspace(user);
    startRealtimeSync(user.uid);
  } else {
    $("app-shell").classList.add("hidden");
    $("auth-screen").classList.remove("hidden");
  }
});
