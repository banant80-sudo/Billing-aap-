import { state } from "./state.js";
import { $, activeProfile, inr, isPlainNonGst, isValidGstin, profileScoped, showToast } from "./utils.js";
import { bindBankPaymentEvents } from "./bank-payments.js";

const indiaMonth = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Asia/Kolkata",
  month: "2-digit",
  year: "numeric"
});

export function returnMonth(date) {
  if (!(date instanceof Date) || !Number.isFinite(date.getTime())) return null;
  return indiaMonth.format(date).replace("/", "");
}

const indiaDate = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Asia/Kolkata",
  day: "2-digit",
  month: "2-digit",
  year: "numeric"
});

function recordDate(record) {
  const created = record.createdAt;
  if (typeof created?.toDate === "function") return created.toDate();
  if (typeof created?.seconds === "number") return new Date(created.seconds * 1000);
  return typeof created === "number" && created > 0 ? new Date(created) : null;
}

function periodRecords(records, period) {
  return profileScoped(records).filter((record) => returnMonth(recordDate(record)) === period);
}

function toPaise(amount) {
  const value = Number(amount ?? 0);
  return Number.isFinite(value) && value >= 0 ? Math.round(value * 100) : 0;
}

function salesReviewIssues(invoice, numberCounts) {
  const issues = [];
  const number = String(invoice.invoiceNumber || "").trim().toUpperCase();
  if (!/^[A-Z0-9/-]{1,16}$/.test(number) || !/[A-Z0-9]/.test(number)) {
    issues.push("Check invoice number");
  } else if (numberCounts.get(number) > 1) {
    issues.push("Duplicate invoice number in this month");
  }
  if (invoice.partyGstin && !isValidGstin(invoice.partyGstin)) {
    issues.push("Check buyer GSTIN format");
  }
  if (!String(invoice.partyState || "").trim()) issues.push("Buyer state missing; verify place of supply");
  if (!invoice.lineItems?.length) issues.push("Item lines missing");
  else if (invoice.lineItems.some((line) => !/^\d{4}(?:\d{2}){0,2}$/.test(String(line.hsnCode || "").trim()))) {
    issues.push("Review missing or malformed HSN/SAC");
  }
  return issues;
}

export function buildSalesReviewCsv(paper) {
  const columns = [
    "Business GSTIN", "Return month", "Invoice number", "Recorded date (IST)",
    "Buyer GSTIN", "Buyer state (review place of supply)", "Recorded tax mode",
    "Invoice taxable amount", "Invoice total tax", "Invoice value", "Item number",
    "HSN/SAC", "Unit", "Quantity", "GST rate %", "Item taxable amount", "Item tax", "Review notes"
  ];
  const rows = paper.salesRows.flatMap((invoice) => {
    const lines = invoice.items.length ? invoice.items : [null];
    return lines.map((line, index) => [
      paper.gstin, paper.period, invoice.invoiceNumber, invoice.recordedDate,
      invoice.recordedBuyerGstin, invoice.recordedBuyerState, invoice.recordedTaxMode,
      index === 0 ? invoice.taxableValue : "", index === 0 ? invoice.outputTax : "",
      index === 0 ? invoice.invoiceValue : "", line ? index + 1 : "",
      line?.recordedHsn, line?.recordedUnit, line?.quantity, line?.recordedRate,
      line?.taxableValue, line?.tax, index === 0 ? invoice.reviewIssues.join(" | ") : ""
    ]);
  });
  return [columns, ...rows].map((row) => row.map((value) => {
    const raw = String(value ?? "");
    const safe = /^[\s\uFEFF]*[=+@-]/.test(raw) && typeof value === "string" ? "'" + raw : raw;
    return '"' + safe.replaceAll('"', '""') + '"';
  }).join(",")).join("\r\n") + "\r\n";
}

