import {
  deleteDoc,
  doc,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch
} from "https://www.gstatic.com/firebasejs/11.14.0/firebase-firestore.js";
import {
  ADMIN_RECEIPT_EMAIL
} from "./firebase-config.js";
import {
  db,
  INDIA_STATES,
  PLAN_SPECS,
  PROFILE_SLOT_META,
  SALESMAN_PERMISSION_DEFS,
  SALESMAN_ROLE_PRESETS,
  SALESMAN_SEAT_IDS,
  state
} from "./state.js";
import {
  $,
  activePlanSpec,
  activeProfile,
  closeModal,
  effectivePlanTier,
  escapeHtml,
  inr,
  isPlainNonGst,
  loadAppSettings,
  normalizePhoneE164,
  openModal,
  saveAppSettings,
  showToast
} from "./utils.js";

export function renderWorkspace() {
  const tier = effectivePlanTier();
  const spec = activePlanSpec();
  const settings = loadAppSettings();

  // Business Profiles Grid
  const profileSlotsHtml = ["main_1", "main_2", "plain_1"]
    .map((slotId) => {
      const meta = PROFILE_SLOT_META[slotId];
      const prof = state.profiles.find((p) => p.id === slotId);
      const allowed = spec.allowedSlots.includes(slotId);
      const isActive = state.activeProfileId === slotId;
      return `
      <div class="workspace-slot-card ${isActive ? "slot-active" : ""}">
        <div style="display:flex;justify-content:space-between;align-items:center;gap:8px;">
          <span class="pill ${slotId === "plain_1" ? "pill-partial" : "pill-info"}">${escapeHtml(meta.badge)}</span>
          ${isActive ? `<span class="pill pill-paid">ACTIVE</span>` : ""}
        </div>
        <h4 style="margin:8px 0 4px;">${prof ? escapeHtml(prof.businessName) : escapeHtml(meta.label)}</h4>
        <div class="muted">${prof ? (slotId === "plain_1" ? "Plain Non-GST Counter" : escapeHtml(prof.gstin || "No GSTIN yet")) : allowed ? "Slot ready to create" : "Requires higher plan"}</div>
        <div style="display:flex;gap:6px;flex-wrap:wrap;margin-top:10px;">
          ${
            prof
              ? `
                ${!isActive ? `<button class="btn btn-sm btn-primary" data-prof-action="switch" data-slot="${slotId}">Switch</button>` : ""}
                <button class="btn btn-sm btn-ghost" data-prof-action="edit" data-slot="${slotId}">Edit</button>
                ${slotId !== "main_1" ? `<button class="btn btn-sm btn-danger-soft" data-prof-action="delete" data-slot="${slotId}">Delete</button>` : ""}
              `
              : allowed
                ? `<button class="btn btn-sm btn-secondary" data-prof-action="create" data-slot="${slotId}">+ Create Profile</button>`
                : `<button class="btn btn-sm btn-ghost" data-prof-action="upgrade">Upgrade (${tier === "silver" ? "Gold/Diamond" : "Diamond"})</button>`
          }
        </div>
      </div>`;
    })
    .join("");
  $("ws-profiles-grid").innerHTML = profileSlotsHtml;

  // Salesman Seats
  const seats = state.salesmen || [];
  $("ws-team-sub").textContent = `${seats.length} of ${spec.maxSalesmen} seat(s) active on ${spec.name}`;
  $("ws-team-list").innerHTML = seats.length
    ? seats
        .map((s) => {
          const sid = s.seatId || s.id;
          const enabledPerms = Object.values(s.permissions || {}).filter(Boolean).length;
          return `
          <div class="team-seat-row">
            <div>
              <div class="bold">${escapeHtml(s.name)} <span class="pill pill-info">${escapeHtml(s.roleLabel || "Salesman")}</span> <span class="pill ${s.isActive ? "pill-paid" : "pill-unpaid"}">${s.isActive ? "ACTIVE" : "PAUSED"}</span></div>
              <div class="muted mono">${escapeHtml(s.phoneNumber)} • Profiles: ${escapeHtml((s.allowedProfiles || []).join(", "))} • ${enabledPerms}/13 permissions</div>
            </div>
            <div class="row-actions">
              <button class="btn btn-sm btn-ghost" data-seat-action="toggle" data-id="${escapeHtml(sid)}">${s.isActive ? "Pause" : "Activate"}</button>
              <button class="btn btn-sm btn-ghost" data-seat-action="edit" data-id="${escapeHtml(sid)}">Edit</button>
              <button class="btn btn-sm btn-danger-soft" data-seat-action="delete" data-id="${escapeHtml(sid)}">Remove</button>
            </div>
          </div>`;
        })
        .join("")
    : `<p class="muted">${spec.maxSalesmen === 0 ? "Salesman seats require Gold (2 seats) or Diamond/Trial (5 seats)." : "No salesman OTP seats added yet. Click '+ Add Salesman Seat' to invite staff."}</p>`;

  // Sync & Backup counts
  $("ws-backup-summary").textContent = `${state.parties.length} Parties • ${state.inventory.length} Stock SKUs • ${state.invoices.length} Invoices • ${state.expenses.length} Expenses • ${state.dashboardEntries.length} Quick Entries`;

  // App Settings quick view
  $("ws-settings-summary").innerHTML = `
    <div><strong>Invoice Prefix:</strong> <span class="mono">${escapeHtml(settings.invoicePrefix)}</span> • <strong>Paper Size:</strong> ${escapeHtml(settings.defaultPaperSize.toUpperCase())} (${escapeHtml(settings.defaultPrintStyle)})</div>
    <div><strong>Automation:</strong> Stock Deduct (${settings.autoDeductStockOnSale ? "ON" : "OFF"}) • Auto-Log Purchase Expense (${settings.autoRecordPurchaseInExpenses ? "ON" : "OFF"}) • Auto State GST (${settings.autoDetectGstModeFromState ? "ON" : "OFF"})</div>
  `;
}

