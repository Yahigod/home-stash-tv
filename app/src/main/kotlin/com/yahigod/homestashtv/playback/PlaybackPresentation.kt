package com.yahigod.homestashtv.playback

import androidx.media3.common.Player

internal enum class PlaybackPresentationState(
    val statusText: String,
    val attentionText: String? = null,
) {
    RESOLVING("Resolving queue from Stash…", "Preparing video…"),
    CONNECTING("Connecting to playback…", "Connecting to playback…"),
    RECONNECTING("Restoring playback…", "Restoring playback…"),
    BUFFERING("Loading video…", "Loading video…"),
    PLAYING("Playing"),
    PAUSED("Paused"),
    COMPLETED("Playback complete"),
    FAILED("Playback stopped"),
}

internal fun playbackPresentationState(
    hasError: Boolean,
    isResolvingQueue: Boolean,
    reconnectOnly: Boolean,
    controllerConnected: Boolean,
    hasMediaItem: Boolean,
    playbackState: Int,
    playWhenReady: Boolean,
    isPlaying: Boolean,
): PlaybackPresentationState = when {
    hasError -> PlaybackPresentationState.FAILED
    isResolvingQueue -> PlaybackPresentationState.RESOLVING
    !controllerConnected -> if (reconnectOnly) {
        PlaybackPresentationState.RECONNECTING
    } else {
        PlaybackPresentationState.CONNECTING
    }
    !hasMediaItem -> PlaybackPresentationState.BUFFERING
    playbackState == Player.STATE_ENDED -> PlaybackPresentationState.COMPLETED
    playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING ->
        PlaybackPresentationState.BUFFERING
    isPlaying -> PlaybackPresentationState.PLAYING
    playWhenReady -> PlaybackPresentationState.BUFFERING
    else -> PlaybackPresentationState.PAUSED
}
