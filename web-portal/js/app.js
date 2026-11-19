import {
  onAuthStateChanged,
  signInWithPopup,
  signOut
} from "https://www.gstatic.com/firebasejs/11.14.0/firebase-auth.js";
import {
  collection,
  doc,
  getDoc,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  setDoc
} from "https://www.gstatic.com/firebasejs/11.14.0/firebase-firestore.js";
import { ADMIN_RECEIPT_EMAIL } from "./firebase-config.js";
import { auth, db, googleProvider, PLAN_SPECS, state } from "./state.js";
import {
  $,
  activeProfile,
  closeModal,
  daysRemaining,
  effectivePlanTier,
  escapeHtml,
  fmtPct,
  inr,
  isPlainNonGst,
  profileScoped,
  shareToWhatsApp,
  showToast
} from "./utils.js";
import {
  bindBillingTableEvents,
  openInvoiceModal,
  renderBilling
} from "./billing.js";
import {
  bindPartyTableEvents,
  computeLedgerAnalytics,
  openPartyModal,
  openQuickEntryModal,
  printLedgerStatement,
  renderParties,
  renderReports
} from "./ledger.js";
import {
  bindExpenseEvents,
  bindStockTableEvents,
  openExpenseModal,
  openItemModal,
  openPurchaseModal,
  openReceivePaymentModal,
  renderExpenses,
  renderStock
} from "./stock-expenses.js";
import {
  bindWorkspaceEvents,
  openBusinessProfileModal,
  openSubscriptionModal,
  renderWorkspace
} from "./workspace.js";
import { bindAiCopilotEvents, renderAiCopilot } from "./ai-copilot.js";
import { bindIncomeTaxSlabs } from "./income-tax-slabs.js";
import { bindItrWorkingPaper } from "./itr-working-paper.js";
import { bindGstFilingEvents, renderGstFiling } from "./gst-filing.js";

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

function updateSyncPill(snap) {
  const pill = $("sync-pill");
  if (!pill || !snap) return;
  if (snap.metadata?.hasPendingWrites) {
    pill.textContent = "↻ Syncing to Cloud...";
    pill.style.background = "#FEF3C7";
    pill.style.color = "#92400E";
  } else if (snap.metadata?.fromCache) {
    pill.textContent = "● Cached Offline Mode";
    pill.style.background = "#E0F2FE";
    pill.style.color = "#075985";
  } else {
    pill.textContent = "● Live Synced";
    pill.style.background = "#D1FAE5";
    pill.style.color = "#065F46";
  }
}

function startRealtimeSync(uid) {
  clearSubscriptions();

  state.unsubs.push(
    onSnapshot(doc(db, "users", uid), (snap) => {
      state.rootProfile = snap.exists() ? { id: snap.id, ...snap.data() } : null;
      updateSyncPill(snap);
      renderAll();
    })
  );

  state.unsubs.push(
    onSnapshot(collection(db, "users", uid, "business_profiles"), (snap) => {
      state.profiles = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      if (state.profiles.length && !state.profiles.some((p) => p.id === state.activeProfileId)) {
        state.activeProfileId = state.profiles[0].id;
        localStorage.setItem("book_web_active_profile", state.activeProfileId);
      }
      renderAll();
    })
  );

  const subscribeList = (colName, key, useOrderBy = true) => {
    const ref = collection(db, "users", uid, colName);
    const q = useOrderBy ? query(ref, orderBy("updatedAt", "desc")) : ref;
    state.unsubs.push(
      onSnapshot(
        q,
        { includeMetadataChanges: true },
        (snap) => {
          state[key] = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
          updateSyncPill(snap);
          renderAll();
        },
        () => {
          state.unsubs.push(
            onSnapshot(ref, (snap) => {
              state[key] = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
              renderAll();
            })
          );
        }
      )
    );
  };

  subscribeList("invoices", "invoices", true);
  subscribeList("inventory_items", "inventory", true);
  subscribeList("parties", "parties", true);
  subscribeList("expenses", "expenses", true);
  subscribeList("dashboard_entries", "dashboardEntries", true);
  subscribeList("salesmen", "salesmen", false);
}

