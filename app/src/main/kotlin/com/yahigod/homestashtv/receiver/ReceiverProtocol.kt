package com.yahigod.homestashtv.receiver

import org.json.JSONArray
import org.json.JSONObject
import com.yahigod.homestashtv.profiles.ServerProfile

const val RECEIVER_PROTOCOL_VERSION = 1

data class PlayQueueCommand(
    val id: String,
    val receiverId: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val profileId: String,
    val sceneIds: List<String>,
    val startIndex: Int,
    val startPositionMs: Long,
    val continuePlayback: Boolean,
    val loop: Boolean,
    val reshuffle: Boolean,
)

enum class AcknowledgementStatus(val wireValue: String) {
    ACCEPTED("accepted"),
    DUPLICATE("duplicate"),
    EXPIRED("expired"),
    REJECTED("rejected"),
}

data class CommandAcknowledgement(
    val commandId: String,
    val status: AcknowledgementStatus,
    val atMs: Long,
    val errorCode: String? = null,
)

enum class PlaybackStateValue(val wireValue: String) {
    RESOLVING("resolving"),
    PLAYING("playing"),
    PAUSED("paused"),
    STOPPED("stopped"),
    COMPLETED("completed"),
    FAILED("failed"),
}

data class PlaybackStateReport(
    val commandId: String,
    val state: PlaybackStateValue,
    val atMs: Long,
    val sceneId: String? = null,
    val queueIndex: Int? = null,
    val positionMs: Long? = null,
    val errorCode: String? = null,
    val skippedSceneIds: List<String> = emptyList(),
)

class ProtocolException(
    val errorCode: String,
    message: String,
) : IllegalArgumentException(message)

fun decodeCommand(value: String): PlayQueueCommand {
    val root = runCatching { JSONObject(value) }
        .getOrElse { throw ProtocolException("invalid_json", "Command is not valid JSON.") }
    if (root.optInt("v", -1) != RECEIVER_PROTOCOL_VERSION) {
        throw ProtocolException("unsupported_version", "Protocol version is not supported.")
    }
    if (root.optString("type") != "command") {
        throw ProtocolException("invalid_command", "Envelope type must be command.")
    }

    val id = root.requiredString("id")
    val receiverId = root.requiredString("receiver_id")
    val createdAtMs = root.requiredNonNegativeLong("created_at_ms")
    val expiresAtMs = root.requiredNonNegativeLong("expires_at_ms")
    if (expiresAtMs <= createdAtMs) {
        throw ProtocolException("invalid_command", "Command expiry must follow creation.")
    }

    val command = root.optJSONObject("command")
        ?: throw ProtocolException("invalid_command", "Command body is required.")
    if (command.optString("type") != "play_queue") {
        throw ProtocolException("invalid_command", "Command type is not supported.")
    }
    val profileId = command.requiredString("profile_id")
    val sceneIds = command.optJSONArray("scene_ids").toSceneIds()
    val startIndex = command.optInt("start_index", -1)
    if (startIndex !in sceneIds.indices) {
        throw ProtocolException("invalid_command", "Start index is outside the queue.")
    }
    val startPositionMs = command.requiredNonNegativeLong("start_position_ms")
    val policy = command.optJSONObject("policy")
        ?: throw ProtocolException("invalid_command", "Playback policy is required.")

    return PlayQueueCommand(
        id = id,
        receiverId = receiverId,
        createdAtMs = createdAtMs,
        expiresAtMs = expiresAtMs,
        profileId = profileId,
        sceneIds = sceneIds,
        startIndex = startIndex,
        startPositionMs = startPositionMs,
        continuePlayback = policy.optBoolean("continue", true),
        loop = policy.optBoolean("loop", false),
        reshuffle = policy.optBoolean("reshuffle", false),
    )
}

fun encodeAcknowledgement(value: CommandAcknowledgement): String =
    JSONObject()
        .put("v", RECEIVER_PROTOCOL_VERSION)
        .put("type", "ack")
        .put("command_id", value.commandId)
        .put("status", value.status.wireValue)
        .put("at_ms", value.atMs)
        .apply {
            value.errorCode?.let { put("error_code", it) }
        }
        .toString()

fun encodePlaybackState(value: PlaybackStateReport): String =
    JSONObject()
        .put("v", RECEIVER_PROTOCOL_VERSION)
        .put("type", "playback_state")
        .put("command_id", value.commandId)
        .put("state", value.state.wireValue)
        .put("at_ms", value.atMs)
        .apply {
            value.sceneId?.let { put("scene_id", it) }
            value.queueIndex?.let { put("queue_index", it) }
            value.positionMs?.let { put("position_ms", it.coerceAtLeast(0L)) }
            value.errorCode?.let { put("error_code", it) }
            if (value.skippedSceneIds.isNotEmpty()) {
                put("skipped_scene_ids", JSONArray(value.skippedSceneIds))
            }
        }
        .toString()

fun encodeHello(
    receiverId: String,
    appVersion: String,
    profiles: List<ServerProfile>,
): String {
    val profileValues = JSONArray()
    profiles.forEach {
        profileValues.put(
            JSONObject()
                .put("id", it.id)
                .put("name", it.name),
        )
    }
    return JSONObject()
        .put("v", RECEIVER_PROTOCOL_VERSION)
        .put("type", "hello")
        .put("receiver_id", receiverId)
        .put("app_version", appVersion)
        .put("profiles", profileValues)
        .toString()
}

private fun JSONObject.requiredString(name: String): String =
    optString(name).takeIf { it.isNotBlank() }
        ?: throw ProtocolException("invalid_command", "$name is required.")

private fun JSONObject.requiredNonNegativeLong(name: String): Long {
    if (!has(name)) {
        throw ProtocolException("invalid_command", "$name is required.")
    }
    val value = optLong(name, -1)
    if (value < 0) {
        throw ProtocolException("invalid_command", "$name must not be negative.")
    }
    return value
}

private fun JSONArray?.toSceneIds(): List<String> {
    if (this == null || length() !in 1..500) {
        throw ProtocolException("invalid_command", "scene_ids must contain 1 to 500 items.")
    }
    return buildList {
        for (index in 0 until length()) {
            val value = optString(index)
            if (!value.matches(Regex("[1-9][0-9]*"))) {
                throw ProtocolException(
                    "invalid_command",
                    "Scene IDs must be positive decimal strings.",
                )
            }
            add(value)
        }
    }
}
