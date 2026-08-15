package com.yahigod.homestashtv.playback

import android.content.Intent

/**
 * Route every playback intent through the active PlaybackActivity instance.
 *
 * During a cold start, MainActivity can finish probing the restored media
 * session after the bridge has already delivered a new queue. CLEAR_TOP plus
 * SINGLE_TOP makes both intent orderings converge on the same activity, where
 * the playback intent policy can reject a stale reconnect.
 */
internal fun playbackActivityLaunchFlags(): Int =
    Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_CLEAR_TOP or
        Intent.FLAG_ACTIVITY_SINGLE_TOP
