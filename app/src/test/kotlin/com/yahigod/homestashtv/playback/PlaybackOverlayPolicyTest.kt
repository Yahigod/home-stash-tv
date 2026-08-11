package com.yahigod.homestashtv.playback

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackOverlayPolicyTest {
    @Test
    fun `overlay uses a five second inactivity timeout`() {
        assertEquals(5_000L, PLAYBACK_OVERLAY_TIMEOUT_MS)
    }

    @Test
    fun `only uninterrupted playback permits overlay auto hide`() {
        assertTrue(
            shouldAutoHidePlaybackOverlay(
                isPlaying = true,
                hasPlaybackError = false,
                hasControlFeedback = false,
            ),
        )
        assertFalse(
            shouldAutoHidePlaybackOverlay(
                isPlaying = false,
                hasPlaybackError = false,
                hasControlFeedback = false,
            ),
        )
        assertFalse(
            shouldAutoHidePlaybackOverlay(
                isPlaying = true,
                hasPlaybackError = true,
                hasControlFeedback = false,
            ),
        )
        assertFalse(
            shouldAutoHidePlaybackOverlay(
                isPlaying = true,
                hasPlaybackError = false,
                hasControlFeedback = true,
            ),
        )
    }

    @Test
    fun `playback actions reveal the overlay and retain their commands`() {
        val expected = mapOf(
            KeyEvent.KEYCODE_DPAD_CENTER to PlaybackRemoteCommand.TOGGLE_PLAY_PAUSE,
            KeyEvent.KEYCODE_ENTER to PlaybackRemoteCommand.TOGGLE_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to PlaybackRemoteCommand.TOGGLE_PLAY_PAUSE,
            KeyEvent.KEYCODE_DPAD_LEFT to PlaybackRemoteCommand.SEEK_BACKWARD,
            KeyEvent.KEYCODE_DPAD_RIGHT to PlaybackRemoteCommand.SEEK_FORWARD,
            KeyEvent.KEYCODE_DPAD_UP to PlaybackRemoteCommand.NEXT_AUDIO_TRACK,
            KeyEvent.KEYCODE_DPAD_DOWN to PlaybackRemoteCommand.NEXT_SUBTITLE_TRACK,
            KeyEvent.KEYCODE_MEDIA_NEXT to PlaybackRemoteCommand.NEXT_MEDIA_ITEM,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS to PlaybackRemoteCommand.PREVIOUS_MEDIA_ITEM,
        )

        expected.forEach { (keyCode, command) ->
            val decision = playbackKeyDecision(keyCode, repeatCount = 0)

            assertTrue(decision.consumed)
            assertTrue(decision.revealOverlay)
            assertEquals(command, decision.command)
        }
    }

    @Test
    fun `held select is consumed without toggling playback repeatedly`() {
        val decision = playbackKeyDecision(
            keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
            repeatCount = 1,
        )

        assertTrue(decision.consumed)
        assertTrue(decision.revealOverlay)
        assertNull(decision.command)
    }

    @Test
    fun `back preserves immediate exit without flashing the overlay`() {
        val decision = playbackKeyDecision(KeyEvent.KEYCODE_BACK, repeatCount = 0)

        assertTrue(decision.consumed)
        assertFalse(decision.revealOverlay)
        assertEquals(PlaybackRemoteCommand.EXIT, decision.command)
    }

    @Test
    fun `unrelated remote key is left to the system`() {
        val decision = playbackKeyDecision(KeyEvent.KEYCODE_VOLUME_UP, repeatCount = 0)

        assertFalse(decision.consumed)
        assertFalse(decision.revealOverlay)
        assertNull(decision.command)
    }
}
