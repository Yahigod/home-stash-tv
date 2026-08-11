package com.yahigod.homestashtv.playback

import android.view.KeyEvent

internal enum class PlaybackRemoteCommand {
    TOGGLE_PLAY_PAUSE,
    SEEK_BACKWARD,
    SEEK_FORWARD,
    NEXT_AUDIO_TRACK,
    NEXT_SUBTITLE_TRACK,
    NEXT_MEDIA_ITEM,
    PREVIOUS_MEDIA_ITEM,
    EXIT,
}

internal data class PlaybackKeyDecision(
    val consumed: Boolean,
    val revealOverlay: Boolean = false,
    val command: PlaybackRemoteCommand? = null,
)

internal fun playbackKeyDecision(
    keyCode: Int,
    repeatCount: Int,
    action: Int = KeyEvent.ACTION_DOWN,
): PlaybackKeyDecision {
    val keyDecision = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        -> PlaybackKeyDecision(
            consumed = true,
            revealOverlay = true,
            command = PlaybackRemoteCommand.TOGGLE_PLAY_PAUSE.takeIf { repeatCount == 0 },
        )

        KeyEvent.KEYCODE_DPAD_LEFT -> playbackCommand(PlaybackRemoteCommand.SEEK_BACKWARD)
        KeyEvent.KEYCODE_DPAD_RIGHT -> playbackCommand(PlaybackRemoteCommand.SEEK_FORWARD)
        KeyEvent.KEYCODE_DPAD_UP -> playbackCommand(PlaybackRemoteCommand.NEXT_AUDIO_TRACK)
        KeyEvent.KEYCODE_DPAD_DOWN -> playbackCommand(PlaybackRemoteCommand.NEXT_SUBTITLE_TRACK)
        KeyEvent.KEYCODE_MEDIA_NEXT -> playbackCommand(PlaybackRemoteCommand.NEXT_MEDIA_ITEM)
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> playbackCommand(PlaybackRemoteCommand.PREVIOUS_MEDIA_ITEM)
        KeyEvent.KEYCODE_BACK -> PlaybackKeyDecision(
            consumed = true,
            command = PlaybackRemoteCommand.EXIT,
        )

        else -> PlaybackKeyDecision(consumed = false)
    }

    return when (action) {
        KeyEvent.ACTION_DOWN -> keyDecision
        KeyEvent.ACTION_UP -> keyDecision.copy(
            revealOverlay = false,
            command = null,
        )
        else -> PlaybackKeyDecision(consumed = false)
    }
}

private fun playbackCommand(command: PlaybackRemoteCommand): PlaybackKeyDecision =
    PlaybackKeyDecision(
        consumed = true,
        revealOverlay = true,
        command = command,
    )

internal fun shouldAutoHidePlaybackOverlay(
    isPlaying: Boolean,
    hasPlaybackError: Boolean,
    hasControlFeedback: Boolean,
): Boolean = isPlaying && !hasPlaybackError && !hasControlFeedback

internal const val PLAYBACK_OVERLAY_TIMEOUT_MS = 5_000L
