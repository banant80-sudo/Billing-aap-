import { state } from "./state.js";
import { $, activeProfile, profileScoped, toMs } from "./utils.js";

export function buildItrRows(year, profile, invoices, expenses, entries) {
  const from = Date.parse(`${year}-04-01T00:00:00+05:30`);
  const until = Date.parse(`${year + 1}-04-01T00:00:00+05:30`);
  const withinYear = (record) => {
    const ts = record.createdAt ? toMs(record.createdAt) : 0;
    return ts >= from && ts < until;
  };
  const sales = invoices.filter(withinYear);
  const costs = expenses.filter(withinYear);
  const purchases = costs.filter((row) => row.category === "purchases");
  const operating = costs.filter((row) => row.category !== "purchases");
  const sum = (rows, get) => rows.reduce((total, row) => total + Math.round((Number(get(row)) || 0) * 100), 0) / 100;
  const date = new Intl.DateTimeFormat("en-GB", { timeZone: "Asia/Kolkata", dateStyle: "short" });
  return [
    ["FINBOSS ITR WORKING PAPER", "Bookkeeping records for review"],
    ["Business", profile.businessName || "", "GSTIN", profile.gstin || ""],
    ["Financial year", `${year}-${year + 1}`, "Date basis", "Record creation date (IST)"],
    ["Book P&L inputs", "Amount (INR)"],
    ["Recorded invoice value before GST after discounts", sum(sales, (row) => Number(row.grandTotal || 0) - Number(row.gstTotal || 0))],
    ["Output GST on recorded invoices", sum(sales, (row) => row.gstTotal)],
    ["Purchase vouchers including recorded GST", sum(purchases, (row) => row.amount)],
    ["Other expense vouchers including recorded GST", sum(operating, (row) => row.amount)],
    ["Stock valuation, cost of goods sold, depreciation and tax adjustments", "Review separately before calculating final profit"],
    ["Bank statement", "Download the official statement from your bank and reconcile against the book entries below"],
    [],
    ["Type", "Recorded date (IST)", "Reference", "Party", "GSTIN recorded", "Gross amount (INR)", "GST recorded (INR)", "Payment method", "Status", "Invoice paid to date (INR)"],
    ...sales.map((row) => ["Sales invoice", date.format(toMs(row.createdAt)), row.invoiceNumber, row.partyName,
      row.partyGstin, row.grandTotal, row.gstTotal, row.paymentMethod, row.status, row.paidAmount]),
    ...costs.map((row) => [row.category === "purchases" ? "Purchase" : "Expense", date.format(toMs(row.createdAt)),
      row.referenceNo, row.partyName || row.title, "", row.amount, row.gstInputAmount, row.paymentMethod, row.status]),
    ...entries.filter(withinYear).map((row) => [`Quick entry for reconciliation (${row.category || "unclassified"})`,
      date.format(toMs(row.createdAt)), row.referenceCode, row.partyName || row.title, "", row.amount, "", "", row.status])
  ];
}

export function bindItrWorkingPaper() {
  const now = new Date(Date.now() + 330 * 60_000);
  const currentFy = now.getUTCFullYear() - (now.getUTCMonth() < 3 ? 1 : 0);
  $("itr-fy-start").value = currentFy - 1;
  $("itr-working-paper").onclick = () => {
    const year = Number($("itr-fy-start").value);
    if (!Number.isInteger(year) || year < 2000 || year > currentFy) {
      $("itr-working-paper-status").textContent = "Enter a valid financial year start (for example, 2025 for FY 2025–26).";
      return;
    }
    const rows = buildItrRows(year, activeProfile(), profileScoped(state.invoices), profileScoped(state.expenses), profileScoped(state.dashboardEntries));
    const csv = rows.map((row) => row.map((value) => {
      const raw = String(value ?? "");
      const safe = typeof value === "string" && /^[\s\uFEFF]*[=+@-]/.test(raw) ? "'" + raw : raw;
      return '"' + safe.replaceAll('"', '""') + '"';
    }).join(",")).join("\r\n");
    const url = URL.createObjectURL(new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = `Finboss-ITR-Working-Paper-FY-${year}-${year + 1}.csv`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    $("itr-working-paper-status").textContent = "Bookkeeping working paper downloaded.";
  };
}
