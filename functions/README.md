# Firebase Cloud Functions GST backend — Sandbox.co.in adapter

The Finboss app uses Firebase for sign-in and data. GST API calls go through this Cloud Functions backend, so Sandbox API credentials and GST taxpayer tokens are never sent to the browser or Android app.

## Included endpoints

- Firebase-authenticated OTP request and OTP verification using Sandbox.co.in's GST compliance API.
- OTP throttling per signed-in user and GST business profile.
- Taxpayer access token encrypted with AES-256-GCM in the user's Firestore `gst_sessions` subcollection. Firestore rules deny client access; only Cloud Functions Admin SDK accesses these records.
- GSTR-1 save request to Sandbox's documented year/month endpoint.
- Firebase Hosting rewrite from `/api/**` to the `gstApi` function.

## Provider contract used

The adapter uses Sandbox API host `https://api.sandbox.co.in`:

1. `POST /authenticate` with `x-api-key`, `x-api-secret`, and `x-api-version` to get a Sandbox API token.
2. `POST /gst/compliance/tax-payer/otp` with the taxpayer username and GSTIN to request OTP.
3. `POST /gst/compliance/tax-payer/otp/verify?otp=...` with username and GSTIN to obtain the six-hour taxpayer token.
4. `POST /gst/compliance/tax-payer/gstrs/gstr-1/{year}/{month}` with the documented GSTR-1 return JSON and taxpayer token to save the return.

Sandbox API details and account/plan requirements can change; verify the exact contract in your Sandbox developer dashboard and test using a sandbox GSTIN before production. A successful GSTR-1 save request is not the same as filing the return; proceed-to-file, EVC/DSC verification, and filing-status steps are separate flows and are not implemented here.

The GSTR-1 endpoint accepts `returnData`, an object formatted to Sandbox's current GSTN return schema. The backend sets/overrides `gstin` and `fp` from the authenticated business profile and `MMYYYY` period. It does not convert the app's invoice records into all applicable GSTR-1 sections, calculate HSN summaries, or certify tax correctness.

## Firebase setup and deployment

1. Enable Cloud Functions for the Firebase project associated with Finboss. Firebase Functions deployment may require the Blaze (pay-as-you-go) plan; check the current Firebase plan requirements before deploying.
2. Install Firebase CLI, sign in, and select the correct project from the repository root:

   ```bash
   firebase login
   firebase use <your-project-id>
   ```

3. Set Sandbox API credentials in Firebase Secret Manager. Do not commit credentials or paste them into the frontend:

   ```bash
   firebase functions:secrets:set SANDBOX_API_KEY
   firebase functions:secrets:set SANDBOX_API_SECRET
   ```

4. Generate a 32-byte encryption key and set it as a secret:

   ```bash
   openssl rand -base64 32
   firebase functions:secrets:set GSP_TOKEN_ENCRYPTION_KEY
   ```

   Keep this key backed up securely. Rotating it makes stored taxpayer sessions unreadable; users must authenticate again.

5. Create `functions/.env.<your-project-id>` locally (do not commit it) with allowed web origins. Defaults are the Sandbox production API host, API version `1.0.0`, and source `primary`:

   ```dotenv
   GST_ALLOWED_ORIGINS=https://your-finboss-host.web.app,https://your-finboss-host.firebaseapp.com,http://localhost:8080
   SANDBOX_BASE_URL=https://api.sandbox.co.in
   SANDBOX_API_VERSION=1.0.0
   SANDBOX_GST_SOURCE=primary
   ```

   The Firebase Hosting origin must be listed exactly. Android requests do not use browser CORS, but still require a Firebase ID token.

6. Install and deploy:

   ```bash
   cd functions && npm install && cd ..
   firebase deploy --only functions:gstApi --config firebase.json
   firebase deploy --only hosting --config web-portal/firebase.hosting.json
   ```

   Deploy both to the same Firebase project. Hosting routes `/api/**` to `gstApi` in `asia-south1`.

## API calls

Every endpoint requires `Authorization: Bearer <Firebase ID token>` for the signed-in Finboss user. Use JSON and call `/api/gst/...` on the deployed Hosting origin. Never send Sandbox API secrets or the taxpayer access token from the client.

- `POST /api/gst/request-otp` — `{ "profileId": "main_1", "gstin": "...", "gstUsername": "..." }`
- `POST /api/gst/verify-otp` — same fields plus `{ "otp": "..." }`. The taxpayer token is encrypted and stored server-side; it is not returned to the client.
- `POST /api/gst/upload-gstr1` — `{ "profileId": "main_1", "returnPeriod": "MMYYYY", "returnData": { "b2b": [], "...": "other applicable GSTR-1 sections" } }`. `returnData` must follow the Sandbox/GSTN schema.
- `POST /api/gst/logout` — `{ "profileId": "main_1" }` deletes that profile's encrypted session.

The GSTIN must match `users/{uid}/business_profiles/{profileId}`. Plain non-GST profile IDs are rejected. The browser/app must obtain the Firebase ID token from Firebase Auth and send it as a Bearer token. The frontend UI is not yet wired to these endpoints.

## Important operational note

Sandbox's API token, taxpayer token, allowed API plan, account configuration, and GSTR-1 schema must be tested with your own Sandbox account. No live request has been made from this code change, and deployment/secrets are not configured automatically by committing these files.