export function openBusinessProfileModal(slotId = state.activeProfileId || "main_1", isCreating = false) {
  const meta = PROFILE_SLOT_META[slotId] || PROFILE_SLOT_META.main_1;
  const existing = isCreating ? null : state.profiles.find((p) => p.id === slotId) || activeProfile();
  const isPlain = slotId === "plain_1";

  openModal(`
    <h3>${isCreating ? `Create ${meta.label}` : `Edit Business Profile (${slotId})`}</h3>
    <div class="form-grid">
      <div class="form-field"><label>Business Name *</label><input id="bp-biz" class="input-text" value="${escapeHtml(existing?.businessName || (isPlain ? "Plain Counter Finboss" : ""))}" /></div>
      <div class="form-field"><label>Owner Name *</label><input id="bp-owner" class="input-text" value="${escapeHtml(existing?.ownerName || state.user?.displayName || "Business Owner")}" /></div>
      <div class="form-field"><label>GSTIN</label><input id="bp-gstin" class="input-text mono" ${isPlain ? "disabled" : ""} value="${isPlain ? "" : escapeHtml(existing?.gstin || "")}" placeholder="${isPlain ? "Disabled in Plain Non-GST" : "27ABCDE1234F1Z5"}" /></div>
      <div class="form-field"><label>Business Type</label><input id="bp-type" class="input-text" value="${escapeHtml(existing?.businessType || "Retail & Wholesale")}" /></div>
      <div class="form-field span-2"><label>Address</label><input id="bp-addr" class="input-text" value="${escapeHtml(existing?.address || "")}" /></div>
      <div class="form-field"><label>State</label><select id="bp-state" class="input-select">
        ${INDIA_STATES.map((s) => `<option value="${s}" ${s.toLowerCase() === String(existing?.state || "Maharashtra").toLowerCase() ? "selected" : ""}>${s}</option>`).join("")}
      </select></div>
      <div class="form-field"><label>PIN Code</label><input id="bp-pin" class="input-text mono" maxlength="12" value="${escapeHtml(existing?.pincode || "")}" /></div>
      <div class="form-field"><label>Monthly Sales Target (₹)</label><input id="bp-target" type="number" min="0" class="input-text mono" value="${Number(existing?.monthlySalesTarget ?? 250000)}" /></div>
      <div class="form-field"><label>Fiscal Year Label</label><input id="bp-fy" class="input-text" value="${escapeHtml(existing?.fiscalYearLabel || "FY 2026-27")}" /></div>
    </div>
    <p id="m-err" class="error-box hidden"></p>
    <div class="modal-actions">
      <button id="m-cancel" class="btn btn-ghost">Cancel</button>
      <button id="bp-save" class="btn btn-primary">${isCreating ? "Create & Switch" : "Save Settings"}</button>
    </div>
  `);
  $("m-cancel").onclick = closeModal;
  $("bp-save").onclick = async () => {
    const businessName = $("bp-biz").value.trim().slice(0, 100);
    const ownerName = ($("bp-owner").value.trim() || "Business Owner").slice(0, 80);
    if (!businessName) {
      $("m-err").textContent = "Business Name is required.";
      $("m-err").classList.remove("hidden");
      return;
    }
    const uid = state.user.uid;
    const ref = doc(db, "users", uid, "business_profiles", slotId);
    const common = {
      ownerName,
      ownerEmail: (state.user?.email || ADMIN_RECEIPT_EMAIL).slice(0, 150),
      businessName,
      gstin: isPlain ? "" : $("bp-gstin").value.trim().toUpperCase().slice(0, 30),
      address: $("bp-addr").value.trim().slice(0, 200),
      state: $("bp-state").value.trim().slice(0, 60),
      pincode: $("bp-pin").value.replace(/[^0-9-]/g, "").slice(0, 12),
      currencySymbol: "₹",
      fiscalYearLabel: ($("bp-fy").value.trim() || "FY 2026-27").slice(0, 20),
      businessType: ($("bp-type").value.trim() || "Retail & Wholesale").slice(0, 40),
      monthlySalesTarget: Math.max(0, Number($("bp-target").value || 250000)),
      updatedAt: serverTimestamp()
    };

    if (isCreating || !state.profiles.some((p) => p.id === slotId)) {
      await setDoc(ref, {
        userId: uid,
        ...common,
        profileMode: meta.mode,
        createdAt: serverTimestamp()
      });
      state.activeProfileId = slotId;
      localStorage.setItem("book_web_active_profile", slotId);
    } else {
      await updateDoc(ref, common);
    }
    closeModal();
    showToast(`Business profile "${businessName}" saved!`);
  };
}