function renderSidebar() {
  $("user-name-label").textContent = state.user.displayName || "Owner";
  $("user-email-label").textContent = state.user.email || "";

  const select = $("profile-select");
  const list = state.profiles.length ? state.profiles : [{ id: "main_1", businessName: "Main GST Business" }];
  select.innerHTML = list
    .map(
      (p) =>
        `<option value="${escapeHtml(p.id)}" ${p.id === state.activeProfileId ? "selected" : ""}>${escapeHtml(p.businessName)}</option>`
    )
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
  const quickEntries = profileScoped(state.dashboardEntries);
  const target = Number(activeProfile().monthlySalesTarget || 250000);

  const billed =
    invoices.reduce((s, i) => s + Number(i.grandTotal || 0), 0) +
    quickEntries.filter((q) => q.category === "sale").reduce((s, q) => s + Number(q.amount || 0), 0);
  const collected =
    invoices.reduce((s, i) => s + Number(i.paidAmount || 0), 0) +
    quickEntries.filter((q) => q.category === "sale" && q.status === "paid").reduce((s, q) => s + Number(q.amount || 0), 0);
  const pending = Math.max(0, billed - collected);
  const expTotal =
    expenses.reduce((s, e) => s + Number(e.amount || 0), 0) +
    quickEntries.filter((q) => q.category === "expense").reduce((s, q) => s + Number(q.amount || 0), 0);
  const gstItc = expenses.reduce((s, e) => s + Number(e.gstInputAmount || 0), 0);
  const pct = target > 0 ? Math.min(100, Math.round((billed / target) * 100)) : 0;

  $("kpi-total-billed").textContent = inr(billed);
  $("kpi-invoice-count").textContent = `${invoices.length} invoice(s) • ${quickEntries.length} quick`;
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
        <td><span class="pill pill-${inv.status === "paid" ? "paid" : inv.status === "partial" ? "partial" : "unpaid"}">${escapeHtml(String(inv.status || "unpaid").toUpperCase())}</span></td>
        <td class="right mono bold">${inr(inv.grandTotal)}</td>
      </tr>`
        )
        .join("")
    : `<tr><td colspan="5" class="muted">No invoices yet for this profile. Click "+ Create Invoice" to create one.</td></tr>`;
}

export function renderAll() {
  if (!state.user) return;
  renderSidebar();
  renderOverview();
  renderBilling();
  renderStock();
  renderParties();
  renderExpenses();
  renderReports();
  renderGstFiling();
  renderAiCopilot();
  renderWorkspace();
}

state.renderCallback = renderAll;

const TAB_TITLES = {
  overview: ["Executive Overview", "Real-time business performance across synced mobile & desktop devices"],
  billing: ["Billing & GST Invoices", "Create and print invoices, and open the official e-invoice and e-way bill portals"],
  stock: ["Inventory & Stock", "Manage SKUs, Purchase Stock-In (+GST ITC), and low-stock alerts"],
  parties: ["Parties & Ledger", "Customer/Supplier Party Ledger, Item / Stock Ledger & Period P&L % analytics"],
  expenses: ["Expenses & Cashbook", "Track operating bills, pending payables, GST ITC & Cashbook flow"],
  reports: ["Reports & Analytics", "Profit & Loss, GSTR-1 / 2B / 3B, Sales & Expense and Stock Valuation"],
  gst_filing: ["GST & Banking", "Open the official GST Portal, review working papers and access bank payments"],
  ai: ["Finboss AI Copilot", "Ask AI Business Analyst, Voice-to-Invoice/Expense & Bill Photo OCR"],
  workspace: ["Workspace & Settings", "Switch GST/Plain Profiles, Salesman OTP Seats, App Settings & Backups"]
};

function closeMobileSidebar() {
  $("app-sidebar")?.classList.remove("mobile-open");
  $("sidebar-backdrop")?.classList.add("hidden");
}

function updateSidebarActiveState(tabKey) {
  document.querySelectorAll(".nav-item").forEach((n) => n.classList.toggle("active", n.dataset.tab === tabKey));
  document.querySelectorAll(".nav-subitem").forEach((sub) => {
    let isMatch = sub.dataset.tab === tabKey;
    if (isMatch && sub.dataset.subLedger) isMatch = sub.dataset.subLedger === state.ledgerView;
    if (isMatch && sub.dataset.subReport) isMatch = sub.dataset.subReport === state.reportFocus;
    if (isMatch && sub.dataset.subAi) isMatch = sub.dataset.subAi === state.aiMode;
    sub.classList.toggle("active", isMatch);
  });
  document.querySelectorAll(".nav-group").forEach((grp) => {
    const hasActive = Boolean(grp.querySelector(".nav-subitem.active"));
    grp.classList.toggle("has-active", hasActive);
    if (hasActive) {
      grp.classList.add("open");
      const toggle = grp.querySelector(".nav-group-toggle");
      if (toggle) toggle.setAttribute("aria-expanded", "true");
    }
  });
}

function switchTab(tabKey) {
  document.querySelectorAll(".tab-panel").forEach((p) => p.classList.toggle("active", p.id === `tab-${tabKey}`));
  const [title, sub] = TAB_TITLES[tabKey] || TAB_TITLES.overview;
  $("page-title").textContent = title;
  $("page-subtitle").textContent = sub;
  updateSidebarActiveState(tabKey);
  closeMobileSidebar();
}

// --- Static UI Event Bindings ---
document.querySelectorAll(".nav-group-toggle").forEach((toggle) => {
  toggle.onclick = () => {
    const grp = toggle.closest(".nav-group");
    if (!grp) return;
    const isOpen = grp.classList.toggle("open");
    toggle.setAttribute("aria-expanded", isOpen ? "true" : "false");
  };
});

document.querySelectorAll(".nav-item").forEach((btn) => {
  btn.onclick = () => switchTab(btn.dataset.tab);
});

document.querySelectorAll(".nav-subitem").forEach((btn) => {
  btn.onclick = () => {
    const tab = btn.dataset.tab;
    if (btn.dataset.subLedger) {
      state.ledgerView = btn.dataset.subLedger;
      document.querySelectorAll("[data-ledger-view]").forEach((b) => b.classList.toggle("active", b.dataset.ledgerView === state.ledgerView));
      renderParties();
    }
    if (btn.dataset.subReport) {
      state.reportFocus = btn.dataset.subReport;
      renderReports();
    }
    if (btn.dataset.subAi) {
      state.aiMode = btn.dataset.subAi;
      renderAiCopilot();
    }
    switchTab(tab);
  };
});

if ($("mobile-menu-btn")) {
  $("mobile-menu-btn").onclick = () => {
    $("app-sidebar")?.classList.add("mobile-open");
    $("sidebar-backdrop")?.classList.remove("hidden");
  };
}
if ($("sidebar-backdrop")) {
  $("sidebar-backdrop").onclick = closeMobileSidebar;
}

document.querySelectorAll("[data-jump-tab]").forEach((btn) => {
  btn.onclick = () => switchTab(btn.dataset.jumpTab);
});

$("profile-select").onchange = (e) => {
  state.activeProfileId = e.target.value;
  localStorage.setItem("book_web_active_profile", state.activeProfileId);
  renderAll();
  showToast(`Switched to ${activeProfile().businessName}`);
};

$("invoice-search").oninput = renderBilling;
$("stock-search").oninput = renderStock;
$("party-search").oninput = renderParties;
$("expense-search").oninput = renderExpenses;

document.querySelectorAll("[data-ledger-period]").forEach((btn) => {
  btn.onclick = () => {
    state.ledgerPeriod = btn.dataset.ledgerPeriod;
    document.querySelectorAll("[data-ledger-period]").forEach((b) => b.classList.toggle("active", b.dataset.ledgerPeriod === state.ledgerPeriod));
    document.querySelectorAll("[data-report-period]").forEach((b) => b.classList.toggle("active", b.dataset.reportPeriod === state.ledgerPeriod));
    renderParties();
    renderReports();
  };
});

document.querySelectorAll("[data-report-period]").forEach((btn) => {
  btn.onclick = () => {
    state.ledgerPeriod = btn.dataset.reportPeriod;
    document.querySelectorAll("[data-report-period]").forEach((b) => b.classList.toggle("active", b.dataset.reportPeriod === state.ledgerPeriod));
    document.querySelectorAll("[data-ledger-period]").forEach((b) => b.classList.toggle("active", b.dataset.ledgerPeriod === state.ledgerPeriod));
    renderParties();
    renderReports();
  };
});

document.querySelectorAll("[data-report-focus]").forEach((btn) => {
  btn.onclick = () => {
    state.reportFocus = btn.dataset.reportFocus;
    renderReports();
    const activePanel = document.querySelector(".tab-panel.active")?.id?.replace("tab-", "") || "reports";
    updateSidebarActiveState(activePanel);
  };
});

document.querySelectorAll("[data-ledger-view]").forEach((btn) => {
  btn.onclick = () => {
    state.ledgerView = btn.dataset.ledgerView;
    document.querySelectorAll("[data-ledger-view]").forEach((b) => b.classList.toggle("active", b.dataset.ledgerView === state.ledgerView));
    renderParties();
    const activePanel = document.querySelector(".tab-panel.active")?.id?.replace("tab-", "") || "parties";
    updateSidebarActiveState(activePanel);
  };
});

$("ledger-print-btn").onclick = printLedgerStatement;
$("ledger-wa-btn").onclick = () => {
  const prof = activeProfile();
  const a = computeLedgerAnalytics(state.ledgerPeriod);
  const topParties = a.parties
    .filter((p) => p.hasSales)
    .slice(0, 5)
    .map((p) => `• ${p.name}: ${inr(p.profit)} (${fmtPct(p.pct)})`)
    .join("\n");
  const topItems = a.items
    .filter((it) => it.hasSold)
    .slice(0, 5)
    .map((it) => `• ${it.name}: ${inr(it.profit)} (${fmtPct(it.pct)})`)
    .join("\n");
  const text = [
    `*${prof.businessName || "Finboss Billing No.1 ERP"} — Ledger & P&L*`,
    `Period: ${a.rangeLabel}`,
    `Sales: *${inr(a.totalSales)}* | Stock Value: ${inr(a.stockValue)}`,
    `Net P&L: *${inr(a.netProfit)} (${fmtPct(a.netPct)})*`,
    topParties ? `\n*Top Parties:*\n${topParties}` : "",
    topItems ? `\n*Top Items:*\n${topItems}` : "",
    `\n_Shared via Finboss Billing No.1 ERP_`
  ]
    .filter(Boolean)
    .join("\n");
  shareToWhatsApp(text);
};

$("topbar-quick-entry-btn").onclick = openQuickEntryModal;
$("parties-quick-entry-btn").onclick = openQuickEntryModal;
$("topbar-receive-payment-btn").onclick = () => openReceivePaymentModal();
$("topbar-new-invoice-btn").onclick = () => openInvoiceModal(null);
$("billing-new-invoice-btn").onclick = () => openInvoiceModal(null);
$("stock-add-item-btn").onclick = () => openItemModal(null);
$("stock-purchase-btn").onclick = () => openPurchaseModal(null);
$("parties-add-btn").onclick = () => openPartyModal(null);
$("expenses-add-btn").onclick = () => openExpenseModal(null);
$("open-subscription-btn").onclick = openSubscriptionModal;
$("topbar-plan-btn").onclick = openSubscriptionModal;
$("open-business-settings-btn").onclick = () => openBusinessProfileModal(state.activeProfileId, false);

$("topbar-whatsapp-btn").onclick = () => {
  const invoices = profileScoped(state.invoices);
  const expenses = profileScoped(state.expenses);
  const billed = invoices.reduce((s, i) => s + Number(i.grandTotal || 0), 0);
  const collected = invoices.reduce((s, i) => s + Number(i.paidAmount || 0), 0);
  const pending = Math.max(0, billed - collected);
  const exp = expenses.reduce((s, e) => s + Number(e.amount || 0), 0);
  const msg = `*${activeProfile().businessName || "Finboss Billing No.1 ERP"} — Business Summary*\nTotal Sales: *${inr(billed)}* (${invoices.length} bills)\nCollected: ${inr(collected)}\nPending Receivables: ${inr(pending)}\nExpenses: ${inr(exp)}\n_Shared via Finboss Billing No.1 ERP Desktop Web_`;
  shareToWhatsApp(msg);
};

$("reports-print-btn").onclick = printLedgerStatement;
$("reports-whatsapp-btn").onclick = () => {
  const analytics = computeLedgerAnalytics(state.ledgerPeriod);
  const invoices = analytics.invoices;
  const expenses = analytics.expenses;
  const outGst = invoices.reduce((s, i) => s + Number(i.gstTotal || 0), 0);
  const inGst = expenses.reduce((s, e) => s + Number(e.gstInputAmount || 0), 0);
  const text = `*${activeProfile().businessName || "Finboss Billing No.1 ERP"} (${analytics.rangeLabel})*\nTaxable Sales: ${inr(invoices.reduce((s, i) => s + Number(i.subtotal || 0), 0))}\nOutput GST: ${inr(outGst)}\nInput ITC: ${inr(inGst)}\nNet GST Payable: ${inr(Math.max(0, outGst - inGst))}\nNet Profit/Loss: *${inr(analytics.netProfit)} (${fmtPct(analytics.netPct)})*\n_Shared via Finboss Billing No.1 ERP_`;
  shareToWhatsApp(text);
};

$("modal-backdrop").onclick = (e) => {
  if (e.target === $("modal-backdrop")) closeModal();
};

// Wire modular table/feature event handlers
bindBillingTableEvents();
bindStockTableEvents();
bindPartyTableEvents(openReceivePaymentModal);
bindExpenseEvents();
bindWorkspaceEvents();
bindAiCopilotEvents();
bindGstFilingEvents();
bindItrWorkingPaper();
bindIncomeTaxSlabs();

// Google Auth Controls
$("google-signin-btn").onclick = async () => {
  $("auth-error").classList.add("hidden");
  try {
    await signInWithPopup(auth, googleProvider);
  } catch (err) {
    $("auth-error").textContent = err?.message || "Google Sign-In failed. Add your localhost/domain to Firebase Authorized Domains.";
    $("auth-error").classList.remove("hidden");
  }
};

$("signout-btn").onclick = async () => {
  clearSubscriptions();
  await signOut(auth);
};

onAuthStateChanged(auth, async (user) => {
  state.user = user;
  if (!user) {
    clearSubscriptions();
    $("auth-screen").classList.remove("hidden");
    $("app-shell").classList.add("hidden");
    return;
  }
  $("auth-screen").classList.add("hidden");
  $("app-shell").classList.remove("hidden");
  await ensureBootstrapWorkspace(user);
  startRealtimeSync(user.uid);
});
