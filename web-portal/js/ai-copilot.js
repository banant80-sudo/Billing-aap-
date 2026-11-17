import { app, EXPENSE_CATEGORIES, state } from "./state.js";
import { RECAPTCHA_SITE_KEY } from "./firebase-config.js";
import { openInvoiceModal } from "./billing.js";
import { openExpenseModal } from "./stock-expenses.js";
import { computeLedgerAnalytics } from "./ledger.js";
import {
  $,
  activeProfile,
  escapeHtml,
  fmtPct,
  fmtQty,
  inr,
  isPlainNonGst,
  profileScoped,
  showToast
} from "./utils.js";

let aiClientPromise = null;

async function getAiModel(responseMimeType = null) {
  if (!aiClientPromise) {
    aiClientPromise = (async () => {
      try {
        const appCheckMod = await import(
          "https://www.gstatic.com/firebasejs/11.14.0/firebase-app-check.js"
        );
        if (RECAPTCHA_SITE_KEY) {
          appCheckMod.initializeAppCheck(app, {
            provider: new appCheckMod.ReCaptchaEnterpriseProvider(RECAPTCHA_SITE_KEY),
            isTokenAutoRefreshEnabled: true
          });
        }
      } catch (_) {
        // Optional on localhost if App Check domain isn't whitelisted yet.
      }
      const aiMod = await import(
        "https://www.gstatic.com/firebasejs/11.14.0/firebase-ai.js"
      );
      return aiMod.getAI(app, { backend: new aiMod.GoogleAIBackend() });
    })();
  }
  const ai = await aiClientPromise;
  const aiMod = await import(
    "https://www.gstatic.com/firebasejs/11.14.0/firebase-ai.js"
  );
  const config = responseMimeType ? { generationConfig: { responseMimeType } } : undefined;
  return aiMod.getGenerativeModel(ai, {
    model: "gemini-2.5-flash",
    ...(config || {})
  });
}

function buildBusinessContextPrompt() {
  const prof = activeProfile();
  const analytics = computeLedgerAnalytics(state.ledgerPeriod || "month");
  const invoices = profileScoped(state.invoices);
  const expenses = profileScoped(state.expenses);
  const parties = profileScoped(state.parties);
  const stock = profileScoped(state.inventory);

  const billed = invoices.reduce((s, i) => s + Number(i.grandTotal || 0), 0);
  const collected = invoices.reduce((s, i) => s + Number(i.paidAmount || 0), 0);
  const receivables = Math.max(0, billed - collected);
  const outGst = analytics.invoices.reduce((s, i) => s + Number(i.gstTotal || 0), 0);
  const inGst = analytics.expenses.reduce((s, e) => s + Number(e.gstInputAmount || 0), 0);

  const topParties = analytics.parties
    .filter((p) => p.sales > 0 || p.due > 0)
    .slice(0, 8)
    .map(
      (p) =>
        `- ${p.name} (${p.type}): Sales ${inr(p.sales)}, Profit ${inr(p.profit)} (${fmtPct(p.pct)}), Due ${inr(p.due)}`
    )
    .join("\n");

  const lowStock = stock
    .filter((s) => Number(s.stockQty || 0) <= Number(s.lowStockThreshold || 5))
    .slice(0, 8)
    .map((s) => `- ${s.name} (${s.sku}): ${fmtQty(s.stockQty)} ${s.unit} @ ${inr(s.salePrice)}`)
    .join("\n");

  return [
    `You are Finboss AI Business Assistant for "${prof.businessName || "Finboss Billing No.1 ERP"}" (${isPlainNonGst() ? "Plain Non-GST" : "GST Registered"}, State: ${prof.state || "India"}).`,
    `Period (${analytics.rangeLabel}): Sales ${inr(analytics.totalSales)}, Net P&L ${inr(analytics.netProfit)} (${fmtPct(analytics.netPct)}), Expenses ${inr(analytics.totalExpenses)}, Output GST ${inr(outGst)}, Input ITC ${inr(inGst)}, Net GST ${inr(Math.max(0, outGst - inGst))}.`,
    `Overall Receivables: ${inr(receivables)} across ${invoices.length} invoice(s), Stock Valuation: ${inr(analytics.stockValue)} (${stock.length} SKUs), Parties: ${parties.length}, Operating Expenses: ${expenses.length}.`,
    topParties ? `Top Parties:\n${topParties}` : "Top Parties: None yet.",
    lowStock ? `Low/Out Stock Alerts:\n${lowStock}` : "Low Stock Alerts: All stock healthy.",
    `Respond concisely in crisp Hinglish/English with INR formatting and bullet points.`
  ].join("\n\n");
}