export function openAppSettingsModal() {
  let s = loadAppSettings();
  const plain = isPlainNonGst();
  openModal(
    `
    <h3>Finboss App Settings &amp; Print Logic</h3>
    <div class="form-grid">
      <div class="form-field"><label>Invoice Number Prefix</label><input id="st-prefix" class="input-text mono" value="${escapeHtml(s.invoicePrefix)}" /></div>
      <div class="form-field"><label>Default Paper Size</label><select id="st-paper" class="input-select">
        <option value="a4" ${s.defaultPaperSize === "a4" ? "selected" : ""}>A4 Standard</option>
        <option value="a5" ${s.defaultPaperSize === "a5" ? "selected" : ""}>A5 Half Sheet</option>
        <option value="thermal_80" ${s.defaultPaperSize === "thermal_80" ? "selected" : ""}>Thermal 80mm POS</option>
        <option value="thermal_58" ${s.defaultPaperSize === "thermal_58" ? "selected" : ""}>Thermal 58mm POS</option>
      </select></div>
      <div class="form-field"><label>Default Print Style</label><select id="st-style" class="input-select">
        <option value="modern" ${s.defaultPrintStyle === "modern" ? "selected" : ""}>Modern Executive</option>
        <option value="tally" ${s.defaultPrintStyle === "tally" ? "selected" : ""}>Classic Tally Grid</option>
        <option value="thermal" ${s.defaultPrintStyle === "thermal" ? "selected" : ""}>Thermal POS Receipt</option>
      </select></div>
      <div class="form-field"><label>Default GST Rate %</label><select id="st-gst" class="input-select" ${plain ? "disabled" : ""}>
        ${[0, 5, 12, 18, 28].map((r) => `<option value="${r}" ${Number(s.defaultGstSlabPercent) === r ? "selected" : ""}>${r}%</option>`).join("")}
      </select></div>
      <div class="form-field"><label>Default Low-Stock Alert Qty</label><input id="st-low" type="number" class="input-text mono" value="${Number(s.defaultLowStockThreshold)}" /></div>
      <div class="form-field"><label>Default Party Credit Limit (₹)</label><input id="st-cred" type="number" class="input-text mono" value="${Number(s.defaultPartyCreditLimit)}" /></div>
      <div class="form-field"><label>GST Portal Mode</label><select id="st-portal" class="input-select">
        <option value="sandbox" ${s.gstPortalMode === "sandbox" ? "selected" : ""}>Sandbox IRP (Test)</option>
        <option value="live" ${s.gstPortalMode === "live" ? "selected" : ""}>Live NIC GSP API</option>
        <option value="offline_json" ${s.gstPortalMode === "offline_json" ? "selected" : ""}>Offline NIC JSON Export</option>
      </select></div>
      <div class="form-field"><label>Default EWB Dispatch City</label><input id="st-city" class="input-text" value="${escapeHtml(s.defaultDispatchCity)}" /></div>
      <div class="form-field"><label>Default Transporter Name</label><input id="st-tr-name" class="input-text" value="${escapeHtml(s.defaultTransporterName)}" /></div>
      <div class="form-field"><label>Default Transporter ID</label><input id="st-tr-id" class="input-text mono" value="${escapeHtml(s.defaultTransporterId)}" /></div>
      <div class="form-field span-2"><label>Invoice Footer Note</label><input id="st-footer" class="input-text" value="${escapeHtml(s.invoiceFooterNote)}" /></div>
      <div class="form-field span-2"><label>Terms / Declaration on Bill</label><input id="st-terms" class="input-text" value="${escapeHtml(s.invoiceTermsText)}" /></div>
      <label class="check-row"><input id="st-ask-print" type="checkbox" ${s.askPrintOptionsBeforePrint ? "checked" : ""} /> Ask Size &amp; Style before Print</label>
      <label class="check-row"><input id="st-show-hsn" type="checkbox" ${s.showHsnOnInvoicePrint ? "checked" : ""} /> Show HSN Code on Bills</label>
      <label class="check-row"><input id="st-show-words" type="checkbox" ${s.showAmountInWordsOnPrint ? "checked" : ""} /> Show Amount in Words</label>
      <label class="check-row"><input id="st-auto-deduct" type="checkbox" ${s.autoDeductStockOnSale ? "checked" : ""} /> Auto-Deduct Stock on Sale</label>
      <label class="check-row"><input id="st-auto-pur" type="checkbox" ${s.autoRecordPurchaseInExpenses ? "checked" : ""} /> Auto-Log Purchase in Expenses &amp; ITC</label>
      <label class="check-row"><input id="st-auto-state" type="checkbox" ${s.autoDetectGstModeFromState ? "checked" : ""} /> Auto CGST+SGST vs IGST from State</label>
    </div>
    <div class="modal-actions">
      <button id="m-cancel" class="btn btn-ghost">Cancel</button>
      <button id="st-save" class="btn btn-primary">Save App Settings</button>
    </div>
    `,
    760
  );
  $("m-cancel").onclick = closeModal;
  $("st-save").onclick = () => {
    saveAppSettings({
      ...s,
      invoicePrefix: $("st-prefix").value.trim().toUpperCase().slice(0, 18) || "BK-",
      defaultPaperSize: $("st-paper").value,
      defaultPrintStyle: $("st-style").value,
      defaultGstSlabPercent: Number($("st-gst").value || 18),
      defaultLowStockThreshold: Math.max(0, Number($("st-low").value || 5)),
      defaultPartyCreditLimit: Math.max(0, Number($("st-cred").value || 50000)),
      gstPortalMode: $("st-portal").value,
      defaultDispatchCity: $("st-city").value.trim(),
      defaultTransporterName: $("st-tr-name").value.trim(),
      defaultTransporterId: $("st-tr-id").value.trim().toUpperCase(),
      invoiceFooterNote: $("st-footer").value.trim(),
      invoiceTermsText: $("st-terms").value.trim(),
      askPrintOptionsBeforePrint: $("st-ask-print").checked,
      showHsnOnInvoicePrint: $("st-show-hsn").checked,
      showAmountInWordsOnPrint: $("st-show-words").checked,
      autoDeductStockOnSale: $("st-auto-deduct").checked,
      autoRecordPurchaseInExpenses: $("st-auto-pur").checked,
      autoDetectGstModeFromState: $("st-auto-state").checked
    });
    closeModal();
    renderWorkspace();
    showToast("App settings saved!");
  };
}

