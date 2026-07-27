package com.yahigod.homestashtv.receiver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI

class BridgePairingClient(
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun startPairing(
        bridgeUrl: String,
        deviceName: String,
    ): PendingPairing = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeBridgeUrl(bridgeUrl)
        val body = JSONObject()
            .put("v", RECEIVER_PROTOCOL_VERSION)
            .put("device_name", deviceName.trim())
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$normalizedUrl/api/v1/pairings")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val value = response.body.string().toJsonResponse()
            if (response.code != 201) {
                throw PairingException(
                    value.optString("error").ifBlank { "Bridge rejected pairing." },
                )
            }
            PendingPairing(
                pairingId = value.getString("pairing_id"),
                code = value.getString("code"),
                expiresAtMs = value.getLong("expires_at_ms"),
            )
        }
    }

    suspend fun pollPairing(
        bridgeUrl: String,
        pairingId: String,
    ): PairingPollResult = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeBridgeUrl(bridgeUrl)
        val request = Request.Builder()
            .url("$normalizedUrl/api/v1/pairings/$pairingId")
            .get()
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                val value = response.body.string().toJsonResponse()
                when {
                    response.code != 200 -> PairingPollResult.Failed(
                        value.optString("error").ifBlank { "Pairing request is unavailable." },
                    )
                    value.optString("status") == "pending" -> PairingPollResult.Pending
                    value.optString("status") == "approved" -> PairingPollResult.Approved(
                        receiverId = value.getString("receiver_id"),
                        receiverToken = value.getString("receiver_token"),
                    )
                    else -> PairingPollResult.Failed("Bridge returned an invalid pairing state.")
                }
            }
        }.getOrElse {
            PairingPollResult.Failed("Could not reach the bridge.")
        }
    }
}

class PairingException(message: String) : IllegalStateException(message)

internal fun normalizeBridgeUrl(value: String): String {
    val candidate = value.trim().trimEnd('/')
    val uri = runCatching { URI(candidate) }.getOrNull()
        ?: throw PairingException("Enter a complete HTTP or HTTPS bridge address.")
    if (
        uri.scheme !in setOf("http", "https") ||
        uri.host.isNullOrBlank() ||
        uri.userInfo != null ||
        uri.query != null ||
        uri.fragment != null ||
        (uri.path.isNotBlank() && uri.path != "/")
    ) {
        throw PairingException("Enter a complete HTTP or HTTPS bridge address.")
    }
    return candidate
}

internal fun websocketUrl(bridgeUrl: String): String {
    val normalized = normalizeBridgeUrl(bridgeUrl)
    return when {
        normalized.startsWith("https://") -> "wss://${normalized.removePrefix("https://")}"
        else -> "ws://${normalized.removePrefix("http://")}"
    } + "/api/v1/receivers/connect"
}

private fun String.toJsonResponse(): JSONObject =
    runCatching { JSONObject(this) }
        .getOrElse { throw PairingException("Bridge returned an invalid response.") }

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