export function buildWorkingPapers(gstin, period) {
  const sales = periodRecords(state.invoices, period)
    .filter((invoice) => invoice.taxMode === "igst" || invoice.taxMode === "cgst_sgst");
  const expenses = periodRecords(state.expenses, period);
  const sum = (rows, key) => rows.reduce((total, row) => total + toPaise(row[key]), 0) / 100;
  const meta = { gstin, period, documentType: "INTERNAL_WORKING_PAPER", periodBasis: "record creation date (IST)" };
  const numberCounts = new Map();
  for (const invoice of sales) {
    const number = String(invoice.invoiceNumber || "").trim().toUpperCase();
    numberCounts.set(number, (numberCounts.get(number) || 0) + 1);
  }
  const salesRows = sales.map((invoice) => ({
    invoiceNumber: invoice.invoiceNumber,
    recordedDate: indiaDate.format(recordDate(invoice)),
    reviewIssues: salesReviewIssues(invoice, numberCounts),
    recordedBuyerGstin: invoice.partyGstin || "",
    recordedBuyerState: invoice.partyState || "",
    recordedTaxMode: invoice.taxMode,
    taxableValue: toPaise(invoice.subtotal) / 100,
    outputTax: toPaise(invoice.gstTotal) / 100,
    invoiceValue: toPaise(invoice.grandTotal) / 100,
    items: (invoice.lineItems || []).map((line) => ({
      recordedHsn: line.hsnCode || "",
      recordedUnit: line.unit || "",
      quantity: line.qty,
      recordedRate: line.gstRatePercent,
      taxableValue: toPaise(line.taxableAmount) / 100,
      tax: toPaise(line.gstAmount) / 100
    }))
  }));
  const taxTotals = {
    taxableSales: sum(sales, "subtotal"),
    recordedOutputTax: sum(sales, "gstTotal"),
    recordedInterstateOutputTax: sum(sales.filter((invoice) => invoice.taxMode === "igst"), "gstTotal"),
    recordedIntrastateOutputTax: sum(sales.filter((invoice) => invoice.taxMode === "cgst_sgst"), "gstTotal"),
    recordedExpenseTaxBeforeReconciliation: sum(expenses, "gstInputAmount")
  };
  return {
    sales: { ...meta, includedInvoices: sales.length, salesRows },
    taxes: { ...meta, ...taxTotals }
  };
}

function downloadFile(filename, contents, type) {
  const blob = new Blob([contents], { type });
  const href = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = href;
  link.download = filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(href), 1000);
}

export function renderGstFiling() {
  const profile = activeProfile();
  const gstin = String(profile.gstin || "").trim().toUpperCase();
  const periodInput = $("gst-return-period");
  const period = periodInput.value.trim();
  const plain = isPlainNonGst();
  const validPeriod = /^(0[1-9]|1[0-2])20\d{2}$/.test(period);
  const message = plain ? "Select a GST business profile for working papers."
    : !isValidGstin(gstin) ? "Enter the business GSTIN in workspace settings."
    : !validPeriod ? "Enter a valid return month in MMYYYY format." : "";
  $("gst-filing-mode-badge").textContent = plain ? "Non-GST profile" : "GST profile";
  $("gst-filing-gstin").value = gstin;
  $("gst-portal-status").textContent = message;
  $("gst-portal-status").classList.toggle("hidden", !message);
  $("gst-dl-gstr1-btn").disabled = Boolean(message);
  $("gst-dl-gstr3b-btn").disabled = Boolean(message);

  const papers = message ? null : buildWorkingPapers(gstin, period);
  const sales = papers?.sales;
  const taxes = papers?.taxes;
  const reviewCount = sales?.salesRows.filter((invoice) => invoice.reviewIssues.length > 0).length ?? 0;
  $("gst-dl-sales-csv-btn").disabled = !sales?.includedInvoices;
  $("gst-gstr1-meta").textContent = sales
    ? `${sales.includedInvoices} GST invoice(s) · ${reviewCount} flagged for review`
    : "";
  $("gst-gstr1-kpis").textContent = taxes ? `Recorded taxable sales ${inr(taxes.taxableSales)}` : "";
  $("gst-gstr3b-kpis").textContent = taxes
    ? `Recorded output GST ${inr(taxes.recordedOutputTax)} · Expense tax to reconcile ${inr(taxes.recordedExpenseTaxBeforeReconciliation)}`
    : "";
  $("gst-gstr1-preview").textContent = sales ? JSON.stringify(sales, null, 2) : "";
  $("gst-gstr3b-preview").textContent = taxes ? JSON.stringify(taxes, null, 2) : "";
  return papers;
}

export function bindGstFilingEvents() {
  $("gst-return-period").value = returnMonth(new Date());
  $("gst-return-period").oninput = renderGstFiling;
  for (const [button, kind, key] of [
    ["gst-dl-gstr1-btn", "GSTR1", "sales"],
    ["gst-dl-gstr3b-btn", "GSTR3B", "taxes"]
  ]) {
    $(button).onclick = () => {
      const papers = renderGstFiling();
      if (!papers) return;
      const paper = papers[key];
      downloadFile(`FINBOSS_WORKING_PAPER_${kind}_${paper.gstin}_${paper.period}.json`,
        JSON.stringify(paper, null, 2), "application/json");
      showToast("Internal GST working paper downloaded.");
    };
  }
  $("gst-dl-sales-csv-btn").onclick = () => {
    const sales = renderGstFiling()?.sales;
    if (!sales?.includedInvoices) return;
    downloadFile(`FINBOSS_SALES_REVIEW_${sales.gstin}_${sales.period}.csv`,
      buildSalesReviewCsv(sales), "text/csv;charset=utf-8");
    showToast("Sales Review CSV downloaded.");
  };
  bindBankPaymentEvents();
}