export function openSalesmanSeatModal(existingSeat = null) {
  const spec = activePlanSpec();
  const usedSeatIds = (state.salesmen || []).map((s) => s.seatId || s.id);
  const nextSeatId = existingSeat
    ? existingSeat.seatId || existingSeat.id
    : SALESMAN_SEAT_IDS.find((id) => !usedSeatIds.includes(id) && spec.allowedSeats.includes(id));

  if (!nextSeatId) {
    showToast("Seat limit reached on your current plan. Upgrade to add more salesmen.");
    return openSubscriptionModal();
  }

  let currentPerms = {
    ...(existingSeat?.permissions || SALESMAN_ROLE_PRESETS.counter_billing.permissions)
  };
  let allowedProfiles = [...(existingSeat?.allowedProfiles || ["main_1"])];

  openModal(
    `
    <h3>${existingSeat ? `Edit Salesman (${nextSeatId})` : `Add Salesman OTP Seat (${nextSeatId})`}</h3>
    <div class="form-grid">
      <div class="form-field"><label>Staff Name *</label><input id="sm-name" class="input-text" value="${escapeHtml(existingSeat?.name || "")}" /></div>
      <div class="form-field"><label>Mobile Number (+91...) *</label><input id="sm-phone" class="input-text mono" value="${escapeHtml(existingSeat?.phoneNumber || "+91")}" placeholder="+919876543210" /></div>
      <div class="form-field"><label>Role Preset</label><select id="sm-preset" class="input-select">
        <option value="counter_billing" ${existingSeat?.roleLabel === "Counter Billing" ? "selected" : ""}>Counter Billing</option>
        <option value="field_sales" ${existingSeat?.roleLabel === "Field Sales & Ledger" || existingSeat?.roleLabel === "Field Sales & Khata" ? "selected" : ""}>Field Sales &amp; Ledger</option>
        <option value="store_manager" ${existingSeat?.roleLabel === "Store Manager" ? "selected" : ""}>Store Manager</option>
      </select></div>
      <div class="form-field"><label>Role Label</label><input id="sm-role" class="input-text" value="${escapeHtml(existingSeat?.roleLabel || "Counter Billing")}" /></div>
    </div>
    <div style="margin-top:8px;">
      <div class="small-label" style="color:var(--muted);margin-bottom:6px;">ALLOWED BUSINESS PROFILES</div>
      <div style="display:flex;gap:12px;flex-wrap:wrap;">
        ${["main_1", "main_2", "plain_1"]
          .map(
            (pid) => `<label class="check-row"><input type="checkbox" class="sm-prof-cb" value="${pid}" ${allowedProfiles.includes(pid) ? "checked" : ""} /> ${escapeHtml(PROFILE_SLOT_META[pid].badge)}</label>`
          )
          .join("")}
      </div>
    </div>
    <div style="margin-top:12px;">
      <div class="small-label" style="color:var(--muted);margin-bottom:6px;">GRANULAR PERMISSIONS (13)</div>
      <div class="perms-grid">
        ${SALESMAN_PERMISSION_DEFS.map(
          (p) => `<label class="check-row"><input type="checkbox" class="sm-perm-cb" data-pkey="${p.key}" ${currentPerms[p.key] ? "checked" : ""} /> ${escapeHtml(p.label)}</label>`
        ).join("")}
      </div>
    </div>
    <p id="m-err" class="error-box hidden"></p>
    <div class="modal-actions">
      <button id="m-cancel" class="btn btn-ghost">Cancel</button>
      <button id="sm-save" class="btn btn-primary">Save Salesman Access</button>
    </div>
    `,
    740
  );

  $("m-cancel").onclick = closeModal;
  $("sm-preset").onchange = (e) => {
    const preset = SALESMAN_ROLE_PRESETS[e.target.value];
    if (!preset) return;
    $("sm-role").value = preset.label;
    document.querySelectorAll(".sm-perm-cb").forEach((cb) => {
      cb.checked = Boolean(preset.permissions[cb.dataset.pkey]);
    });
  };

  $("sm-save").onclick = async () => {
    const name = $("sm-name").value.trim().slice(0, 60);
    const phoneE164 = normalizePhoneE164("+91", $("sm-phone").value);
    if (!name || !phoneE164) {
      $("m-err").textContent = "Enter salesman name and valid E.164 mobile number (e.g. +919876543210).";
      $("m-err").classList.remove("hidden");
      return;
    }
    const roleLabel = ($("sm-role").value.trim() || "Salesman").slice(0, 40);
    const checkedProfiles = Array.from(document.querySelectorAll(".sm-prof-cb:checked")).map((c) => c.value);
    const cleanProfiles = checkedProfiles.length ? checkedProfiles : ["main_1"];
    const permissions = {};
    SALESMAN_PERMISSION_DEFS.forEach((p) => {
      const cb = document.querySelector(`.sm-perm-cb[data-pkey="${p.key}"]`);
      permissions[p.key] = Boolean(cb?.checked);
    });

    const uid = state.user.uid;
    const batch = writeBatch(db);
    const seatRef = doc(db, "users", uid, "salesmen", nextSeatId);
    const grantRef = doc(db, "phone_access", phoneE164);
    const prevPhone = existingSeat?.phoneNumber?.trim();
    const isSamePhone = Boolean(existingSeat && prevPhone === phoneE164);

    if (existingSeat) {
      batch.update(seatRef, {
        name,
        phoneNumber: phoneE164,
        roleLabel,
        isActive: existingSeat.isActive ?? true,
        allowedProfiles: cleanProfiles,
        permissions,
        updatedAt: serverTimestamp()
      });
    } else {
      batch.set(seatRef, {
        userId: uid,
        seatId: nextSeatId,
        name,
        phoneNumber: phoneE164,
        roleLabel,
        isActive: true,
        allowedProfiles: cleanProfiles,
        permissions,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      });
    }

    if (prevPhone && prevPhone !== phoneE164) {
      batch.delete(doc(db, "phone_access", prevPhone));
    }

    if (isSamePhone) {
      batch.update(grantRef, {
        seatId: nextSeatId,
        salesmanName: name,
        roleLabel,
        isActive: existingSeat.isActive ?? true,
        allowedProfiles: cleanProfiles,
        permissions,
        updatedAt: serverTimestamp()
      });
    } else {
      batch.set(grantRef, {
        ownerUid: uid,
        seatId: nextSeatId,
        phoneNumber: phoneE164,
        salesmanName: name,
        roleLabel,
        isActive: existingSeat ? Boolean(existingSeat.isActive) : true,
        allowedProfiles: cleanProfiles,
        permissions,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      });
    }

    try {
      await batch.commit();
      closeModal();
      showToast(`Saved salesman access for ${name} (${phoneE164})!`);
    } catch (err) {
      $("m-err").textContent = err?.message || "Failed to save salesman seat.";
      $("m-err").classList.remove("hidden");
    }
  };
}