function matchCatalogAndParty(draft) {
  const parties = profileScoped(state.parties);
  const stock = profileScoped(state.inventory);
  const qParty = String(draft.partyName || "").trim().toLowerCase();
  const matchedParty = qParty
    ? parties.find((p) => String(p.name || "").toLowerCase().includes(qParty) || qParty.includes(String(p.name || "").toLowerCase()))
    : null;

  const lineItems = (draft.lineItems || []).map((l) => {
    const qName = String(l.name || "").trim().toLowerCase();
    const matchedItem = qName
      ? stock.find(
          (it) =>
            String(it.name || "").toLowerCase().includes(qName) ||
            qName.includes(String(it.name || "").toLowerCase()) ||
            (l.sku && String(it.sku || "").toLowerCase() === String(l.sku).toLowerCase())
        )
      : null;
    const qty = Math.max(0.01, Number(l.qty || 1));
    const unitPrice = Number(l.unitPrice || matchedItem?.salePrice || 0);
    const gstRate = isPlainNonGst()
      ? 0
      : Number(l.gstRatePercent ?? matchedItem?.gstRatePercent ?? 18);
    return {
      itemId: matchedItem?.id || "",
      matchedInventoryId: matchedItem?.id || "",
      name: matchedItem?.name || l.name || "Item",
      sku: matchedItem?.sku || l.sku || "",
      hsnCode: matchedItem?.hsnCode || l.hsnCode || "",
      unit: matchedItem?.unit || l.unit || "PCS",
      qty,
      unitPrice,
      gstRatePercent: gstRate
    };
  });

  return {
    ...draft,
    partyId: matchedParty?.id || draft.partyId || "",
    partyName: matchedParty?.name || draft.partyName || "",
    partyGstin: matchedParty?.gstin || draft.partyGstin || "",
    lineItems
  };
}

function fallbackLocalCommandParse(text) {
  const lower = text.toLowerCase();
  const isExpense = /(petrol|diesel|rent|salary|payroll|electricity|freight|transport|tea|coffee|bill pay|paid|kharch|expense|repair|purchase)/.test(lower);
  const nums = (text.match(/\d+(?:\.\d+)?/g) || []).map(Number);
  const plain = isPlainNonGst();

  if (isExpense) {
    const amt = nums[0] || 500;
    let cat = "other";
    if (/rent|kiraya/.test(lower)) cat = "rent";
    else if (/salary|staff|payroll/.test(lower)) cat = "payroll";
    else if (/petrol|diesel|fuel|freight|transport|auto|cab/.test(lower)) cat = "transport";
    else if (/light|electricity|internet|wifi|phone/.test(lower)) cat = "utilities";
    else if (/purchase|maal|stock/.test(lower)) cat = "purchases";
    const gstMatch = text.match(/(\d{1,2})\s*%/);
    const gstInputAmount = !plain && gstMatch ? Math.round((amt * Number(gstMatch[1])) / (100 + Number(gstMatch[1]))) : 0;
    return {
      intent: "expense",
      confidenceNote: "Parsed via local rule matcher",
      title: text.trim().slice(0, 60),
      category: cat,
      paymentMethod: /cash|nagad/.test(lower) ? "cash" : /card/.test(lower) ? "card" : "upi",
      status: /udhar|pending|due|baad/.test(lower) ? "pending" : "settled",
      amount: amt,
      gstInputAmount,
      partyName: "",
      notes: "Voice/Text Quick Expense"
    };
  }

  // Try matching known item or extracting qty + rate
  const stock = profileScoped(state.inventory);
  const matchedItem = stock.find((it) => lower.includes(String(it.name || "").toLowerCase()));
  const qty = nums.length >= 1 ? Math.max(1, nums[0]) : 1;
  const price = nums.length >= 2 ? nums[1] : Number(matchedItem?.salePrice || 500);
  const gstMatch = text.match(/(\d{1,2})\s*%/);
  const gstRate = plain ? 0 : gstMatch ? Number(gstMatch[1]) : Number(matchedItem?.gstRatePercent ?? 18);

  const partyMatch = text.match(/(?:ko|to|for|customer|party)\s+([a-zA-Z0-9\s]{2,30})/i);
  const extractedParty = partyMatch ? partyMatch[1].replace(/\b(ka|ki|ke|bill|invoice|credit|upi|cash)\b/gi, "").trim() : "";

  return matchCatalogAndParty({
    intent: "invoice",
    confidenceNote: "Parsed via local rule matcher",
    partyName: extractedParty || "Walk-in Customer",
    paymentMethod: /udhar|credit|due/.test(lower) ? "credit" : /cash|nagad/.test(lower) ? "cash" : "upi",
    discountAmount: 0,
    lineItems: [
      {
        name: matchedItem?.name || text.trim().slice(0, 40),
        qty,
        unitPrice: price,
        gstRatePercent: gstRate
      }
    ]
  });
}

