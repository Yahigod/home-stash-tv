package com.yahigod.homestashtv.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPresentationTest {
    @Test
    fun `error takes precedence over every transient state`() {
        assertEquals(
            PlaybackPresentationState.FAILED,
            state(
                hasError = true,
                isResolvingQueue = true,
                controllerConnected = false,
            ),
        )
    }

    @Test
    fun `queue resolution remains visible even while prior media plays`() {
        assertEquals(
            PlaybackPresentationState.RESOLVING,
            state(
                isResolvingQueue = true,
                hasMediaItem = true,
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                isPlaying = true,
            ),
        )
    }

    @Test
    fun `reopening reports restoration until controller connects`() {
        assertEquals(
            PlaybackPresentationState.RECONNECTING,
            state(
                reconnectOnly = true,
                controllerConnected = false,
            ),
        )
    }

    @Test
    fun `connected player without media reports loading`() {
        assertEquals(
            PlaybackPresentationState.BUFFERING,
            state(controllerConnected = true),
        )
    }

    @Test
    fun `buffering ready playing and paused remain distinct`() {
        assertEquals(
            PlaybackPresentationState.BUFFERING,
            state(
                hasMediaItem = true,
                playbackState = Player.STATE_BUFFERING,
                playWhenReady = true,
            ),
        )
        assertEquals(
            PlaybackPresentationState.PLAYING,
            state(
                hasMediaItem = true,
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                isPlaying = true,
            ),
        )
        assertEquals(
            PlaybackPresentationState.PAUSED,
            state(
                hasMediaItem = true,
                playbackState = Player.STATE_READY,
            ),
        )
    }

    @Test
    fun `ready player waiting to play remains a loading state`() {
        assertEquals(
            PlaybackPresentationState.BUFFERING,
            state(
                hasMediaItem = true,
                playbackState = Player.STATE_READY,
                playWhenReady = true,
            ),
        )
    }

    @Test
    fun `ended playback has an explicit terminal presentation`() {
        assertEquals(
            PlaybackPresentationState.COMPLETED,
            state(
                hasMediaItem = true,
                playbackState = Player.STATE_ENDED,
            ),
        )
    }

    private fun state(
        hasError: Boolean = false,
        isResolvingQueue: Boolean = false,
        reconnectOnly: Boolean = false,
        controllerConnected: Boolean = true,
        hasMediaItem: Boolean = false,
        playbackState: Int = Player.STATE_IDLE,
        playWhenReady: Boolean = false,
        isPlaying: Boolean = false,
    ): PlaybackPresentationState = playbackPresentationState(
        hasError = hasError,
        isResolvingQueue = isResolvingQueue,
        reconnectOnly = reconnectOnly,
        controllerConnected = controllerConnected,
        hasMediaItem = hasMediaItem,
        playbackState = playbackState,
        playWhenReady = playWhenReady,
        isPlaying = isPlaying,
    )
}
