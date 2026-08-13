package com.yahigod.homestashtv.playback

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackIntentPolicyTest {
    @Test
    fun `stale reconnect cannot replace a newly delivered command`() {
        assertFalse(
            shouldApplyPlaybackIntent(
                currentCommandId = "new-command",
                currentReconnectOnly = false,
                nextReconnectOnly = true,
            ),
        )
    }

    @Test
    fun `new command can replace reconnect or another command`() {
        assertTrue(
            shouldApplyPlaybackIntent(
                currentCommandId = "old-command",
                currentReconnectOnly = true,
                nextReconnectOnly = false,
            ),
        )
        assertTrue(
            shouldApplyPlaybackIntent(
                currentCommandId = "old-command",
                currentReconnectOnly = false,
                nextReconnectOnly = false,
            ),
        )
    }

    @Test
    fun `reconnect remains valid before a command is active`() {
        assertTrue(
            shouldApplyPlaybackIntent(
                currentCommandId = null,
                currentReconnectOnly = null,
                nextReconnectOnly = true,
            ),
        )
    }

    @Test
    fun `queue resolution never converts cancellation into failure`() {
        assertThrows(CancellationException::class.java) {
            CancellationException("superseded").rethrowIfCancellation()
        }
        RuntimeException("network").rethrowIfCancellation()
    }
}