function cleanJsonText(raw) {
  const txt = String(raw || "").trim();
  const fenced = txt.match(/```(?:json)?\s*([\s\S]*?)```/i);
  return (fenced ? fenced[1] : txt).trim();
}

export function renderAiCopilot() {
  document.querySelectorAll("[data-ai-mode]").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.aiMode === state.aiMode);
  });
  if (document.querySelector("#tab-ai.active")) {
    document.querySelectorAll("[data-sub-ai]").forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.subAi === state.aiMode);
    });
  }
  $("ai-chat-wrap").classList.toggle("hidden", state.aiMode !== "chat");
  $("ai-voice-wrap").classList.toggle("hidden", state.aiMode !== "voice");
  $("ai-ocr-wrap").classList.toggle("hidden", state.aiMode !== "ocr");

  // Chat log
  const log = $("ai-chat-log");
  log.innerHTML = (state.aiMessages || [])
    .map(
      (m) => `
      <div class="ai-bubble ${m.isUser ? "ai-user" : "ai-bot"}">
        <div class="ai-role">${m.isUser ? "You" : "Finboss AI Copilot"}</div>
        <div class="ai-text">${escapeHtml(m.text).replace(/\n/g, "<br/>")}</div>
      </div>`
    )
    .join("");
  log.scrollTop = log.scrollHeight;
  $("ai-chat-send").disabled = state.aiStreaming;
  $("ai-chat-send").textContent = state.aiStreaming ? "Thinking..." : "Send";

  // Voice result card
  const vBox = $("ai-voice-result");
  const vDraft = state.aiVoiceDraft;
  if (!vDraft) {
    vBox.innerHTML = `<p class="muted">Speak or type a Hindi/Hinglish/English command like <em>"Ramesh Traders ko 5 Cement Bag 400 rupaye credit bill banao"</em> or <em>"Petrol 500 cash kharch"</em>.</p>`;
  } else if (vDraft.intent === "expense") {
    vBox.innerHTML = `
      <div class="workspace-slot-card">
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <span class="pill pill-partial">EXPENSE DRAFT</span>
          <span class="muted">${escapeHtml(vDraft.confidenceNote || "AI Parsed")}</span>
        </div>
        <h4 style="margin:8px 0 4px;">${escapeHtml(vDraft.title)}</h4>
        <div class="mono bold">${inr(vDraft.amount)} • ${escapeHtml(String(vDraft.category || "other").toUpperCase())} • ${escapeHtml(String(vDraft.paymentMethod || "upi").toUpperCase())}</div>
        ${vDraft.gstInputAmount > 0 ? `<div class="muted">Estimated GST ITC: ${inr(vDraft.gstInputAmount)}</div>` : ""}
        <div style="margin-top:10px;display:flex;gap:8px;">
          <button id="ai-voice-open-exp" class="btn btn-primary btn-sm">Review &amp; Save Expense</button>
        </div>
      </div>`;
    $("ai-voice-open-exp").onclick = () => openExpenseModal(null, vDraft);
  } else {
    const sub = (vDraft.lineItems || []).reduce((s, l) => s + Number(l.qty || 0) * Number(l.unitPrice || 0), 0);
    const gst = (vDraft.lineItems || []).reduce(
      (s, l) => s + Number(l.qty || 0) * Number(l.unitPrice || 0) * (Number(l.gstRatePercent || 0) / 100),
      0
    );
    vBox.innerHTML = `
      <div class="workspace-slot-card">
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <span class="pill pill-paid">INVOICE DRAFT</span>
          <span class="muted">${escapeHtml(vDraft.confidenceNote || "AI Parsed")}</span>
        </div>
        <h4 style="margin:8px 0 4px;">Customer: ${escapeHtml(vDraft.partyName || "Walk-in Customer")}</h4>
        <div class="muted">Mode: ${escapeHtml(String(vDraft.paymentMethod || "upi").toUpperCase())} • ${(vDraft.lineItems || []).length} line item(s)</div>
        <ul style="margin:8px 0;padding-left:18px;font-size:13px;">
          ${(vDraft.lineItems || [])
            .map((l) => `<li><strong>${escapeHtml(l.name)}</strong> — ${fmtQty(l.qty)} ${escapeHtml(l.unit || "PCS")} × ${inr(l.unitPrice)} (GST ${Number(l.gstRatePercent || 0)}%)</li>`)
            .join("")}
        </ul>
        <div class="mono bold">Estimated Total: ${inr(sub + gst - Number(vDraft.discountAmount || 0))}</div>
        <div style="margin-top:10px;display:flex;gap:8px;">
          <button id="ai-voice-open-inv" class="btn btn-primary btn-sm">Open in Invoice Builder</button>
        </div>
      </div>`;
    $("ai-voice-open-inv").onclick = () => openInvoiceModal(null, vDraft);
  }

  // OCR preview + result
  const preview = $("ai-ocr-preview");
  if (state.aiOcrPreviewUrl) {
    preview.src = state.aiOcrPreviewUrl;
    preview.classList.remove("hidden");
  } else {
    preview.classList.add("hidden");
  }
  const ocrRes = $("ai-ocr-result");
  const bill = state.aiOcrBill;
  if (!bill) {
    ocrRes.innerHTML = `<p class="muted">Upload a supplier bill, receipt, or invoice photo to extract Vendor, GSTIN, Invoice #, Items, and GST ITC automatically.</p>`;
  } else {
    ocrRes.innerHTML = `
      <div class="workspace-slot-card">
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <span class="pill pill-info">EXTRACTED BILL OCR</span>
          <span class="mono muted">${escapeHtml(bill.invoiceNumber || "NO-REF")} • ${escapeHtml(bill.invoiceDate || "")}</span>
        </div>
        <h4 style="margin:8px 0 4px;">Vendor: ${escapeHtml(bill.vendorName || "Unknown Vendor")}</h4>
        <div class="muted mono">GSTIN: ${escapeHtml(bill.vendorGstin || "Not detected")}</div>
        <div style="margin:8px 0;" class="mono bold">
          Total: ${inr(bill.grandTotal)} • Subtotal: ${inr(bill.subtotal)} • GST Tax: ${inr(bill.gstTotal)}
        </div>
        ${
          (bill.lineItems || []).length
            ? `<ul style="margin:6px 0 10px;padding-left:18px;font-size:13px;">${bill.lineItems
                .map((l) => `<li>${escapeHtml(l.name)} — ${fmtQty(l.qty)} × ${inr(l.unitPrice)} (${Number(l.gstRatePercent || 0)}% GST)</li>`)
                .join("")}</ul>`
            : ""
        }
        <div style="display:flex;gap:8px;flex-wrap:wrap;">
          <button id="ai-ocr-save-exp" class="btn btn-primary btn-sm">Save as Purchase Expense (+ITC)</button>
          <button id="ai-ocr-open-inv" class="btn btn-secondary btn-sm">Open as Sale Invoice</button>
        </div>
      </div>`;
    $("ai-ocr-save-exp").onclick = () =>
      openExpenseModal(null, {
        title: `Purchase: ${bill.vendorName || "Supplier Bill"}`.slice(0, 100),
        category: "purchases",
        paymentMethod: "upi",
        status: "settled",
        partyName: bill.vendorName || "",
        referenceNo: bill.invoiceNumber || `OCR-${String(Date.now()).slice(-5)}`,
        amount: Number(bill.grandTotal || bill.subtotal || 0),
        gstInputAmount: Number(bill.gstTotal || 0),
        notes: `Scanned via Finboss OCR (${bill.vendorGstin || "No GSTIN"})`
      });
    $("ai-ocr-open-inv").onclick = () =>
      openInvoiceModal(
        null,
        matchCatalogAndParty({
          invoiceNumber: bill.invoiceNumber || "",
          partyName: bill.vendorName || "",
          partyGstin: bill.vendorGstin || "",
          lineItems: (bill.lineItems || []).length
            ? bill.lineItems
            : [{ name: bill.vendorName || "Scanned Item", qty: 1, unitPrice: Number(bill.subtotal || bill.grandTotal || 0), gstRatePercent: 18 }]
        })
      );
  }
}

