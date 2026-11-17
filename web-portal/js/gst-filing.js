import { state } from "./state.js";
import { $, activeProfile, escapeHtml, fmtPct, inr, isPlainNonGst, profileScoped, showToast, toMs } from "./utils.js";

function defaultReturnPeriod() {
  const now = new Date();
  const mm = String(now.getMonth() + 1).padStart(2, "0");
  return `${mm}${now.getFullYear()}`;
}

function invoicesForPeriod(periodMmYyyy) {
  const clean = String(periodMmYyyy || "").replace(/\D/g, "");
  const all = profileScoped(state.invoices).filter((inv) => inv.taxMode !== "none");
  if (clean.length !== 6) return all;
  const monthIdx = Number(clean.slice(0, 2)) - 1;
  const year = Number(clean.slice(2, 6));
  return all.filter((inv) => {
    const d = new Date(toMs(inv.createdAt));
    return d.getMonth() === monthIdx && d.getFullYear() === year;
  });
}

function expensesForPeriod(periodMmYyyy) {
  const clean = String(periodMmYyyy || "").replace(/\D/g, "");
  const all = profileScoped(state.expenses);
  if (clean.length !== 6) return all;
  const monthIdx = Number(clean.slice(0, 2)) - 1;
  const year = Number(clean.slice(2, 6));
  return all.filter((exp) => {
    const d = new Date(toMs(exp.createdAt));
    return d.getMonth() === monthIdx && d.getFullYear() === year;
  });
}

export function buildGstr1Payload(periodMmYyyy, gstinOverride = "") {
  const prof = activeProfile();
  const gstin = (gstinOverride || prof.gstin || "").trim().toUpperCase();
  const fp = (periodMmYyyy || defaultReturnPeriod()).replace(/\D/g, "").slice(0, 6) || defaultReturnPeriod();
  const invs = invoicesForPeriod(fp);

  const b2bMap = new Map();
  const b2csMap = new Map();
  const hsnMap = new Map();

  invs.forEach((inv) => {
    const dt = new Date(toMs(inv.createdAt));
    const idt = `${String(dt.getDate()).padStart(2, "0")}-${String(dt.getMonth() + 1).padStart(2, "0")}-${dt.getFullYear()}`;
    const pos = String(inv.partyGstin || "").slice(0, 2) || (gstin.slice(0, 2) || "27");

    if (inv.partyGstin) {
      const ctin = String(inv.partyGstin).trim().toUpperCase();
      if (!b2bMap.has(ctin)) b2bMap.set(ctin, []);
      b2bMap.get(ctin).push({
        inum: inv.invoiceNumber,
        idt,
        val: Number(inv.grandTotal || 0),
        pos,
        rchrg: "N",
        inv_typ: "R",
        itms: (inv.lineItems || []).map((l, idx) => {
          const rt = Number(l.gstRatePercent || 0);
          const txval = Number(l.taxableAmount || 0);
          const tax = Number(l.gstAmount || 0);
          return {
            num: idx + 1,
            itm_det: {
              rt,
              txval,
              iamt: inv.taxMode === "igst" ? tax : 0,
              camt: inv.taxMode === "cgst_sgst" ? Math.round((tax / 2) * 100) / 100 : 0,
              samt: inv.taxMode === "cgst_sgst" ? Math.round((tax / 2) * 100) / 100 : 0
            }
          };
        })
      });
    } else {
      (inv.lineItems || []).forEach((l) => {
        const rt = Number(l.gstRatePercent || 0);
        const key = `${pos}_${rt}`;
        const prev = b2csMap.get(key) || { sply_ty: inv.taxMode === "igst" ? "INTER" : "INTRA", pos, rt, txval: 0, iamt: 0, camt: 0, samt: 0 };
        const tax = Number(l.gstAmount || 0);
        prev.txval += Number(l.taxableAmount || 0);
        if (inv.taxMode === "igst") prev.iamt += tax;
        else {
          prev.camt += tax / 2;
          prev.samt += tax / 2;
        }
        b2csMap.set(key, prev);
      });
    }

    (inv.lineItems || []).forEach((l, i) => {
      const hsn = (l.hsnCode || "9999").trim().toUpperCase();
      const rt = Number(l.gstRatePercent || 0);
      const key = `${hsn}_${rt}`;
      const prev = hsnMap.get(key) || {
        num: hsnMap.size + 1 + i * 0,
        hsn_sc: hsn,
        uqc: (l.unit || "PCS").slice(0, 8),
        qty: 0,
        txval: 0,
        rt,
        iamt: 0,
        camt: 0,
        samt: 0
      };
      const tax = Number(l.gstAmount || 0);
      prev.qty += Number(l.qty || 0);
      prev.txval += Number(l.taxableAmount || 0);
      if (inv.taxMode === "igst") prev.iamt += tax;
      else {
        prev.camt += tax / 2;
        prev.samt += tax / 2;
      }
      hsnMap.set(key, prev);
    });
  });

  const round2 = (v) => Math.round(Number(v || 0) * 100) / 100;
  return {
    gstin,
    fp,
    b2b: Array.from(b2bMap.entries()).map(([ctin, inv]) => ({ ctin, inv })),
    b2cs: Array.from(b2csMap.values()).map((x) => ({
      ...x,
      txval: round2(x.txval),
      iamt: round2(x.iamt),
      camt: round2(x.camt),
      samt: round2(x.samt)
    })),
    hsn: {
      data: Array.from(hsnMap.values()).map((h, idx) => ({
        num: idx + 1,
        hsn_sc: h.hsn_sc,
        uqc: h.uqc,
        qty: round2(h.qty),
        rt: h.rt,
        txval: round2(h.txval),
        iamt: round2(h.iamt),
        camt: round2(h.camt),
        samt: round2(h.samt)
      }))
    },
    _summary: {
      invoiceCount: invs.length,
      taxableTotal: round2(invs.reduce((s, i) => s + Number(i.subtotal || 0), 0)),
      gstTotal: round2(invs.reduce((s, i) => s + Number(i.gstTotal || 0), 0))
    }
  };
}

