package com.yahigod.homestashtv.receiver

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.yahigod.homestashtv.profiles.ServerProfile

class ReceiverProtocolTest {
    @Test
    fun `decodes a versioned play queue command`() {
        val command = decodeCommand(commandJson())

        assertEquals("command-1", command.id)
        assertEquals("receiver-1", command.receiverId)
        assertEquals("profile-1", command.profileId)
        assertEquals(listOf("42", "43"), command.sceneIds)
        assertEquals(1, command.startIndex)
        assertEquals(2500, command.startPositionMs)
        assertTrue(command.continuePlayback)
        assertFalse(command.loop)
        assertTrue(command.reshuffle)
    }

    @Test
    fun `rejects unsupported versions and invalid queue bounds`() {
        val unsupported = JSONObject(commandJson()).put("v", 2).toString()
        assertEquals(
            "unsupported_version",
            assertThrows(ProtocolException::class.java) {
                decodeCommand(unsupported)
            }.errorCode,
        )

        val invalid = JSONObject(commandJson())
        invalid.getJSONObject("command").put("start_index", 2)
        assertEquals(
            "invalid_command",
            assertThrows(ProtocolException::class.java) {
                decodeCommand(invalid.toString())
            }.errorCode,
        )
    }

    @Test
    fun `acknowledgement does not include credentials or command payload`() {
        val encoded = encodeAcknowledgement(
            CommandAcknowledgement(
                commandId = "command-1",
                status = AcknowledgementStatus.REJECTED,
                atMs = 3000,
                errorCode = "profile_missing",
            ),
        )
        val value = JSONObject(encoded)

        assertEquals(1, value.getInt("v"))
        assertEquals("rejected", value.getString("status"))
        assertEquals("profile_missing", value.getString("error_code"))
        assertFalse(encoded.contains("token", ignoreCase = true))
        assertFalse(encoded.contains("scene_ids"))
    }

    @Test
    fun `hello exposes only non-secret profile identity`() {
        val encoded = encodeHello(
            receiverId = "receiver-1",
            appVersion = "test",
            profiles = listOf(
                ServerProfile("profile-1", "Normal Stash", "http://private.invalid"),
            ),
        )
        val profile = JSONObject(encoded).getJSONArray("profiles").getJSONObject(0)

        assertEquals("profile-1", profile.getString("id"))
        assertEquals("Normal Stash", profile.getString("name"))
        assertFalse(encoded.contains("private.invalid"))
        assertFalse(encoded.contains("api", ignoreCase = true))
    }
}

internal fun commandJson(
    receiverId: String = "receiver-1",
    profileId: String = "profile-1",
    createdAtMs: Long = 1000,
    expiresAtMs: Long = 5000,
): String =
    """
    {
      "v": 1,
      "type": "command",
      "id": "command-1",
      "receiver_id": "$receiverId",
      "created_at_ms": $createdAtMs,
      "expires_at_ms": $expiresAtMs,
      "command": {
        "type": "play_queue",
        "profile_id": "$profileId",
        "scene_ids": ["42", "43"],
        "start_index": 1,
        "start_position_ms": 2500,
        "policy": {
          "continue": true,
          "loop": false,
          "reshuffle": true
        }
      }
    }
    """.trimIndent()
