import { DEFAULT_APP_SETTINGS, PLAN_SPECS, state } from "./state.js";

export const $ = (id) => document.getElementById(id);

export function escapeHtml(str) {
  return String(str ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

export function inr(value) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2
  }).format(Number(value || 0));
}

export function fmtQty(qty) {
  const n = Number(qty || 0);
  return Math.abs(n - Math.round(n)) < 0.001 ? String(Math.round(n)) : n.toFixed(2);
}

export function fmtPct(pct) {
  const n = Number(pct || 0);
  const sign = n > 0.05 ? "+" : n < -0.05 ? "-" : "";
  return `${sign}${Math.abs(n).toFixed(1)}%`;
}

export function toMs(ts) {
  if (!ts) return Date.now();
  if (typeof ts.toDate === "function") return ts.toDate().getTime();
  if (typeof ts.seconds === "number") return ts.seconds * 1000;
  return Number(ts) || Date.now();
}

export function showToast(message) {
  const banner = $("toast-banner");
  if (!banner) return;
  banner.textContent = message;
  banner.classList.remove("hidden");
  clearTimeout(showToast._t);
  showToast._t = setTimeout(() => banner.classList.add("hidden"), 4500);
}

export function openModal(html, maxWidthPx = 680) {
  const card = $("modal-container");
  if (!card) return;
  card.style.maxWidth = `${maxWidthPx}px`;
  card.innerHTML = html;
  $("modal-backdrop").classList.remove("hidden");
}

export function closeModal() {
  $("modal-backdrop").classList.add("hidden");
  $("modal-container").innerHTML = "";
}

export function normalizeWaPhone(rawPhone) {
  const digits = String(rawPhone || "").replace(/\D/g, "");
  if (digits.length === 10) return `91${digits}`;
  if (digits.length >= 11 && digits.length <= 15) return digits;
  return "";
}

export function shareToWhatsApp(text, rawPhone = "") {
  const phone = normalizeWaPhone(rawPhone);
  const url = phone
    ? `https://wa.me/${phone}?text=${encodeURIComponent(text)}`
    : `https://wa.me/?text=${encodeURIComponent(text)}`;
  window.open(url, "_blank", "noopener");
}

export function findPartyPhone(partyId, partyName) {
  const match = profileScoped(state.parties).find(
    (p) =>
      (partyId && p.id === partyId) ||
      (partyName && String(p.name).trim().toLowerCase() === String(partyName).trim().toLowerCase())
  );
  return match?.phone || "";
}

export function activeProfile() {
  return (
    state.profiles.find((p) => p.id === state.activeProfileId) ||
    state.profiles[0] ||
    state.rootProfile || {
      id: "main_1",
      businessName: "Finboss Enterprise",
      profileMode: "main_gst",
      state: "Maharashtra",
      monthlySalesTarget: 250000
    }
  );
}

export function isPlainNonGst() {
  const prof = activeProfile();
  return prof.id === "plain_1" || prof.profileMode === "plain_non_gst";
}

export function effectivePlanTier() {
  const rp = state.rootProfile || {};
  const tier = rp.planTier || "trial";
  const expires = Number(rp.planExpiresAtMs || 0);
  if (expires > 0 && Date.now() > expires) {
    return tier === "trial" ? "silver" : "expired";
  }
  return tier;
}

export function activePlanSpec() {
  return PLAN_SPECS[effectivePlanTier()] || PLAN_SPECS.silver;
}

export function daysRemaining() {
  const expires = Number(state.rootProfile?.planExpiresAtMs || 0);
  if (!expires) return 30;
  return Math.max(0, Math.ceil((expires - Date.now()) / 86400000));
}

export function profileScoped(list) {
  const pid = state.activeProfileId || "main_1";
  return (list || []).filter((docItem) => {
    const ref = docItem.businessProfileId || "";
    return ref === pid || (pid === "main_1" && ref === "");
  });
}

export function loadAppSettings() {
  try {
    const raw = localStorage.getItem("book_web_settings_v1");
    if (!raw) return { ...DEFAULT_APP_SETTINGS };
    return { ...DEFAULT_APP_SETTINGS, ...JSON.parse(raw) };
  } catch (_) {
    return { ...DEFAULT_APP_SETTINGS };
  }
}

export function saveAppSettings(next) {
  const merged = { ...DEFAULT_APP_SETTINGS, ...next };
  localStorage.setItem("book_web_settings_v1", JSON.stringify(merged));
  return merged;
}

export function isValidGstin(raw) {
  const clean = String(raw || "").trim().toUpperCase().replace(/\s+/g, "");
  return /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/.test(clean);
}