async function sendAiChatQuestion(rawQuestion) {
  const q = String(rawQuestion || "").trim();
  if (!q || state.aiStreaming) return;
  state.aiMessages.push({ id: `u_${Date.now()}`, isUser: true, text: q });
  const botId = `b_${Date.now() + 1}`;
  state.aiMessages.push({ id: botId, isUser: false, text: "Analyzing your Finboss ERP data..." });
  state.aiStreaming = true;
  $("ai-chat-input").value = "";
  renderAiCopilot();

  try {
    const model = await getAiModel();
    const prompt = `${buildBusinessContextPrompt()}\n\nUser Question: ${q}`;
    const result = await model.generateContentStream(prompt);
    let full = "";
    for await (const chunk of result.stream) {
      full += chunk.text();
      const target = state.aiMessages.find((m) => m.id === botId);
      if (target) target.text = full;
      renderAiCopilot();
    }
    if (!full.trim()) {
      const target = state.aiMessages.find((m) => m.id === botId);
      if (target) target.text = "Response empty. Please try rephrasing your question.";
    }
  } catch (err) {
    const analytics = computeLedgerAnalytics(state.ledgerPeriod || "month");
    const target = state.aiMessages.find((m) => m.id === botId);
    if (target) {
      target.text = [
        `Cloud Gemini stream unreachable from this origin (${err?.message || "Check Firebase App Check / authorized domains"}).`,
        `Live Finboss Business Summary (${analytics.rangeLabel}):`,
        `• Sales: ${inr(analytics.totalSales)} | Net P&L: ${inr(analytics.netProfit)} (${fmtPct(analytics.netPct)})`,
        `• Expenses: ${inr(analytics.totalExpenses)} | Stock Value: ${inr(analytics.stockValue)}`
      ].join("\n");
    }
  } finally {
    state.aiStreaming = false;
    renderAiCopilot();
  }
}

