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
const BASE_URL = (process.env.GSP_BASE_URL || "https://apisandbox.whitebooks.in").replace(/\/+$/, "");
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
  if (!["main_1", "main_2"].includes(value)) throw httpError(400, "GST services are only available for GST business profiles.");
  return value;
}
function validateGstin(value) {
  const gstin = String(value || "").trim().toUpperCase();
  if (!/^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/.test(gstin)) throw httpError(400, "Enter a valid 15-character GSTIN.");
  return gstin;
}
function validateBaseUrl() {
  let parsed;
  try { parsed = new URL(BASE_URL); } catch { throw httpError(503, "WhiteBooks API URL is invalid."); }
  if (parsed.protocol !== "https:" || !["apisandbox.whitebooks.in", "api.whitebooks.in"].includes(parsed.hostname)) {
    throw httpError(503, "GSP_BASE_URL must use the official WhiteBooks sandbox or production HTTPS host.");
  }
  return BASE_URL;
}
function getIpAddress() {
  const value = process.env.GSP_IP_ADDRESS;
  if (!value) throw httpError(503, "Configure the provider-approved GSP_IP_ADDRESS on the server.");
  return value;
}
function makeGstHeaders(gstin, username, txn = "") {
  return {
    gst_username: username,
    state_cd: gstin.slice(0, 2),
    ip_address: getIpAddress(),
    client_id: clientIdSecret.value(),
    client_secret: clientSecretSecret.value(),
    ...(txn ? { txn } : {})
  };
}
function encryptionKey() {
  const key = Buffer.from(tokenKeySecret.value(), "base64");
  if (key.length !== 32) throw httpError(503, "GST session encryption key is not configured correctly.");
  return key;
}
function encryptSession(payload) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", encryptionKey(), iv);
  const ciphertext = Buffer.concat([cipher.update(JSON.stringify(payload), "utf8"), cipher.final()]);
  return { encryptedSession: ciphertext.toString("base64"), iv: iv.toString("base64"), tag: cipher.getAuthTag().toString("base64") };
}
function decryptSession(record) {
  try {
    const decipher = crypto.createDecipheriv("aes-256-gcm", encryptionKey(), Buffer.from(record.iv, "base64"));
    decipher.setAuthTag(Buffer.from(record.tag, "base64"));
    const clear = Buffer.concat([decipher.update(Buffer.from(record.encryptedSession, "base64")), decipher.final()]).toString("utf8");
    return JSON.parse(clear);
  } catch (error) {
    if (error.status) throw error;
    throw httpError(500, "Could not securely read the GST session. Please authenticate again.");
  }
}
function sessionRef(uid, profileId) { return db.doc(`users/${uid}/gst_sessions/${profileId}`); }
function pendingOtpRef(uid, profileId) { return db.doc(`users/${uid}/gst_pending_auth/${profileId}`); }
function providerError(error) {
  const status = error.response?.status;
  const body = error.response?.data;
  const message = [body?.message, body?.error, body?.error_description, body?.data?.message].find((item) => typeof item === "string");
  return { status: status >= 400 && status < 600 ? status : 502, message: message ? message.slice(0, 300) : (error.code === "ECONNABORTED" ? "WhiteBooks request timed out." : "WhiteBooks request failed.") };
}
function asyncRoute(handler) {
  return (req, res) => Promise.resolve(handler(req, res)).catch((error) => {
    const status = error.status || 500;
    if (status >= 500) console.error("GST API error", { status, message: error.message });
    res.status(status).json({ success: false, error: status === 500 ? "GST service encountered an error." : error.message });
  });
}
async function verifyProfile(uid, profileId, gstin) {
  const profile = await db.doc(`users/${uid}/business_profiles/${profileId}`).get();
  if (!profile.exists || String(profile.get("gstin") || "").trim().toUpperCase() !== gstin) {
    throw httpError(403, "GSTIN must match the selected Finboss business profile.");
  }
}
function pickTxn(body) {
  const txn = body?.data?.txn || body?.txn || body?.data?.transaction_id || body?.transaction_id || body?.data?.transactionId;
  return typeof txn === "string" && txn.length <= 200 ? txn : "";
}
function pickAccessToken(body) {
  const token = body?.data?.auth_token || body?.auth_token || body?.data?.access_token || body?.access_token || body?.data?.authToken;
  return typeof token === "string" ? token : "";
}

