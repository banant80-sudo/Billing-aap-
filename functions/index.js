const crypto = require("node:crypto");
const express = require("express");
const axios = require("axios");
const admin = require("firebase-admin");
const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

admin.initializeApp();
const db = admin.firestore();
const clientIdSecret = defineSecret("GSP_CLIENT_ID");
const clientSecretSecret = defineSecret("GSP_CLIENT_SECRET");
const tokenKeySecret = defineSecret("GSP_TOKEN_ENCRYPTION_KEY");
const app = express();

app.disable("x-powered-by");
app.use(express.json({ limit: "1mb", strict: true }));

const allowedOrigins = (process.env.GST_ALLOWED_ORIGINS || "")
  .split(",")
  .map((origin) => origin.trim())
  .filter(Boolean);

app.use((req, res, next) => {
  const origin = req.get("origin");
  if (origin && allowedOrigins.includes(origin)) {
    res.set("Access-Control-Allow-Origin", origin);
    res.set("Vary", "Origin");
  }
  res.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
  res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.set("Cache-Control", "no-store");
  if (req.method === "OPTIONS") return res.status(204).end();
  next();
});

function httpError(status, message) {
  const error = new Error(message);
  error.status = status;
  return error;
}

async function requireFirebaseUser(req, res, next) {
  try {
    const match = /^Bearer\s+(.+)$/i.exec(req.get("authorization") || "");
    if (!match) throw httpError(401, "Sign in to Finboss before using GST services.");
    req.user = await admin.auth().verifyIdToken(match[1], true);
    return next();
  } catch (error) {
    return res.status(error.status || 401).json({ success: false, error: error.status ? error.message : "Invalid or expired sign-in session." });
  }
}

app.use(requireFirebaseUser);

function validateProfileId(value) {
  if (!["main_1", "main_2"].includes(value)) {
    throw httpError(400, "GST services are available only for a GST business profile.");
  }
  return value;
}

function validateGstin(value) {
  const gstin = String(value || "").trim().toUpperCase();
  if (!/^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/.test(gstin)) {
    throw httpError(400, "Enter a valid 15-character GSTIN.");
  }
  return gstin;
}

function getGspBaseUrl() {
  const raw = process.env.GSP_BASE_URL;
  if (!raw) throw httpError(503, "GST provider is not configured on the server.");
  let parsed;
  try {
    parsed = new URL(raw);
  } catch {
    throw httpError(503, "GST provider URL is not configured correctly.");
  }
  if (parsed.protocol !== "https:") throw httpError(503, "GST provider URL must use HTTPS.");
  return raw.replace(/\/+$/, "");
}

function getServerIp() {
  const ip = process.env.GSP_IP_USER;
  if (!ip) throw httpError(503, "GST provider IP configuration is missing on the server.");
  return ip;
}

function makeHeaders(gstin, username, authToken = "") {
  const headers = {
    "Content-Type": "application/json",
    client_id: clientIdSecret.value(),
    client_secret: clientSecretSecret.value(),
    gstin,
    username,
    "state-cd": gstin.slice(0, 2),
    "ip-usr": getServerIp(),
    txn: `FB${crypto.randomUUID().replace(/-/g, "")}`
  };
  if (authToken) headers["auth-token"] = authToken;
  return headers;
}

function encryptSession(payload) {
  const key = Buffer.from(tokenKeySecret.value(), "base64");
  if (key.length !== 32) throw httpError(503, "GST session encryption key is not configured correctly.");
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", key, iv);
  const ciphertext = Buffer.concat([cipher.update(JSON.stringify(payload), "utf8"), cipher.final()]);
  return {
    encryptedSession: ciphertext.toString("base64"),
    iv: iv.toString("base64"),
    tag: cipher.getAuthTag().toString("base64")
  };
}

function decryptSession(record) {
  const key = Buffer.from(tokenKeySecret.value(), "base64");
  if (key.length !== 32) throw httpError(503, "GST session encryption key is not configured correctly.");
  try {
    const decipher = crypto.createDecipheriv("aes-256-gcm", key, Buffer.from(record.iv, "base64"));
    decipher.setAuthTag(Buffer.from(record.tag, "base64"));
    const clear = Buffer.concat([
      decipher.update(Buffer.from(record.encryptedSession, "base64")),
      decipher.final()
    ]).toString("utf8");
    return JSON.parse(clear);
  } catch {
    throw httpError(500, "Could not securely read the GST session. Please authenticate again.");
  }
}

function sessionRef(uid, profileId) {
  return db.doc(`users/${uid}/gst_sessions/${profileId}`);
}

function getProviderError(error) {
  const status = error.response?.status;
  const providerData = error.response?.data;
  const providerMessage = typeof providerData?.message === "string" ? providerData.message.slice(0, 300) : "";
  return {
    status: status && status >= 400 && status < 600 ? status : 502,
    message: providerMessage || (error.code === "ECONNABORTED" ? "GST provider request timed out." : "GST provider request failed.")
  };
}

function asyncRoute(handler) {
  return (req, res) => Promise.resolve(handler(req, res)).catch((error) => {
    const status = error.status || 500;
    if (status >= 500) console.error("GST API error", { status, message: error.message });
    res.status(status).json({ success: false, error: status === 500 ? "GST service encountered an error." : error.message });
  });
}

