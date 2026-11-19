# Finboss Billing No.1 ERP — Desktop Web Portal

This folder contains the browser-based **Desktop Website** edition of Finboss Billing No.1 ERP.
It connects directly to the exact same **Firebase Auth + Cloud Firestore** database (`gen-lang-client-0249346990` / database `ai-studio-android-finbosse-8c9fbc7b-98a3-412d-8ccf-3779b232a5b8`) as the Android APK, so invoices, stock, ledger parties, expenses, E-Invoice/E-Way Bills, and subscriptions sync live across desktop and mobile.

## Option 1: Run Immediately on Local Desktop Browser
From the repository root:
```bash
python3 -m http.server 8080 --directory web-portal
```
Then open `http://localhost:8080` in Chrome / Edge (`localhost` is pre-authorized for Google Sign-In).

## Option 2: Deploy Live to Firebase Hosting (`https://gen-lang-client-0249346990.web.app`)
```bash
firebase deploy --only hosting --config web-portal/firebase.hosting.json
```

## GST & Banking

Open **GST & Banking** on the web or **Reports → GST Report** on Android.
The official GST portal handles PMT-06, payment and returns. Use only the payment
options and NEFT/RTGS mandate it shows for that challan. Check CIN and the
Electronic Cash Ledger on the portal.

The downloaded working papers use the active business profile and record-creation
month in India. They contain bookkeeping figures for review. Reconcile purchases
with GSTR-2B, review tax heads and place of supply, and prepare the official
returns on the GST Portal. Use the IRP and e-way bill portal for official document
references. Finboss keeps prior locally generated references for review.

Business Banking opens external UPI and bank apps or websites for the user to
authorize. It does not auto-post or confirm transfers. Production integrations
require an empanelled GST Suvidha Provider for GST and a bank or payment provider
for transfers and a verified payment status. Use the provider's official
sandbox and a server-side integration before enabling automatic filing,
reconciliation or paid plan activation. Existing Firestore rules and old client
deployments also need review before enabling a live paid checkout.
