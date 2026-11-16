# Firebase Cloud Functions GST backend — WhiteBooks adapter

The Finboss app uses Firebase for sign-in and data. GST API calls pass through this Cloud Functions backend, keeping WhiteBooks client credentials and GST access tokens off the Android/web clients.

## WhiteBooks endpoints implemented

- OTP request: `GET /authentication/otprequest?email=...`
- OTP verification/token: `GET /authentication/authtoken?email=...&otp=...`
- GSTR-1 draft save: `PUT /gstr1/retsave?email=...`
- GSTR-3B draft save: `PUT /gstr3b/retsave?email=...`
- GSTR-3B EVC final filing: `POST /gstr3b/retevcfile?email=...&pan=...` (path configurable with `GSTR3B_EVC_FILE_PATH`)

The API base defaults to `https://apisandbox.whitebooks.in`. Production may use `https://api.whitebooks.in` only when your WhiteBooks account is approved and configured for production.

OTP-request transaction IDs and taxpayer access tokens are encrypted server-side in Firestore and never returned to the app. Each endpoint requires a valid Firebase ID token. OTP requests are throttled per user/business profile.

## Configure Firebase

1. Enable Cloud Functions for the Firebase project associated with Finboss. Cloud Functions deployment may require the Blaze (pay-as-you-go) plan; verify current Firebase plan requirements.
2. Install Firebase CLI, sign in, and select the correct project from the repository root:

   ```bash
   firebase login
   firebase use <your-project-id>
   ```

3. Configure WhiteBooks credentials in Firebase Secret Manager (do not commit real credentials):

   ```bash
   firebase functions:secrets:set GSP_CLIENT_ID
   firebase functions:secrets:set GSP_CLIENT_SECRET
   ```

4. Generate and set a 32-byte encryption key:

   ```bash
   openssl rand -base64 32
   firebase functions:secrets:set GSP_TOKEN_ENCRYPTION_KEY
   ```

   Keep the key securely backed up. Rotating it invalidates currently stored encrypted GST sessions.

5. Create `functions/.env.<your-project-id>` locally; do not commit it. Set the IP WhiteBooks has authorized and the exact portal origins:

   ```dotenv
   GSP_BASE_URL=https://apisandbox.whitebooks.in
   GSP_IP_ADDRESS=YOUR_PROVIDER_APPROVED_SERVER_IP
   GST_ALLOWED_ORIGINS=https://your-finboss-host.web.app,https://your-finboss-host.firebaseapp.com,http://localhost:8080
   ```

   Confirm whether Cloud Functions' outbound IP meets WhiteBooks allowlisting requirements; serverless outbound IP may not be stable without configured static egress.

6. Install and deploy to the selected Firebase project:

   ```bash
   cd functions && npm install && cd ..
   firebase deploy --only functions:gstApi --config firebase.json
   firebase deploy --only hosting --config web-portal/firebase.hosting.json
   ```

   The Hosting rewrite sends `/api/**` to the `gstApi` function in `asia-south1`.

## App API

Every route requires `Authorization: Bearer <Firebase ID token>` and JSON requests to `/api/gst/...`.

- `POST /api/gst/request-otp` — `{ "profileId": "main_1", "gstin": "...", "gstUsername": "...", "email": "..." }`
- `POST /api/gst/verify-otp` — same fields plus `{ "otp": "..." }`. The pending transaction ID is stored encrypted and used server-side.
- `POST /api/gst/upload-gstr1` — `{ "profileId": "main_1", "returnPeriod": "MMYYYY", "returnData": { ... } }`. Payload must follow the current WhiteBooks GSTR-1 schema.
- `POST /api/gst/gstr3b/save` — `{ "profileId": "main_1", "returnPeriod": "MMYYYY", "returnData": { ... } }`. Saves/prepares GSTR-3B; does not file it.
- `POST /api/gst/gstr3b/file-evc` — `{ "profileId": "main_1", "returnPeriod": "MMYYYY", "pan": "...", "otp": "...", "confirmFinalFiling": "FILE_GSTR3B", "returnData": { ... } }`. This is a final legal filing request. The app must show the exact GSTIN and return period for review, collect the authorized signatory's EVC, and require explicit confirmation. No retries should be made automatically.
- `POST /api/gst/logout` — `{ "profileId": "main_1" }` clears encrypted session data.

The business profile GSTIN must match. The UI must prepare a valid WhiteBooks GSTR-3B payload from verified accounting data; the API does not calculate tax or certify correctness.

## Important filing caveats

`PUT /gstr3b/retsave` only saves the return. The EVC route submits a legal return. Confirm exact endpoint, required request/response fields, EVC process, and schema in your authenticated WhiteBooks developer portal before using a live GSTIN. The EVC path defaults to `/gstr3b/retevcfile`, based on WhiteBooks endpoint documentation; set `GSTR3B_EVC_FILE_PATH` only if WhiteBooks confirms a different path for your account/version. Do not enable production filing until the full workflow has been tested in sandbox and reviewed by the authorized taxpayer/signatory.

A timeout during final filing may mean the provider accepted the return but the response was lost. The backend returns an uncertain status message; check filing status with WhiteBooks/GST portal before attempting again.