async function parseVoiceCommand(commandText) {
  const text = String(commandText || "").trim();
  if (!text) return showToast("Enter or speak a billing / expense command first.");
  $("ai-voice-parse-btn").disabled = true;
  $("ai-voice-parse-btn").textContent = "Parsing...";

  try {
    const stockNames = profileScoped(state.inventory)
      .slice(0, 20)
      .map((i) => `${i.name} (₹${i.salePrice}/${i.unit}, GST ${i.gstRatePercent}%)`)
      .join("; ");
    const partyNames = profileScoped(state.parties)
      .slice(0, 20)
      .map((p) => p.name)
      .join(", ");

    const model = await getAiModel("application/json");
    const prompt = `Convert this Indian shopkeeper voice/text command (English/Hindi/Hinglish) into strict JSON.
Catalog SKUs: ${stockNames || "None"}
Known Parties: ${partyNames || "None"}
Allowed expense categories: ${EXPENSE_CATEGORIES.map((c) => c.key).join(", ")}

If it's a sale bill/invoice return JSON:
{"intent":"invoice","partyName":"...","paymentMethod":"upi|cash|card|credit","discountAmount":0,"lineItems":[{"name":"...","qty":1,"unitPrice":100,"gstRatePercent":18}]}

If it's an expense/purchase/payment paid return JSON:
{"intent":"expense","title":"...","category":"rent|payroll|utilities|purchases|transport|marketing|tax|other","paymentMethod":"upi|cash|bank|card","status":"settled|pending","amount":500,"gstInputAmount":0,"partyName":""}

Command: "${text}"`;

    const res = await model.generateContent(prompt);
    const parsed = JSON.parse(cleanJsonText(res.response.text()));
    if (parsed.intent === "expense") {
      state.aiVoiceDraft = { ...parsed, confidenceNote: "Gemini AI Parsed" };
    } else {
      state.aiVoiceDraft = matchCatalogAndParty({ ...parsed, confidenceNote: "Gemini AI Parsed" });
    }
  } catch (_) {
    state.aiVoiceDraft = fallbackLocalCommandParse(text);
  } finally {
    $("ai-voice-parse-btn").disabled = false;
    $("ai-voice-parse-btn").textContent = "Parse Command with AI";
    renderAiCopilot();
  }
}

