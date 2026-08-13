package com.yahigod.homestashtv.playback

import kotlinx.coroutines.CancellationException

internal fun shouldApplyPlaybackIntent(
    currentCommandId: String?,
    currentReconnectOnly: Boolean?,
    nextReconnectOnly: Boolean,
): Boolean = !(
    nextReconnectOnly &&
        currentReconnectOnly == false &&
        !currentCommandId.isNullOrBlank()
    )

internal fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) {
        throw this
    }
}
