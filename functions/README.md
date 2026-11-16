# Firebase Cloud Functions GST backend — WhiteBooks adapter

The Finboss app uses Firebase for sign-in and data. GST API calls pass through this Cloud Functions backend, keeping WhiteBooks client credentials and GST access tokens off the Android/web clients.

## WhiteBooks endpoints implemented

- OTP request: `GET /authentication/otprequest?email=...`
- OTP verification/token: `GET /authentication/authtoken?email=...&otp=...`
- Save GSTR-1 draft: `PUT /gstr1/retsave?email=...`

The API base defaults to `https://apisandbox.whitebooks.in`. Production may use `https://api.whitebooks.in` only when your WhiteBooks account is approved and configured for production.

OTP-request transaction IDs are encrypted server-side for 10 minutes and used only during verification. The taxpayer access token is encrypted at rest in Firestore and never returned to the app. Each request requires a valid Firebase ID token. OTP requests are throttled per user/business profile.

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

5. Create `functions/.env.<your-project-id>` locally; do not commit it. Set the IP WhiteBooks has authorized for your account and the exact portal origins:

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
- `POST /api/gst/verify-otp` — same fields plus `{ "otp": "..." }`. The pending transaction ID must match the OTP request and is handled server-side.
- `POST /api/gst/upload-gstr1` — `{ "profileId": "main_1", "returnPeriod": "MMYYYY", "returnData": { ... } }`. `returnData` must match WhiteBooks' current GSTN GSTR-1 schema. The backend sets GSTIN and `fp` from the authenticated profile/period.
- `POST /api/gst/logout` — `{ "profileId": "main_1" }` clears encrypted session data.

The business profile GSTIN must match the request. Non-GST profile IDs are rejected. The UI still needs to send the required email field and create a valid GSTR-1 return object from invoice records; arbitrary invoice arrays are not a compliant GSTR-1 payload.

## Important

`PUT /gstr1/retsave` saves/prepares return data; it does not by itself complete or file the legal return. Final filing, verification/signature and status checks are separate operations and are not implemented. Verify the exact current endpoint headers, response fields, account requirements, and payload schema in your WhiteBooks developer portal and sandbox before production use. The API adapter has not been deployed or tested against a live provider account.