async function runBillOcrScan(file) {
  if (!file) return showToast("Select a bill photo first.");
  $("ai-ocr-scan-btn").disabled = true;
  $("ai-ocr-scan-btn").textContent = "Scanning Bill...";
  try {
    const base64 = await new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        const res = String(reader.result || "");
        resolve(res.split(",")[1] || "");
      };
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });

    const model = await getAiModel("application/json");
    const prompt = `Extract Indian invoice/receipt fields as JSON:
{"vendorName":"","vendorGstin":"","invoiceNumber":"","invoiceDate":"","subtotal":0,"gstTotal":0,"grandTotal":0,"lineItems":[{"name":"","qty":1,"unitPrice":0,"gstRatePercent":18}]}`;

    const res = await model.generateContent([
      prompt,
      { inlineData: { mimeType: file.type || "image/jpeg", data: base64 } }
    ]);
    state.aiOcrBill = JSON.parse(cleanJsonText(res.response.text()));
    showToast("Bill photo scanned successfully!");
  } catch (err) {
    showToast(`OCR error: ${err?.message || "Unable to scan image"}`);
  } finally {
    $("ai-ocr-scan-btn").disabled = false;
    $("ai-ocr-scan-btn").textContent = "Scan Bill Photo with AI";
    renderAiCopilot();
  }
}

export function bindAiCopilotEvents() {
  document.querySelectorAll("[data-ai-mode]").forEach((btn) => {
    btn.onclick = () => {
      state.aiMode = btn.dataset.aiMode;
      renderAiCopilot();
    };
  });

  $("ai-chat-send").onclick = () => sendAiChatQuestion($("ai-chat-input").value);
  $("ai-chat-input").onkeydown = (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendAiChatQuestion($("ai-chat-input").value);
    }
  };
  document.querySelectorAll("[data-ai-chip]").forEach((chip) => {
    chip.onclick = () => sendAiChatQuestion(chip.dataset.aiChip);
  });

  document.querySelectorAll("[data-voice-sample]").forEach((btn) => {
    btn.onclick = () => {
      $("ai-voice-text").value = btn.dataset.voiceSample;
      parseVoiceCommand(btn.dataset.voiceSample);
    };
  });
  $("ai-voice-parse-btn").onclick = () => parseVoiceCommand($("ai-voice-text").value);

  const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
  let recognition = null;
  $("ai-voice-mic-btn").onclick = () => {
    if (!SpeechRec) {
      return showToast("Browser Voice Mic requires Chrome/Edge. You can still type Hinglish commands below.");
    }
    if (recognition) {
      recognition.stop();
      recognition = null;
      $("ai-voice-mic-btn").textContent = "Start Mic";
      return;
    }
    recognition = new SpeechRec();
    recognition.lang = $("ai-voice-lang").value || "en-IN";
    recognition.interimResults = false;
    recognition.maxAlternatives = 1;
    $("ai-voice-mic-btn").textContent = "Listening... (Stop)";
    recognition.onresult = (ev) => {
      const transcript = ev.results?.[0]?.[0]?.transcript || "";
      $("ai-voice-text").value = transcript;
      parseVoiceCommand(transcript);
    };
    recognition.onerror = (ev) => {
      showToast(`Mic error: ${ev.error || "Permission denied"}`);
    };
    recognition.onend = () => {
      recognition = null;
      $("ai-voice-mic-btn").textContent = "Start Mic";
    };
    recognition.start();
  };

  let selectedOcrFile = null;
  $("ai-ocr-file").onchange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    selectedOcrFile = file;
    state.aiOcrPreviewUrl = URL.createObjectURL(file);
    renderAiCopilot();
  };
  $("ai-ocr-scan-btn").onclick = () => runBillOcrScan(selectedOcrFile);
}