export function buildGstr3bPayload(periodMmYyyy, gstinOverride = "") {
  const prof = activeProfile();
  const gstin = (gstinOverride || prof.gstin || "").trim().toUpperCase();
  const fp = (periodMmYyyy || defaultReturnPeriod()).replace(/\D/g, "").slice(0, 6) || defaultReturnPeriod();
  const invs = invoicesForPeriod(fp);
  const exps = expensesForPeriod(fp);

  const round2 = (v) => Math.round(Number(v || 0) * 100) / 100;
  const txval = round2(invs.reduce((s, i) => s + Number(i.subtotal || 0), 0));
  let iamt = 0;
  let camt = 0;
  let samt = 0;
  invs.forEach((i) => {
    const g = Number(i.gstTotal || 0);
    if (i.taxMode === "igst") iamt += g;
    else {
      camt += g / 2;
      samt += g / 2;
    }
  });
  const totalOut = round2(iamt + camt + samt);
  const totalItc = round2(exps.reduce((s, e) => s + Number(e.gstInputAmount || 0), 0));
  const netPayable = round2(Math.max(0, totalOut - totalItc));

  return {
    gstin,
    ret_period: fp,
    sup_details: {
      osup_det: {
        txval,
        iamt: round2(iamt),
        camt: round2(camt),
        samt: round2(samt),
        csamt: 0
      }
    },
    itc_elg: {
      itc_avl: [
        {
          ty: "OTH",
          iamt: 0,
          camt: round2(totalItc / 2),
          samt: round2(totalItc / 2),
          csamt: 0
        }
      ]
    },
    _summary: {
      taxableSales: txval,
      outputGst: totalOut,
      inputItc: totalItc,
      netPayable
    }
  };
}

function setStatusBanner(msg, isError = false) {
  const el = $("gst-portal-status");
  if (!el) return;
  if (!msg) {
    el.classList.add("hidden");
    return;
  }
  el.textContent = msg;
  el.classList.toggle("error", isError);
  el.classList.remove("hidden");
}