export function exportBackupJson() {
  const payload = {
    app: "Finboss Billing No.1 ERP",
    schemaVersion: 1,
    exportedAtMs: Date.now(),
    businessProfile: activeProfile(),
    businessProfiles: state.profiles,
    parties: state.parties,
    inventory: state.inventory,
    invoices: state.invoices,
    expenses: state.expenses,
    dashboardEntries: state.dashboardEntries
  };
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = `Finboss_Backup_${new Date().toISOString().slice(0, 10)}.json`;
  a.click();
  showToast("Exported Finboss JSON Backup!");
}

export async function restoreBackupJsonFile(file) {
  if (!file) return;
  try {
    const raw = await file.text();
    const data = JSON.parse(raw);
    if (!data || Number(data.schemaVersion || 0) < 1) {
      return showToast("Unrecognized backup JSON format.");
    }
    const uid = state.user.uid;
    const validId = (id) => typeof id === "string" && /^[a-zA-Z0-9_-]{1,128}$/.test(id);
    const existingParties = new Set(state.parties.map((x) => x.id));
    const existingStock = new Set(state.inventory.map((x) => x.id));
    const existingInv = new Set(state.invoices.map((x) => x.id));
    const existingExp = new Set(state.expenses.map((x) => x.id));
    const existingEnt = new Set(state.dashboardEntries.map((x) => x.id));

    const ops = [];
    (data.parties || []).forEach((p) => {
      if (p.name && !existingParties.has(p.id)) {
        const ref = validId(p.id) ? doc(db, "users", uid, "parties", p.id) : doc(db, "users", uid, "parties", `pty_${Date.now()}_${ops.length}`);
        ops.push([ref, {
          userId: uid, businessProfileId: p.businessProfileId || "main_1",
          name: String(p.name).slice(0, 100), partyType: p.partyType === "supplier" ? "supplier" : "customer",
          phone: String(p.phone || "").slice(0, 25), gstin: String(p.gstin || "").toUpperCase().slice(0, 30),
          address: String(p.address || "").slice(0, 200), state: String(p.state || "").slice(0, 60),
          pincode: String(p.pincode || "").slice(0, 12), openingDue: Number(p.openingDue || 0),
          totalBilled: Number(p.totalBilled || 0), totalPaid: Number(p.totalPaid || 0),
          creditLimit: Number(p.creditLimit || 0), notes: String(p.notes || "").slice(0, 200),
          createdAt: serverTimestamp(), updatedAt: serverTimestamp()
        }]);
      }
    });
    (data.inventory || []).forEach((it) => {
      if (it.name && !existingStock.has(it.id)) {
        const ref = validId(it.id) ? doc(db, "users", uid, "inventory_items", it.id) : doc(db, "users", uid, "inventory_items", `sku_${Date.now()}_${ops.length}`);
        ops.push([ref, {
          userId: uid, businessProfileId: it.businessProfileId || "main_1",
          name: String(it.name).slice(0, 100), sku: String(it.sku || "SKU-RESTORE").slice(0, 40),
          hsnCode: String(it.hsnCode || "").slice(0, 16), category: String(it.category || "General").slice(0, 40),
          unit: String(it.unit || "PCS").slice(0, 15), salePrice: Number(it.salePrice || 0),
          purchasePrice: Number(it.purchasePrice || 0), gstRatePercent: Math.min(28, Math.max(0, Number(it.gstRatePercent || 0))),
          stockQty: Math.max(0, Number(it.stockQty || 0)), lowStockThreshold: Math.max(0, Number(it.lowStockThreshold || 5)),
          createdAt: serverTimestamp(), updatedAt: serverTimestamp()
        }]);
      }
    });
    (data.invoices || []).forEach((inv) => {
      if (inv.invoiceNumber && (inv.lineItems || []).length && Number(inv.grandTotal || 0) > 0 && !existingInv.has(inv.id)) {
        const ref = validId(inv.id) ? doc(db, "users", uid, "invoices", inv.id) : doc(db, "users", uid, "invoices", `inv_${Date.now()}_${ops.length}`);
        ops.push([ref, {
          userId: uid, businessProfileId: inv.businessProfileId || "main_1",
          invoiceNumber: String(inv.invoiceNumber).slice(0, 40), partyId: String(inv.partyId || "").slice(0, 128),
          partyName: String(inv.partyName || "Walk-in Customer").slice(0, 100), partyGstin: String(inv.partyGstin || "").slice(0, 30),
          partyAddress: String(inv.partyAddress || "").slice(0, 200), partyState: String(inv.partyState || "").slice(0, 60),
          partyPincode: String(inv.partyPincode || "").slice(0, 12), invoiceType: inv.invoiceType === "pos_bill" ? "pos_bill" : "tax_invoice",
          taxMode: ["cgst_sgst", "igst", "none"].includes(inv.taxMode) ? inv.taxMode : "cgst_sgst",
          paymentMethod: ["cash", "upi", "card", "credit"].includes(inv.paymentMethod) ? inv.paymentMethod : "upi",
          status: ["paid", "partial", "unpaid"].includes(inv.status) ? inv.status : "paid",
          subtotal: Math.max(0, Number(inv.subtotal || 0)), gstTotal: Math.max(0, Number(inv.gstTotal || 0)),
          discountAmount: Math.max(0, Number(inv.discountAmount || 0)), grandTotal: Math.max(0.01, Number(inv.grandTotal || 1)),
          paidAmount: Math.min(Math.max(0.01, Number(inv.grandTotal || 1)), Math.max(0, Number(inv.paidAmount || 0))),
          notes: String(inv.notes || "").slice(0, 240), lineItems: (inv.lineItems || []).slice(0, 25),
          createdAt: serverTimestamp(), updatedAt: serverTimestamp()
        }]);
      }
    });
    (data.expenses || []).forEach((e) => {
      if (e.title && Number(e.amount || 0) > 0 && !existingExp.has(e.id)) {
        const ref = validId(e.id) ? doc(db, "users", uid, "expenses", e.id) : doc(db, "users", uid, "expenses", `exp_${Date.now()}_${ops.length}`);
        const amt = Math.max(0.01, Number(e.amount));
        ops.push([ref, {
          userId: uid, businessProfileId: e.businessProfileId || "main_1",
          title: String(e.title).slice(0, 100),
          category: ["rent", "payroll", "utilities", "purchases", "transport", "marketing", "tax", "other"].includes(e.category) ? e.category : "other",
          paymentMethod: ["cash", "upi", "bank", "card"].includes(e.paymentMethod) ? e.paymentMethod : "upi",
          status: e.status === "pending" ? "pending" : "settled",
          partyId: String(e.partyId || "").slice(0, 128), partyName: String(e.partyName || "").slice(0, 100),
          referenceNo: String(e.referenceNo || "").slice(0, 40), amount: amt,
          gstInputAmount: Math.min(amt, Math.max(0, Number(e.gstInputAmount || 0))),
          notes: String(e.notes || "").slice(0, 240), createdAt: serverTimestamp(), updatedAt: serverTimestamp()
        }]);
      }
    });
    (data.dashboardEntries || []).forEach((en) => {
      if (en.title && Number(en.amount || 0) > 0 && !existingEnt.has(en.id)) {
        const ref = validId(en.id) ? doc(db, "users", uid, "dashboard_entries", en.id) : doc(db, "users", uid, "dashboard_entries", `qe_${Date.now()}_${ops.length}`);
        ops.push([ref, {
          userId: uid, businessProfileId: en.businessProfileId || "main_1",
          title: String(en.title).slice(0, 100), partyName: String(en.partyName || "").slice(0, 100),
          category: ["sale", "expense", "receivable"].includes(en.category) ? en.category : "sale",
          amount: Math.max(0.01, Number(en.amount)), status: en.status === "pending" ? "pending" : "paid",
          referenceCode: String(en.referenceCode || "").slice(0, 30),
          createdAt: serverTimestamp(), updatedAt: serverTimestamp()
        }]);
      }
    });

    for (let i = 0; i < ops.length; i += 400) {
      const batch = writeBatch(db);
      ops.slice(i, i + 400).forEach(([ref, d]) => batch.set(ref, d));
      await batch.commit();
    }
    showToast(`Restored ${ops.length} new record(s) from backup!`);
  } catch (err) {
    showToast(err?.message || "Failed to restore backup JSON.");
  }
}

