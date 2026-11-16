const crypto = require("node:crypto");
const express = require("express");
const axios = require("axios");
const admin = require("firebase-admin");
const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

admin.initializeApp();
const db = admin.firestore();
const sandboxApiKey = defineSecret("SANDBOX_API_KEY");
const sandboxApiSecret = defineSecret("SANDBOX_API_SECRET");
const tokenKeySecret = defineSecret("GSP_TOKEN_ENCRYPTION_KEY");
const app = express();
const SANDBOX_BASE_URL = (process.env.SANDBOX_BASE_URL || "https://api.sandbox.co.in").replace(/\/+$/, "");
const SANDBOX_API_VERSION = process.env.SANDBOX_API_VERSION || "1.0.0";
const GST_SOURCE = process.env.SANDBOX_GST_SOURCE || "primary";

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

function validateSandboxUrl() {
  let parsed;
  try {
    parsed = new URL(SANDBOX_BASE_URL);
  } catch {
    throw httpError(503, "Sandbox API URL is not configured correctly.");
  }
  if (parsed.protocol !== "https:" || parsed.hostname !== "api.sandbox.co.in") {
    throw httpError(503, "Sandbox API URL must be https://api.sandbox.co.in.");
  }
  return SANDBOX_BASE_URL;
}

function sandboxHeaders(accessToken = "") {
  const headers = {
    accept: "application/json",
    "x-api-key": sandboxApiKey.value(),
    "x-api-version": SANDBOX_API_VERSION
  };
  if (accessToken) headers.authorization = accessToken;
  return headers;
}

function taxpayerHeaders(accessToken) {
  return {
    ...sandboxHeaders(accessToken),
    "Content-Type": "application/json",
    "x-source": GST_SOURCE
  };
}

function extractAccessToken(response) {
  return response?.data?.data?.access_token || response?.data?.access_token || response?.data?.data?.accessToken || response?.data?.accessToken || "";
}

async function authenticateSandbox() {
  try {
    const response = await axios.post(`${validateSandboxUrl()}/authenticate`, null, {
      headers: {
        accept: "application/json",
        "x-api-key": sandboxApiKey.value(),
        "x-api-secret": sandboxApiSecret.value(),
        "x-api-version": SANDBOX_API_VERSION
      },
      timeout: 20_000,
      maxRedirects: 0,
      maxContentLength: 1_000_000
    });
    const token = extractAccessToken(response);
    if (!token) throw httpError(502, "Sandbox authentication did not return an API access token.");
    return token;
  } catch (error) {
    if (error.status) throw error;
    const providerError = getProviderError(error);
    throw httpError(providerError.status, providerError.message);
  }
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
  const providerMessage = [providerData?.message, providerData?.error, providerData?.data?.message]
    .find((message) => typeof message === "string");
  return {
    status: status && status >= 400 && status < 600 ? status : 502,
    message: providerMessage ? providerMessage.slice(0, 300) : (error.code === "ECONNABORTED" ? "Sandbox request timed out." : "Sandbox request failed.")
  };
}

function asyncRoute(handler) {
  return (req, res) => Promise.resolve(handler(req, res)).catch((error) => {
    const status = error.status || 500;
    if (status >= 500) console.error("GST API error", { status, message: error.message });
    res.status(status).json({ success: false, error: status === 500 ? "GST service encountered an error." : error.message });
  });
}

async function verifyProfileGstin(uid, profileId, gstin) {
  const profile = await db.doc(`users/${uid}/business_profiles/${profileId}`).get();
  if (!profile.exists || String(profile.get("gstin") || "").toUpperCase() !== gstin) {
    throw httpError(403, "GSTIN must match the selected business profile in Finboss settings.");
  }
}

app.post("/api/gst/request-otp", asyncRoute(async (req, res) => {
  const profileId = validateProfileId(req.body.profileId);
  const gstin = validateGstin(req.body.gstin);
  const username = String(req.body.gstUsername || "").trim();
  if (!username || username.length > 100) throw httpError(400, "Enter the GST portal username.");
  await verifyProfileGstin(req.user.uid, profileId, gstin);

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
    const sandboxToken = await authenticateSandbox();
    const response = await axios.post(`${validateSandboxUrl()}/gst/compliance/tax-payer/otp`, {
      username,
      gstin
    }, {
      headers: taxpayerHeaders(sandboxToken),
      timeout: 20_000,
      maxRedirects: 0,
      maxContentLength: 1_000_000
    });
    res.json({ success: true, message: "OTP request sent. Check the mobile number or email registered with the GST portal.", providerStatus: response.data?.data?.status_cd || response.data?.status || "received" });
  } catch (error) {
    if (error.status) throw error;
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
  await verifyProfileGstin(req.user.uid, profileId, gstin);

  try {
    const sandboxToken = await authenticateSandbox();
    const response = await axios.post(`${validateSandboxUrl()}/gst/compliance/tax-payer/otp/verify`, {
      username,
      gstin
    }, {
      params: { otp },
      headers: taxpayerHeaders(sandboxToken),
      timeout: 20_000,
      maxRedirects: 0,
      maxContentLength: 1_000_000
    });
    const taxpayerToken = extractAccessToken(response);
    if (!taxpayerToken) throw httpError(502, "Sandbox OTP verification did not return a taxpayer access token.");

    const expiresAtMs = Date.now() + 6 * 60 * 60 * 1000;
    await sessionRef(req.user.uid, profileId).set({
      ...encryptSession({ gstin, username, taxpayerToken }),
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
  const returnData = req.body.returnData;
  if (!/^(0[1-9]|1[0-2])[0-9]{4}$/.test(returnPeriod)) throw httpError(400, "Return period must use MMYYYY format.");
  if (!returnData || typeof returnData !== "object" || Array.isArray(returnData)) {
    throw httpError(400, "Provide a GSTR-1 return object using Sandbox's documented GSTN schema.");
  }
  const [month, year] = [returnPeriod.slice(0, 2), returnPeriod.slice(2)];
  const stored = await sessionRef(req.user.uid, profileId).get();
  if (!stored.exists || Number(stored.get("expiresAtMs") || 0) <= Date.now()) {
    throw httpError(401, "GST session is missing or expired. Request and verify a new OTP.");
  }
  const session = decryptSession(stored.data());
  await verifyProfileGstin(req.user.uid, profileId, session.gstin);

  try {
    const payload = { ...returnData, gstin: session.gstin, fp: returnPeriod };
    const response = await axios.post(`${validateSandboxUrl()}/gst/compliance/tax-payer/gstrs/gstr-1/${year}/${month}`, payload, {
      headers: taxpayerHeaders(session.taxpayerToken),
      timeout: 30_000,
      maxRedirects: 0,
      maxContentLength: 1_000_000
    });
    res.json({
      success: true,
      referenceId: response.data?.data?.reference_id || response.data?.reference_id || response.data?.data?.referenceId || response.data?.referenceId || null,
      providerStatus: response.data?.data?.status_cd || response.data?.status || "received"
    });
  } catch (error) {
    if (error.status) throw error;
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
  secrets: [sandboxApiKey, sandboxApiSecret, tokenKeySecret]
}, app);
