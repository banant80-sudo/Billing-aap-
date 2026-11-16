# Firebase GST backend

This project uses Firebase for app authentication and data. Live GST/GSP traffic must go through this Cloud Functions backend; never put GSP credentials or GST auth tokens in the browser or Android app.

## What is included

- Firebase-authenticated HTTPS endpoints for OTP request, OTP verification, GSTR-1 save, and logout.
- OTP throttling per signed-in user and GST business profile.
- GST auth sessions encrypted with AES-256-GCM before being stored in a Firestore subcollection. Firestore rules deny client access to these records; Cloud Functions uses the Admin SDK.
- Hosting rewrite from `/api/**` to the `gstApi` function.

The GSP URL and payload shown in the original sample are not universal GSTN endpoints. This backend follows that sample's `/authenticate` and `/returns/gstr1` contract as a starting adapter. Confirm the exact paths, headers, token response shape, and GSTR-1 schema with your contracted GSP before production use. The upload endpoint currently accepts `invoicesData` already formatted as the GSP-required B2B invoice array; it does not convert Finboss invoice documents into a certified return payload.

## Configure Firebase

1. Select the Firebase project associated with Finboss and enable Cloud Functions. Functions deployment generally requires the Blaze (pay-as-you-go) plan; check the current Firebase plan requirements before deploying.
2. Install Firebase CLI and authenticate, then from the repository root run `firebase use <your-project-id>`.
3. Set the GSP secrets using Secret Manager. Do not commit real values:

   ```bash
   firebase functions:secrets:set GSP_CLIENT_ID
   firebase functions:secrets:set GSP_CLIENT_SECRET
   ```

4. Generate a 32-byte encryption key and set it as a secret:

   ```bash
   openssl rand -base64 32
   firebase functions:secrets:set GSP_TOKEN_ENCRYPTION_KEY
   ```

   Preserve this key securely. Rotating it invalidates existing encrypted GST sessions; users can authenticate again after rotation.

5. Create `functions/.env.<your-project-id>` (do not commit it) with the provider's HTTPS base URL, a real provider-approved server IP if required, and exact allowed web origins:

   ```dotenv
   GSP_BASE_URL=https://your-real-gsp-host
   GSP_IP_USER=YOUR_PROVIDER_APPROVED_STATIC_EGRESS_IP
   GST_ALLOWED_ORIGINS=https://your-finboss-host.web.app,https://your-finboss-host.firebaseapp.com,http://localhost:8080
   ```

   `GSP_IP_USER` must be the IP address your GSP has authorized. A serverless function may not have a stable outbound IP unless static egress is configured, so coordinate networking with the GSP. Never use a hard-coded sample/client IP.

6. Deploy the function and hosting rewrite:

   ```bash
   cd functions && npm install && cd ..
   firebase deploy --only functions:gstApi --config firebase.json
   firebase deploy --only hosting --config web-portal/firebase.hosting.json
   ```

   Ensure the hosting config is deployed to the same Firebase project. The rewrite points to `gstApi` in `asia-south1`.

## API

Every endpoint requires `Authorization: Bearer <Firebase ID token>` from the currently signed-in Finboss user. Requests use JSON and must be sent to `/api/gst/...` on the deployed Hosting origin.

- `POST /api/gst/request-otp` — `{ "profileId": "main_1", "gstin": "...", "gstUsername": "..." }`
- `POST /api/gst/verify-otp` — same fields plus `{ "otp": "..." }`. The provider auth token is encrypted and stored server-side; it is never returned to the client.
- `POST /api/gst/upload-gstr1` — `{ "profileId": "main_1", "returnPeriod": "MMYYYY", "invoicesData": [...] }`. `invoicesData` must match the GSP's required B2B schema.
- `POST /api/gst/logout` — `{ "profileId": "main_1" }` removes the encrypted server-side session.

The GSTIN must match the selected `business_profiles/{profileId}` record. Plain non-GST profiles are rejected. Test in the GSP sandbox first; successful RETSAVE is not the same as filing a return on the GST portal. Review the portal/GSP response and complete any required filing/signing steps separately.
