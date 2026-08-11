package com.yahigod.homestashtv.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackErrorMessageTest {
    @Test
    fun `decoder failures identify an unsupported format`() {
        assertEquals(
            "The TV decoder cannot play this file's video or audio format.",
            actionablePlaybackError(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED),
        )
    }

    @Test
    fun `http and network failures provide distinct recovery guidance`() {
        assertEquals(
            "Stash refused the media request. Check the server profile and media access.",
            actionablePlaybackError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS),
        )
        assertEquals(
            "The TV lost its connection to Stash. Check the local network and try again.",
            actionablePlaybackError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT),
        )
    }

    @Test
    fun `generic failures remain actionable and redact connection details`() {
        val message = actionablePlaybackError(PlaybackException.ERROR_CODE_UNSPECIFIED)

        assertEquals("Playback failed. Try another scene or file format.", message)
        listOf("http://", "https://", "ApiKey", "Bearer").forEach {
            assertFalse(message.contains(it, ignoreCase = true))
        }
    }
}
