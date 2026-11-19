import { $ } from "./utils.js";

export function parsePaymentPaise(input) {
  const match = /^(\d{1,12})(?:\.(\d{1,2}))?$/.exec(input.trim());
  if (!match) return null;
  const paise = Number(match[1]) * 100 + Number((match[2] || "").padEnd(2, "0"));
  return paise > 0 ? paise : null;
}

export function buildUpiPaymentUri(vpa, paise) {
  const address = vpa.trim();
  if (!/^[a-z0-9._-]+@[a-z0-9]+$/i.test(address) || !Number.isSafeInteger(paise) || paise <= 0) {
    return null;
  }
  const rupees = Math.floor(paise / 100);
  const fraction = String(paise % 100).padStart(2, "0");
  return `upi://pay?pa=${encodeURIComponent(address)}&am=${rupees}.${fraction}&cu=INR`;
}

export function bindBankPaymentEvents() {
  const method = $("bank-method");
  const amount = $("bank-amount");
  const vpa = $("bank-upi-id");
  const upiLink = $("bank-upi-open");
  const render = () => {
    const selected = method.value;
    const paise = parsePaymentPaise(amount.value);
    const isUpi = selected === "upi";
    const isRtgs = selected === "rtgs";
    const belowRtgsMinimum = isRtgs && paise < 20000000;
    const upiUri = isUpi ? buildUpiPaymentUri(vpa.value, paise) : null;
    $("bank-amount-field").classList.toggle("hidden", !isUpi && !isRtgs);
    $("bank-upi-field").classList.toggle("hidden", !isUpi);
    $("bank-links").classList.toggle("hidden", isUpi || belowRtgsMinimum);
    upiLink.classList.toggle("hidden", !upiUri);
    if (upiUri) upiLink.href = upiUri;
    else upiLink.removeAttribute("href");

    const notes = {
      neft: "NEFT has no RBI minimum or maximum for account transfers. Check your bank's limits.",
      rtgs: belowRtgsMinimum
        ? "Enter a valid amount of at least ₹2,00,000 for RTGS."
        : "RTGS has a ₹2,00,000 RBI minimum. Enter the amount again and review it in your bank.",
      upi: "Enter the recipient UPI ID and amount, then confirm the displayed name and any limits in your UPI app.",
      netbanking: "Choose your bank. Your login and payment authorization take place on the bank's website."
    };
    $("bank-payment-note").textContent = notes[selected];
  };

  method.onchange = render;
  amount.oninput = render;
  vpa.oninput = render;
  render();
}