export function openSubscriptionModal() {
  let cycle = "yearly";
  const renderPlans = () => {
    openModal(`
      <h3>Finboss Billing No.1 ERP Subscription &amp; Plans</h3>
      <p class="muted">New workspaces include a 30-day free trial. Paid checkout is unavailable until verified payments are connected.</p>
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
              <div class="muted">${cycle === "yearly" ? "per year" : "per month"} • ${p.maxProfiles} profile(s) • ${p.maxSalesmen} staff</div>
              <div class="muted">${p.eInvoice ? "Includes E-Invoice &amp; E-Way Bill" : "Standard Billing"}</div>
              <button class="btn btn-primary" disabled>Paid checkout unavailable</button>
            </div>`;
          })
          .join("")}
      </div>
      <div class="modal-actions"><button id="m-cancel" class="btn btn-ghost">Close</button></div>
    `);
    $("m-cancel").onclick = closeModal;
    $("sub-cyc-m").onclick = () => { cycle = "monthly"; renderPlans(); };
    $("sub-cyc-y").onclick = () => { cycle = "yearly"; renderPlans(); };
  };
  renderPlans();
}

export function bindWorkspaceEvents() {
  $("ws-profiles-grid").onclick = async (e) => {
    const btn = e.target.closest("[data-prof-action]");
    if (!btn) return;
    const action = btn.dataset.profAction;
    const slot = btn.dataset.slot;
    if (action === "upgrade") return openSubscriptionModal();
    if (action === "switch") {
      state.activeProfileId = slot;
      localStorage.setItem("book_web_active_profile", slot);
      state.renderCallback?.();
      return showToast(`Switched to ${PROFILE_SLOT_META[slot]?.badge || slot}`);
    }
    if (action === "edit") return openBusinessProfileModal(slot, false);
    if (action === "create") return openBusinessProfileModal(slot, true);
    if (action === "delete" && (slot === "main_2" || slot === "plain_1")) {
      await deleteDoc(doc(db, "users", state.user.uid, "business_profiles", slot));
      if (state.activeProfileId === slot) {
        state.activeProfileId = "main_1";
        localStorage.setItem("book_web_active_profile", "main_1");
      }
      showToast(`Deleted profile slot ${slot}`);
    }
  };

  $("ws-add-seat-btn").onclick = () => openSalesmanSeatModal(null);

  $("ws-team-list").onclick = async (e) => {
    const btn = e.target.closest("[data-seat-action]");
    if (!btn) return;
    const seat = (state.salesmen || []).find((s) => (s.seatId || s.id) === btn.dataset.id);
    if (!seat) return;
    const sid = seat.seatId || seat.id;
    const uid = state.user.uid;
    const action = btn.dataset.seatAction;

    if (action === "edit") return openSalesmanSeatModal(seat);
    if (action === "toggle") {
      const nextActive = !seat.isActive;
      const batch = writeBatch(db);
      batch.update(doc(db, "users", uid, "salesmen", sid), { isActive: nextActive, updatedAt: serverTimestamp() });
      if (seat.phoneNumber) {
        batch.update(doc(db, "phone_access", seat.phoneNumber), { isActive: nextActive, updatedAt: serverTimestamp() });
      }
      await batch.commit();
      return showToast(`${seat.name} ${nextActive ? "activated" : "paused"}.`);
    }
    if (action === "delete") {
      const batch = writeBatch(db);
      batch.delete(doc(db, "users", uid, "salesmen", sid));
      if (seat.phoneNumber) {
        batch.delete(doc(db, "phone_access", seat.phoneNumber));
      }
      await batch.commit();
      return showToast(`Removed salesman ${seat.name}.`);
    }
  };

  $("ws-open-settings-btn").onclick = openAppSettingsModal;
  $("ws-export-json-btn").onclick = exportBackupJson;
  $("ws-import-json-input").onchange = async (e) => {
    const file = e.target.files?.[0];
    if (file) await restoreBackupJsonFile(file);
    e.target.value = "";
  };
  $("ws-open-sub-btn").onclick = openSubscriptionModal;
}
