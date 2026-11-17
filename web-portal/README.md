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