app.post("/api/gst/request-otp", asyncRoute(async (req, res) => {
  const profileId = validateProfileId(req.body.profileId);
  const gstin = validateGstin(req.body.gstin);
  const username = String(req.body.gstUsername || "").trim();
  const email = String(req.body.email || "").trim();
  if (!username || username.length > 100) throw httpError(400, "Enter the GST portal username.");
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 200) throw httpError(400, "Enter a valid email address for WhiteBooks OTP requests.");
  await verifyProfile(req.user.uid, profileId, gstin);

  const rateRef = db.doc(`users/${req.user.uid}/gst_rate_limits/${profileId}`);
  const now = Date.now();
  const dayKey = new Date(now).toISOString().slice(0, 10);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(rateRef);
    const data = snapshot.exists ? snapshot.data() : {};
    const count = data.dayKey === dayKey ? Number(data.count || 0) : 0;
    if (now - Number(data.lastOtpAtMs || 0) < 60_000) throw httpError(429, "Wait one minute before requesting another OTP.");
    if (count >= 10) throw httpError(429, "Daily OTP request limit reached.");
    transaction.set(rateRef, { dayKey, count: count + 1, lastOtpAtMs: now });
  });

  try {
    const response = await axios.get(`${validateBaseUrl()}/authentication/otprequest`, {
      params: { email },
      headers: makeGstHeaders(gstin, username),
      timeout: 20_000,
      maxRedirects: 0,
      maxContentLength: 1_000_000
    });
    const txn = pickTxn(response.data);
    if (!txn) throw httpError(502, "WhiteBooks did not return the OTP transaction ID required to verify the code.");
    await pendingOtpRef(req.user.uid, profileId).set({
      ...encryptSession({ gstin, username, email, txn }),
      expiresAtMs: Date.now() + 10 * 60 * 1000,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    res.json({ success: true, message: "OTP request sent. Check the email/mobile associated with the GST portal." });
  } catch (error) {
    if (error.status) throw error;
    const failure = providerError(error);
    throw httpError(failure.status, failure.message);
  }
}));

app.post("/api/gst/verify-otp", asyncRoute(async (req, res) => {
  const profileId = validateProfileId(req.body.profileId);
  const gstin = validateGstin(req.body.gstin);
  const username = String(req.body.gstUsername || "").trim();
  const email = String(req.body.email || "").trim();
  const otp = String(req.body.otp || "").trim();
  if (!username || !email || !/^\d{4,8}$/.test(otp)) throw httpError(400, "Enter email, GST username, and the OTP received.");
  await verifyProfile(req.user.uid, profileId, gstin);

  const pendingRef = pendingOtpRef(req.user.uid, profileId);
  const pendingDoc = await pendingRef.get();
  if (!pendingDoc.exists || Number(pendingDoc.get("expiresAtMs") || 0) <= Date.now()) throw httpError(401, "OTP request expired. Request a new OTP.");
  const pending = decryptSession(pendingDoc.data());
  if (pending.gstin !== gstin || pending.username !== username || pending.email.toLowerCase() !== email.toLowerCase()) throw httpError(403, "OTP details do not match the request. Request a new OTP.");

  try {
    const response = await axios.get(`${validateBaseUrl()}/authentication/authtoken`, {
      params: { email, otp },
      headers: makeGstHeaders(gstin, username, pending.txn),
      timeout: 20_000,
      maxRedirects: 0,
      maxContentLength: 1_000_000
    });
    const authToken = pickAccessToken(response.data);
    if (!authToken) throw httpError(502, "WhiteBooks did not return an access token.");
    const expiresAtMs = Date.now() + 6 * 60 * 60 * 1000;
    await sessionRef(req.user.uid, profileId).set({
      ...encryptSession({ gstin, username, email, authToken }),
      expiresAtMs,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    await pendingRef.delete();
    res.json({ success: true, expiresAt: new Date(expiresAtMs).toISOString() });
  } catch (error) {
    if (error.status) throw error;
    const failure = providerError(error);
    throw httpError(failure.status, failure.message);
  }
}));

app.post("/api/gst/upload-gstr1", asyncRoute(async (req, res) => {
  const profileId = validateProfileId(req.body.profileId);
  const returnPeriod = String(req.body.returnPeriod || "").trim();
  const returnData = req.body.returnData;
  if (!/^(0[1-9]|1[0-2])\d{4}$/.test(returnPeriod)) throw httpError(400, "Return period must use MMYYYY format.");
  if (!returnData || typeof returnData !== "object" || Array.isArray(returnData)) throw httpError(400, "Provide a GSTR-1 JSON object using the WhiteBooks schema.");
  const stored = await sessionRef(req.user.uid, profileId).get();
  if (!stored.exists || Number(stored.get("expiresAtMs") || 0) <= Date.now()) throw httpError(401, "GST session missing or expired. Request and verify a new OTP.");
  const session = decryptSession(stored.data());
  await verifyProfile(req.user.uid, profileId, session.gstin);
  const payload = { ...returnData, gstin: session.gstin, fp: returnPeriod };

  try {
    const response = await axios.put(`${validateBaseUrl()}/gstr1/retsave`, payload, {
      params: { email: session.email },
      headers: {
        ...makeGstHeaders(session.gstin, session.username),
        ret_period: returnPeriod,
        "auth-token": session.authToken,
        "Content-Type": "application/json"
      },
      timeout: 30_000,
      maxRedirects: 0,
      maxContentLength: 1_000_000
    });
    res.json({
      success: true,
      referenceId: response.data?.data?.reference_id || response.data?.reference_id || response.data?.data?.referenceId || response.data?.referenceId || null,
      providerStatus: response.data?.data?.status || response.data?.status || "received"
    });
  } catch (error) {
    if (error.status) throw error;
    const failure = providerError(error);
    throw httpError(failure.status, failure.message);
  }
}));

app.post("/api/gst/logout", asyncRoute(async (req, res) => {
  const profileId = validateProfileId(req.body.profileId);
  await Promise.all([sessionRef(req.user.uid, profileId).delete(), pendingOtpRef(req.user.uid, profileId).delete()]);
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
