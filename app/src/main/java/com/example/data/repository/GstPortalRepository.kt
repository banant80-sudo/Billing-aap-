package com.example.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Response from the authenticated Firebase GST function. Tokens are never returned by this API. */
data class GstPortalApiResponse(
    val success: Boolean,
    val message: String,
    val referenceId: String = "",
    val filed: Boolean = false,
)

class GstPortalRepository(
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    companion object {
        // Replace with the Firebase Hosting origin for the project, e.g. https://project.web.app
        const val DEFAULT_BASE_URL = "https://your-finboss-host.web.app"
    }

    suspend fun requestOtp(
        profileId: String,
        gstin: String,
        gstUsername: String,
        email: String,
        firebaseToken: String,
    ): GstPortalApiResponse = post(
        "/api/gst/request-otp",
        JSONObject()
            .put("profileId", profileId)
            .put("gstin", gstin)
            .put("gstUsername", gstUsername)
            .put("email", email),
        firebaseToken,
    )

    suspend fun verifyOtp(
        profileId: String,
        gstin: String,
        gstUsername: String,
        email: String,
        otp: String,
        firebaseToken: String,
    ): GstPortalApiResponse = post(
        "/api/gst/verify-otp",
        JSONObject()
            .put("profileId", profileId)
            .put("gstin", gstin)
            .put("gstUsername", gstUsername)
            .put("email", email)
            .put("otp", otp),
        firebaseToken,
    )

    suspend fun uploadGstr1(
        profileId: String,
        returnPeriod: String,
        returnDataJson: String,
        firebaseToken: String,
    ): GstPortalApiResponse = postReturn(
        "/api/gst/upload-gstr1",
        profileId,
        returnPeriod,
        returnDataJson,
        firebaseToken,
    )

    suspend fun saveGstr3b(
        profileId: String,
        returnPeriod: String,
        returnDataJson: String,
        firebaseToken: String,
    ): GstPortalApiResponse = postReturn(
        "/api/gst/gstr3b/save",
        profileId,
        returnPeriod,
        returnDataJson,
        firebaseToken,
    )

    suspend fun fileGstr3bWithEvc(
        profileId: String,
        returnPeriod: String,
        pan: String,
        otp: String,
        returnDataJson: String,
        firebaseToken: String,
    ): GstPortalApiResponse {
        val data = parseJsonObject(returnDataJson)
        return post(
            "/api/gst/gstr3b/file-evc",
            JSONObject()
                .put("profileId", profileId)
                .put("returnPeriod", returnPeriod)
                .put("pan", pan)
                .put("otp", otp)
                .put("confirmFinalFiling", "FILE_GSTR3B")
                .put("returnData", data),
            firebaseToken,
        )
    }

    private suspend fun postReturn(
        endpoint: String,
        profileId: String,
        returnPeriod: String,
        returnDataJson: String,
        firebaseToken: String,
    ): GstPortalApiResponse = post(
        endpoint,
        JSONObject()
            .put("profileId", profileId)
            .put("returnPeriod", returnPeriod)
            .put("returnData", parseJsonObject(returnDataJson)),
        firebaseToken,
    )

    private fun parseJsonObject(raw: String): JSONObject = try {
        JSONObject(raw)
    } catch (_: Exception) {
        throw IllegalArgumentException("Return data must be a valid JSON object.")
    }

    private suspend fun post(
        endpoint: String,
        payload: JSONObject,
        firebaseToken: String,
    ): GstPortalApiResponse = withContext(Dispatchers.IO) {
        require(firebaseToken.isNotBlank()) { "Please sign in before using GST services." }
        val connection = URL(baseUrl.trimEnd('/') + endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 50_000
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Authorization", "Bearer $firebaseToken")
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = if (raw.isBlank()) JSONObject() else runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            val success = json.optBoolean("success", code in 200..299)
            val message = if (success) {
                when {
                    json.has("message") -> json.optString("message")
                    json.optBoolean("filed", false) -> "Filing request accepted. Check GST portal status and ARN."
                    else -> "GST request completed."
                }
            } else {
                json.optString("error", "GST request failed (HTTP $code).")
            }
            GstPortalApiResponse(
                success = success,
                message = message,
                referenceId = json.optString("referenceId", ""),
                filed = json.optBoolean("filed", false),
            )
        } finally {
            connection.disconnect()
        }
    }
}
