import { $ } from "./utils.js";

export function validBands(bands) {
  if (!Array.isArray(bands) || bands.length < 2) return false;
  let previous = 0;
  for (const [index, band] of bands.entries()) {
    if (!Array.isArray(band) || band.length !== 2) return false;
    const [limit, rate] = band;
    if (!Number.isFinite(rate) || rate < 0 || rate > 100) return false;
    if (index === bands.length - 1) return limit === null;
    if (!Number.isSafeInteger(limit) || limit <= previous) return false;
    previous = limit;
  }
  return false;
}

export function bindIncomeTaxSlabs() {
  const input = $("slabs-year");
  const table = $("slabs-rows");
  const status = $("slabs-status");
  const link = $("slabs-source");
  const india = new Date(Date.now() + 330 * 60_000);
  input.value = india.getUTCFullYear() - (india.getUTCMonth() < 3 ? 1 : 0);
  const currency = new Intl.NumberFormat("en-IN");
  let requestNumber = 0;
  async function refresh() {
    const request = ++requestNumber;
    table.replaceChildren();
    link.classList.add("hidden");
    status.textContent = "Loading published Finboss rates…";
    const year = input.value;
    if (!/^20\d{2}$/.test(year)) {
      status.textContent = "Enter a valid financial year start.";
      return;
    }
    try {
      const response = await fetch(new URL("../income-tax-slabs.json", import.meta.url), { cache: "no-store" });
      if (!response.ok) throw new Error("Failed to load rates");
      const data = await response.json();
      if (request !== requestNumber) return;
      const entry = data?.[year];
      if (!entry) {
        status.textContent = "Verified rates for this year are not available in Finboss yet.";
        return;
      }
      const source = new URL(entry.source);
      if (source.protocol !== "https:" || !["www.incometax.gov.in", "www.incometaxindia.gov.in"].includes(source.hostname)
          || !validBands(entry.bands) || !/^\d{4}-\d{2}-\d{2}$/.test(entry.checkedOn)) throw new Error("Invalid rates");
      let previous = 0;
      for (const [limit, rate] of entry.bands) {
        const row = table.insertRow();
        row.insertCell().textContent = limit === null ? `Above ₹${currency.format(previous)}`
          : previous === 0 ? `Up to ₹${currency.format(limit)}`
          : `Above ₹${currency.format(previous)} to ₹${currency.format(limit)}`;
        row.insertCell().textContent = `${rate}%`;
        previous = limit;
      }
      link.href = source.href;
      link.classList.remove("hidden");
      status.textContent = `FY ${year}–${Number(year) + 1} · Official source checked ${entry.checkedOn}.`;
    } catch {
      if (request === requestNumber) status.textContent = "Verified rates could not be loaded. Please refresh or check the official Income Tax Portal.";
    }
  }
  input.oninput = refresh;
  $("slabs-refresh").onclick = refresh;
  refresh();
}
