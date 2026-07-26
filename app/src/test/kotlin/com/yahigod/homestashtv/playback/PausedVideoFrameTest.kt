package com.yahigod.homestashtv.playback

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PausedVideoFrameTest {
    @Test
    fun `prepared paused scene needs its frame primed after reconnect`() {
        assertTrue(
            shouldPrimePausedVideoFrame(
                hasMediaItem = true,
                playbackState = Player.STATE_READY,
                playWhenReady = false,
            ),
        )
    }

    @Test
    fun `playing scene is not disturbed after reconnect`() {
        assertFalse(
            shouldPrimePausedVideoFrame(
                hasMediaItem = true,
                playbackState = Player.STATE_READY,
                playWhenReady = true,
            ),
        )
    }

    @Test
    fun `unprepared scene is not primed`() {
        assertFalse(
            shouldPrimePausedVideoFrame(
                hasMediaItem = true,
                playbackState = Player.STATE_BUFFERING,
                playWhenReady = false,
            ),
        )
    }
}
