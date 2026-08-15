package com.yahigod.homestashtv.playback

import androidx.media3.common.Player
import com.yahigod.homestashtv.receiver.PlaybackStateValue

internal enum class PlaybackServiceStartRoute {
    LOAD_QUEUE,
    LOAD_TEST_SCENE,
    STOP_PLAYBACK,
    MEDIA3,
}

internal fun playbackServiceStartRoute(action: String?): PlaybackServiceStartRoute =
    when (action) {
        PlaybackService.ACTION_LOAD_QUEUE -> PlaybackServiceStartRoute.LOAD_QUEUE
        PlaybackService.ACTION_LOAD_TEST_SCENE -> PlaybackServiceStartRoute.LOAD_TEST_SCENE
        PlaybackService.ACTION_STOP_PLAYBACK -> PlaybackServiceStartRoute.STOP_PLAYBACK
        else -> PlaybackServiceStartRoute.MEDIA3
    }

internal fun playerPlaybackStateReport(
    playbackState: Int,
    playWhenReady: Boolean,
    isPlaying: Boolean,
): PlaybackStateValue? {
    val hasPreparedMedia =
        playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY

    return when {
        playbackState == Player.STATE_ENDED -> null
        isPlaying -> PlaybackStateValue.PLAYING
        playWhenReady && hasPreparedMedia ->
            PlaybackStateValue.RESOLVING
        !playWhenReady && hasPreparedMedia ->
            PlaybackStateValue.PAUSED
        else -> null
    }
}
