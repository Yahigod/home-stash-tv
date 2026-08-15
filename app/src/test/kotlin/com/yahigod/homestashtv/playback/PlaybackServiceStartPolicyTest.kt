package com.yahigod.homestashtv.playback

import androidx.media3.common.Player
import com.yahigod.homestashtv.receiver.PlaybackStateValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackServiceStartPolicyTest {
    @Test
    fun `application playback actions bypass the Media3 first-start path`() {
        assertEquals(
            PlaybackServiceStartRoute.LOAD_QUEUE,
            playbackServiceStartRoute(PlaybackService.ACTION_LOAD_QUEUE),
        )
        assertEquals(
            PlaybackServiceStartRoute.LOAD_TEST_SCENE,
            playbackServiceStartRoute(PlaybackService.ACTION_LOAD_TEST_SCENE),
        )
        assertEquals(
            PlaybackServiceStartRoute.STOP_PLAYBACK,
            playbackServiceStartRoute(PlaybackService.ACTION_STOP_PLAYBACK),
        )
    }

    @Test
    fun `Media3 and null start actions still delegate to Media3`() {
        assertEquals(
            PlaybackServiceStartRoute.MEDIA3,
            playbackServiceStartRoute("android.intent.action.MEDIA_BUTTON"),
        )
        assertEquals(
            PlaybackServiceStartRoute.MEDIA3,
            playbackServiceStartRoute(null),
        )
    }

    @Test
    fun `requested playback remains resolving until the player actually plays`() {
        assertEquals(
            PlaybackStateValue.RESOLVING,
            playerPlaybackStateReport(
                playbackState = Player.STATE_BUFFERING,
                playWhenReady = true,
                isPlaying = false,
            ),
        )
        assertEquals(
            PlaybackStateValue.RESOLVING,
            playerPlaybackStateReport(
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                isPlaying = false,
            ),
        )
        assertEquals(
            PlaybackStateValue.PLAYING,
            playerPlaybackStateReport(
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                isPlaying = true,
            ),
        )
    }

    @Test
    fun `paused and terminal states are not misreported as playing`() {
        assertEquals(
            PlaybackStateValue.PAUSED,
            playerPlaybackStateReport(
                playbackState = Player.STATE_READY,
                playWhenReady = false,
                isPlaying = false,
            ),
        )
        assertNull(
            playerPlaybackStateReport(
                playbackState = Player.STATE_IDLE,
                playWhenReady = false,
                isPlaying = false,
            ),
        )
        assertNull(
            playerPlaybackStateReport(
                playbackState = Player.STATE_ENDED,
                playWhenReady = false,
                isPlaying = false,
            ),
        )
    }
}
