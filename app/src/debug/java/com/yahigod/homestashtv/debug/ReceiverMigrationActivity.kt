package com.yahigod.homestashtv.debug

import android.app.Activity
import android.os.Bundle
import com.yahigod.homestashtv.profiles.AndroidServerProfileRepository
import com.yahigod.homestashtv.profiles.ServerProfile
import com.yahigod.homestashtv.profiles.normalizeServerUrl
import com.yahigod.homestashtv.receiver.AndroidBridgePairingRepository
import com.yahigod.homestashtv.receiver.BridgePairing
import com.yahigod.homestashtv.receiver.normalizeBridgeUrl
import org.json.JSONObject
import java.io.File

/**
 * Debug-only, one-shot migration entry point for development signing resets.
 *
 * The caller must place the fixed-name input inside this app's private files
 * directory with `run-as`. No secret is accepted through an intent or printed
 * to logs. The input is deleted whether the import succeeds or fails.
 */
class ReceiverMigrationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val input = File(filesDir, INPUT_FILE)
        val result = runCatching {
            val migration = decodeReceiverMigration(input.readText())
            migration.profiles.forEach { profile ->
                AndroidServerProfileRepository(applicationContext).saveProfile(
                    ServerProfile(
                        id = profile.id,
                        name = profile.name,
                        serverUrl = profile.serverUrl,
                    ),
                    newCredential = profile.apiKey,
                )
            }
            AndroidBridgePairingRepository(applicationContext).savePairing(
                BridgePairing(
                    bridgeUrl = migration.bridgeUrl,
                    receiverId = migration.receiverId,
                    deviceName = migration.deviceName,
                ),
                migration.receiverToken,
            )
            "ok:${migration.profiles.size}"
        }.getOrElse {
            "error"
        }

        input.delete()
        File(filesDir, RESULT_FILE).writeText(result)
        finish()
    }

    companion object {
        const val INPUT_FILE = "receiver-migration-v1.json"
        const val RESULT_FILE = "receiver-migration-v1.result"
    }
}

internal data class ReceiverMigrationProfile(
    val id: String,
    val name: String,
    val serverUrl: String,
    val apiKey: String,
)

internal data class ReceiverMigration(
    val bridgeUrl: String,
    val receiverId: String,
    val receiverToken: String,
    val deviceName: String,
    val profiles: List<ReceiverMigrationProfile>,
)

internal fun decodeReceiverMigration(value: String): ReceiverMigration {
    val root = JSONObject(value)
    require(root.getInt("v") == 1) { "Unsupported migration version." }
    val pairing = root.getJSONObject("pairing")
    val bridgeUrl = normalizeBridgeUrl(pairing.requiredString("bridge_url"))
    val profilesValue = root.getJSONArray("profiles")
    require(profilesValue.length() in 1..10) { "Migration must contain profiles." }

    val profiles = buildList {
        for (index in 0 until profilesValue.length()) {
            val item = profilesValue.getJSONObject(index)
            add(
                ReceiverMigrationProfile(
                    id = item.requiredString("id"),
                    name = item.requiredString("name"),
                    serverUrl = normalizeServerUrl(item.requiredString("server_url")),
                    apiKey = item.getString("api_key"),
                ),
            )
        }
    }
    require(profiles.map { it.id }.distinct().size == profiles.size) {
        "Profile IDs must be unique."
    }

    return ReceiverMigration(
        bridgeUrl = bridgeUrl,
        receiverId = pairing.requiredString("receiver_id"),
        receiverToken = pairing.requiredString("receiver_token"),
        deviceName = pairing.requiredString("device_name"),
        profiles = profiles,
    )
}

private fun JSONObject.requiredString(name: String): String =
    getString(name).trim().also {
        require(it.isNotEmpty()) { "$name is required." }
    }