export function numberToIndianWords(amount) {
  const n = Math.round(Number(amount || 0));
  if (n <= 0) return "Zero Rupees Only";
  const ones = [
    "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
    "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
    "Seventeen", "Eighteen", "Nineteen"
  ];
  const tens = ["", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"];
  const twoDigits = (x) => (x < 20 ? ones[x] : `${tens[Math.floor(x / 10)]}${x % 10 ? " " + ones[x % 10] : ""}`);
  const threeDigits = (x) => {
    const h = Math.floor(x / 100);
    const r = x % 100;
    return `${h ? ones[h] + " Hundred" + (r ? " " : "") : ""}${r ? twoDigits(r) : ""}`;
  };
  let num = n;
  const crore = Math.floor(num / 10000000);
  num %= 10000000;
  const lakh = Math.floor(num / 100000);
  num %= 100000;
  const thousand = Math.floor(num / 1000);
  num %= 1000;
  const parts = [];
  if (crore) parts.push(`${twoDigits(crore)} Crore`);
  if (lakh) parts.push(`${twoDigits(lakh)} Lakh`);
  if (thousand) parts.push(`${twoDigits(thousand)} Thousand`);
  if (num) parts.push(threeDigits(num));
  return `${parts.join(" ").trim()} Rupees Only`;
}

export async function sha256Hex(text) {
  const data = new TextEncoder().encode(text);
  const hashBuffer = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(hashBuffer))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

export function estimateDistanceKm(fromCity, fromPin, toCity, toPin) {
  const fc = String(fromCity || "").trim().toLowerCase();
  const tc = String(toCity || "").trim().toLowerCase();
  const fp = String(fromPin || "").trim();
  const tp = String(toPin || "").trim();
  if (fp && fp === tp) return 15;
  if (fc && fc === tc && !fp) return 20;
  const hubs = {
    delhi: [28.61, 77.21], noida: [28.53, 77.39], gurugram: [28.46, 77.03],
    mumbai: [19.07, 72.88], pune: [18.52, 73.86], nagpur: [21.14, 79.09],
    surat: [21.17, 72.83], ahmedabad: [23.02, 72.57], bengaluru: [12.97, 77.59],
    bangalore: [12.97, 77.59], chennai: [13.08, 80.27], hyderabad: [17.38, 78.48],
    kolkata: [22.57, 88.36], bhubaneswar: [20.29, 85.82], cuttack: [20.46, 85.88],
    rourkela: [22.26, 84.85], sambalpur: [21.46, 83.98], jaipur: [26.91, 75.78],
    lucknow: [26.84, 80.94], patna: [25.59, 85.13], ranchi: [23.34, 85.31],
    raipur: [21.25, 81.63], indore: [22.71, 75.85], bhopal: [23.25, 77.41],
    chandigarh: [30.73, 76.77], kochi: [9.93, 76.26], guwahati: [26.14, 91.73]
  };
  const pinCoords = {
    11: [28.61, 77.21], 12: [28.46, 76.9], 14: [30.9, 75.85], 20: [28.53, 77.39],
    22: [26.84, 80.94], 30: [26.91, 75.78], 38: [23.02, 72.57], 39: [21.17, 72.83],
    40: [19.07, 72.88], 41: [18.52, 73.86], 44: [21.14, 79.09], 45: [22.71, 75.85],
    49: [21.25, 81.63], 50: [17.38, 78.48], 56: [12.97, 77.59], 60: [13.08, 80.27],
    68: [9.93, 76.26], 70: [22.57, 88.36], 75: [20.29, 85.82], 76: [21.46, 83.98],
    80: [25.59, 85.13], 83: [23.34, 85.31]
  };
  const resolveCoords = (city, pin) => {
    for (const [k, coords] of Object.entries(hubs)) {
      if (city.includes(k)) return coords;
    }
    if (pin.length >= 2) {
      const prefix = Number(pin.slice(0, 2));
      if (pinCoords[prefix]) return pinCoords[prefix];
    }
    return null;
  };
  const c1 = resolveCoords(fc, fp);
  const c2 = resolveCoords(tc, tp);
  if (c1 && c2) {
    const latDiff = (c1[0] - c2[0]) * 111;
    const lonDiff = (c1[1] - c2[1]) * 96;
    const straight = Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);
    return Math.min(3500, Math.max(15, Math.round(straight * 1.26)));
  }
  return 120;
}

export function normalizePhoneE164(countryCode, raw) {
  const trimmed = String(raw || "").trim().replace(/[\s-]/g, "");
  if (!trimmed) return null;
  let candidate;
  if (trimmed.startsWith("+")) {
    candidate = `+${trimmed.slice(1).replace(/\D/g, "")}`;
  } else {
    const ccDigits = String(countryCode || "+91").replace(/\D/g, "") || "91";
    const localDigits = trimmed.replace(/\D/g, "").replace(/^0+/, "");
    candidate = `+${ccDigits}${localDigits}`;
  }
  return /^\+[1-9][0-9]{7,14}$/.test(candidate) ? candidate : null;
}
