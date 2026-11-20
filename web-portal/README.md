# Finboss Billing No.1 ERP — Desktop Web Portal

This folder contains the browser-based **Desktop Website** edition of Finboss Billing No.1 ERP.
It connects directly to the exact same **Firebase Auth + Cloud Firestore** database (`gen-lang-client-0249346990` / database `ai-studio-android-finbosse-8c9fbc7b-98a3-412d-8ccf-3779b232a5b8`) as the Android APK, so invoices, stock, ledger parties, expenses, E-Invoice/E-Way Bills, and subscriptions sync live across desktop and mobile.

## Firebase configuration

Store the replacement Android API key as the GitHub Actions secret
`FINBOSS_ANDROID_FIREBASE_API_KEY`. For a local Android build, set that
environment variable and run `python3 scripts/configure_firebase.py android`
from the repository root before running Gradle. The generated Android
configuration is ignored by Git. GitHub publishes a release only when a
maintainer manually requests it and selects the publish option.

Android release signing uses a persistent keystore with the alias `upload`.
Save its base64 data as `FINBOSS_RELEASE_KEYSTORE_BASE64` and its two
passwords as `FINBOSS_RELEASE_KEYSTORE_PASSWORD` and
`FINBOSS_RELEASE_KEY_PASSWORD` in GitHub Actions secrets. Register the
keystore's SHA-1 for the Finboss Android app in Firebase before testing Google
Sign-In. Keep the original signing key to update an existing installation.

If Firebase supplies an updated Android configuration, its base64 JSON can be
saved in `GOOGLE_SERVICES_JSON_BASE64`. The build uses its OAuth metadata
and still uses the replacement API key from `FINBOSS_ANDROID_FIREBASE_API_KEY`.

For the portal, configure `FINBOSS_WEB_FIREBASE_API_KEY` in the hosting build
environment with the project's restricted Browser key. Run the following
command before serving or deploying the portal:

```bash
python3 scripts/configure_firebase.py web
```

The generated browser module is ignored by Git and must be present in the
deployed portal files. Firebase client keys remain readable by the Android
or browser client. Restrict these keys to the Firebase APIs in use.

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