app.post("/api/gst/request-otp", asyncRoute(async (req, res) => {
  const profileId = validateProfileId(req.body.profileId);
  const gstin = validateGstin(req.body.gstin);
  const username = String(req.body.gstUsername || "").trim();
  if (!username || username.length > 100) throw httpError(400, "Enter the GST portal username.");

  const profile = await db.doc(`users/${req.user.uid}/business_profiles/${profileId}`).get();
  if (!profile.exists || String(profile.get("gstin") || "").toUpperCase() !== gstin) {
    throw httpError(403, "GSTIN must match the selected business profile in Finboss settings.");
  }

  const rateRef = db.doc(`users/${req.user.uid}/gst_rate_limits/${profileId}`);
  const now = Date.now();
  const dayKey = new Date(now).toISOString().slice(0, 10);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(rateRef);
    const data = snapshot.exists ? snapshot.data() : {};
    const count = data.dayKey === dayKey ? Number(data.count || 0) : 0;
    if (now - Number(data.lastOtpAtMs || 0) < 60_000) throw httpError(429, "Please wait one minute before requesting another OTP.");
    if (count >= 10) throw httpError(429, "Daily OTP request limit reached. Try again tomorrow.");
    transaction.set(rateRef, { dayKey, count: count + 1, lastOtpAtMs: now });
  });

  try {
    const response = await axios.post(`${getGspBaseUrl()}/authenticate`, {
      action: "OTPREQUEST",
      username
    }, {
      headers: makeHeaders(gstin, username),
      timeout: 20_000,
      maxRedirects: 0,
      maxContentLength: 1_000_000
    });
    res.json({ success: true, message: "OTP request sent. Check the mobile number registered with the GST portal.", providerStatus: response.data?.status || "received" });
  } catch (error) {
    const providerError = getProviderError(error);
    throw httpError(providerError.status, providerError.message);
  }
}));

app.post("/api/gst/verify-otp", asyncRoute(async (req, res) => {
  const profileId = validateProfileId(req.body.profileId);
  const gstin = validateGstin(req.body.gstin);
  const username = String(req.body.gstUsername || "").trim();
  const otp = String(req.body.otp || "").trim();
  if (!username || username.length > 100 || !/^[0-9]{4,8}$/.test(otp)) {
    throw httpError(400, "Enter the GST portal username and the OTP received from GST.");
  }
  const profile = await db.doc(`users/${req.user.uid}/business_profiles/${profileId}`).get();
  if (!profile.exists || String(profile.get("gstin") || "").toUpperCase() !== gstin) {
    throw httpError(403, "GSTIN must match the selected business profile in Finboss settings.");
  }

  try {
    const response = await axios.post(`${getGspBaseUrl()}/authenticate`, {
      action: "AUTHTOKEN",
      username,
      otp
    }, {
      headers: makeHeaders(gstin, username),
      timeout: 20_000,
      maxRedirects: 0,
      maxContentLength: 1_000_000
    });
    const authToken = response.data?.auth_token || response.data?.authToken;
    if (typeof authToken !== "string" || !authToken) throw httpError(502, "GST provider did not return an authentication token.");

    const expiresAtMs = Date.now() + 6 * 60 * 60 * 1000;
    await sessionRef(req.user.uid, profileId).set({
      ...encryptSession({ gstin, username, authToken }),
      expiresAtMs,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    res.json({ success: true, expiresAt: new Date(expiresAtMs).toISOString() });
  } catch (error) {
    if (error.status) throw error;
    const providerError = getProviderError(error);
    throw httpError(providerError.status, providerError.message);
  }
}));

app.post("/api/gst/upload-gstr1", asyncRoute(async (req, res) => {
  const profileId = validateProfileId(req.body.profileId);
  const returnPeriod = String(req.body.returnPeriod || "").trim();
  const invoicesData = req.body.invoicesData;
  if (!/^(0[1-9]|1[0-2])[0-9]{4}$/.test(returnPeriod)) throw httpError(400, "Return period must use MMYYYY format.");
  if (!Array.isArray(invoicesData) || invoicesData.length < 1 || invoicesData.length > 500) {
    throw httpError(400, "Provide 1 to 500 invoice records in the provider-required GSTR-1 schema.");
  }
  const stored = await sessionRef(req.user.uid, profileId).get();
  if (!stored.exists || Number(stored.get("expiresAtMs") || 0) <= Date.now()) {
    throw httpError(401, "GST session is missing or expired. Request and verify a new OTP.");
  }
  const session = decryptSession(stored.data());
  const profile = await db.doc(`users/${req.user.uid}/business_profiles/${profileId}`).get();
  if (!profile.exists || String(profile.get("gstin") || "").toUpperCase() !== session.gstin) {
    throw httpError(403, "GSTIN no longer matches this business profile. Authenticate again.");
  }

  try {
    const response = await axios.put(`${getGspBaseUrl()}/returns/gstr1`, {
      action: "RETSAVE",
      data: { gstin: session.gstin, fp: returnPeriod, b2b: invoicesData }
    }, {
      headers: makeHeaders(session.gstin, session.username, session.authToken),
      timeout: 30_000,
      maxRedirects: 0,
      maxContentLength: 1_000_000
    });
    res.json({ success: true, referenceId: response.data?.reference_id || response.data?.referenceId || null, providerStatus: response.data?.status || "received" });
  } catch (error) {
    const providerError = getProviderError(error);
    throw httpError(providerError.status, providerError.message);
  }
}));

app.post("/api/gst/logout", asyncRoute(async (req, res) => {
  const profileId = validateProfileId(req.body.profileId);
  await sessionRef(req.user.uid, profileId).delete();
  res.json({ success: true });
}));

app.use((error, req, res, next) => {
  if (error instanceof SyntaxError && "body" in error) return res.status(400).json({ success: false, error: "Request body must be valid JSON." });
  console.error("Unhandled GST API error", error.message);
  return res.status(500).json({ success: false, error: "GST service encountered an error." });
});

exports.gstApi = onRequest({
  region: "asia-south1",
  timeoutSeconds: 60,
  memory: "256MiB",
  secrets: [clientIdSecret, clientSecretSecret, tokenKeySecret]
}, app);