function downloadJsonFile(filename, dataObj) {
  const blob = new Blob([JSON.stringify(dataObj, null, 2)], { type: "application/json" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
}

async function callGstApi(endpoint, payload) {
  if (!state.user) throw new Error("Sign in first before calling GST Portal APIs.");
  const baseUrl = ($("gst-api-base-url")?.value || state.gstApiBaseUrl || window.location.origin).trim().replace(/\/+$/, "");
  if (!/^https?:\/\//i.test(baseUrl)) {
    throw new Error("Enter a valid https:// GST API Server URL.");
  }
  localStorage.setItem("finboss_gst_api_url", baseUrl);
  state.gstApiBaseUrl = baseUrl;

  const token = await state.user.getIdToken();
  const res = await fetch(`${baseUrl}${endpoint}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(payload)
  });
  const text = await res.text();
  let json = {};
  try {
    json = text ? JSON.parse(text) : {};
  } catch (_) {
    throw new Error(`Endpoint ${endpoint} returned non-JSON HTTP ${res.status}. Ensure your Firebase GST Cloud Function is deployed at ${baseUrl}.`);
  }
  if (!res.ok || json.success === false) {
    throw new Error(json.error || json.message || `GST API failed (HTTP ${res.status}).`);
  }
  return json;
}

export function renderGstFiling() {
  if (!$("tab-gst_filing")) return;
  const prof = activeProfile();
  const plain = isPlainNonGst();
  const badge = $("gst-filing-mode-badge");
  if (badge) {
    badge.textContent = plain ? "Plain Non-GST Mode" : prof.gstin ? `GSTIN: ${prof.gstin}` : "GSTIN Not Set";
    badge.className = `pill ${plain ? "pill-partial" : prof.gstin ? "pill-paid" : "pill-info"}`;
  }

  const periodInput = $("gst-return-period");
  if (periodInput && !periodInput.value) periodInput.value = defaultReturnPeriod();

  const gstinInput = $("gst-filing-gstin");
  if (gstinInput && !gstinInput.value && prof.gstin) gstinInput.value = prof.gstin;

  const emailInput = $("gst-portal-email");
  if (emailInput && !emailInput.value) emailInput.value = prof.ownerEmail || state.user?.email || "";

  const apiInput = $("gst-api-base-url");
  if (apiInput && !apiInput.value) apiInput.value = state.gstApiBaseUrl || "";

  const period = periodInput?.value || defaultReturnPeriod();
  const gstin = gstinInput?.value || prof.gstin || "";
  const g1 = buildGstr1Payload(period, gstin);
  const g3b = buildGstr3bPayload(period, gstin);

  if ($("gst-gstr1-meta")) {
    $("gst-gstr1-meta").textContent = `${g1.b2b.length} B2B GSTIN(s) • ${g1.b2cs.length} B2C slab(s) • ${g1.hsn.data.length} HSN row(s)`;
  }
  if ($("gst-gstr1-kpis")) {
    $("gst-gstr1-kpis").innerHTML = `
      <div class="kpi-card"><div class="kpi-label">Invoices</div><div class="kpi-value mono">${g1._summary.invoiceCount}</div></div>
      <div class="kpi-card"><div class="kpi-label">Taxable Value</div><div class="kpi-value mono">${inr(g1._summary.taxableTotal)}</div></div>
      <div class="kpi-card"><div class="kpi-label">Output Tax</div><div class="kpi-value mono">${inr(g1._summary.gstTotal)}</div></div>
    `;
  }
  if ($("gst-gstr1-preview")) {
    const clean1 = { gstin: g1.gstin, fp: g1.fp, b2b: g1.b2b, b2cs: g1.b2cs, hsn: g1.hsn };
    $("gst-gstr1-preview").textContent = JSON.stringify(clean1, null, 2);
  }

  if ($("gst-gstr3b-kpis")) {
    $("gst-gstr3b-kpis").innerHTML = `
      <div class="kpi-card"><div class="kpi-label">Output GST</div><div class="kpi-value mono">${inr(g3b._summary.outputGst)}</div></div>
      <div class="kpi-card"><div class="kpi-label">Eligible ITC</div><div class="kpi-value mono text-emerald">${inr(g3b._summary.inputItc)}</div></div>
      <div class="kpi-card"><div class="kpi-label">Net Payable</div><div class="kpi-value mono text-amber">${inr(g3b._summary.netPayable)}</div></div>
    `;
  }
  if ($("gst-gstr3b-preview")) {
    const clean3b = { gstin: g3b.gstin, ret_period: g3b.ret_period, sup_details: g3b.sup_details, itc_elg: g3b.itc_elg };
    $("gst-gstr3b-preview").textContent = JSON.stringify(clean3b, null, 2);
  }
}

export function bindGstFilingEvents() {
  ["gst-return-period", "gst-filing-gstin"].forEach((id) => {
    const el = $(id);
    if (el) el.oninput = renderGstFiling;
  });

  $("gst-dl-gstr1-btn").onclick = () => {
    const period = $("gst-return-period").value || defaultReturnPeriod();
    const g1 = buildGstr1Payload(period, $("gst-filing-gstin").value);
    const { _summary, ...clean } = g1;
    downloadJsonFile(`GSTR1_${clean.gstin || "RETURN"}_${clean.fp}.json`, clean);
    showToast("Downloaded GSTR-1 JSON!");
  };

  $("gst-dl-gstr3b-btn").onclick = () => {
    const period = $("gst-return-period").value || defaultReturnPeriod();
    const g3b = buildGstr3bPayload(period, $("gst-filing-gstin").value);
    const { _summary, ...clean } = g3b;
    downloadJsonFile(`GSTR3B_${clean.gstin || "RETURN"}_${clean.ret_period}.json`, clean);
    showToast("Downloaded GSTR-3B JSON!");
  };

  const runApiAction = async (btnId, fn) => {
    const btn = $(btnId);
    const prev = btn.textContent;
    btn.disabled = true;
    btn.textContent = "Working...";
    try {
      await fn();
    } catch (err) {
      setStatusBanner(err?.message || "GST Portal request failed.", true);
      showToast(err?.message || "GST Portal request failed.");
    } finally {
      btn.disabled = false;
      btn.textContent = prev;
    }
  };

  $("gst-request-otp-btn").onclick = () =>
    runApiAction("gst-request-otp-btn", async () => {
      const gstin = $("gst-filing-gstin").value.trim().toUpperCase();
      const gstUsername = $("gst-portal-username").value.trim();
      const email = $("gst-portal-email").value.trim();
      if (!gstin || !gstUsername) throw new Error("Enter GSTIN and GST Portal Username first.");
      const res = await callGstApi("/api/gst/request-otp", {
        profileId: state.activeProfileId || "main_1",
        gstin,
        gstUsername,
        email
      });
      setStatusBanner(res.message || "GST Portal OTP requested successfully.");
      showToast(res.message || "OTP requested!");
    });

  $("gst-verify-otp-btn").onclick = () =>
    runApiAction("gst-verify-otp-btn", async () => {
      const gstin = $("gst-filing-gstin").value.trim().toUpperCase();
      const gstUsername = $("gst-portal-username").value.trim();
      const email = $("gst-portal-email").value.trim();
      const otp = $("gst-portal-otp").value.trim();
      if (!gstin || !gstUsername || !otp) throw new Error("Enter GSTIN, Username, and Portal OTP.");
      const res = await callGstApi("/api/gst/verify-otp", {
        profileId: state.activeProfileId || "main_1",
        gstin,
        gstUsername,
        email,
        otp
      });
      setStatusBanner(res.message || "GST Portal session verified!");
      showToast(res.message || "OTP verified!");
    });

  $("gst-upload-gstr1-btn").onclick = () =>
    runApiAction("gst-upload-gstr1-btn", async () => {
      const period = $("gst-return-period").value.trim() || defaultReturnPeriod();
      const g1 = buildGstr1Payload(period, $("gst-filing-gstin").value);
      const { _summary, ...clean } = g1;
      const res = await callGstApi("/api/gst/upload-gstr1", {
        profileId: state.activeProfileId || "main_1",
        returnPeriod: period,
        returnData: clean
      });
      setStatusBanner(res.message || "GSTR-1 uploaded successfully.");
      showToast(res.message || "GSTR-1 uploaded!");
    });

  $("gst-save-gstr3b-btn").onclick = () =>
    runApiAction("gst-save-gstr3b-btn", async () => {
      const period = $("gst-return-period").value.trim() || defaultReturnPeriod();
      const g3b = buildGstr3bPayload(period, $("gst-filing-gstin").value);
      const { _summary, ...clean } = g3b;
      const res = await callGstApi("/api/gst/gstr3b/save", {
        profileId: state.activeProfileId || "main_1",
        returnPeriod: period,
        returnData: clean
      });
      setStatusBanner(res.message || "GSTR-3B saved to GST Portal.");
      showToast(res.message || "GSTR-3B saved!");
    });

  $("gst-file-evc-btn").onclick = () =>
    runApiAction("gst-file-evc-btn", async () => {
      const period = $("gst-return-period").value.trim() || defaultReturnPeriod();
      const pan = $("gst-evc-pan").value.trim().toUpperCase();
      const otp = $("gst-evc-otp").value.trim();
      if (!pan || !otp) throw new Error("Enter Authorized Signatory PAN and EVC OTP to file GSTR-3B.");
      const g3b = buildGstr3bPayload(period, $("gst-filing-gstin").value);
      const { _summary, ...clean } = g3b;
      const res = await callGstApi("/api/gst/gstr3b/file-evc", {
        profileId: state.activeProfileId || "main_1",
        returnPeriod: period,
        pan,
        otp,
        confirmFinalFiling: "FILE_GSTR3B",
        returnData: clean
      });
      setStatusBanner(`${res.message || "GSTR-3B EVC submitted."}${res.referenceId ? ` Ref: ${res.referenceId}` : ""}`);
      showToast(res.message || "GSTR-3B filing request completed!");
    });
}
