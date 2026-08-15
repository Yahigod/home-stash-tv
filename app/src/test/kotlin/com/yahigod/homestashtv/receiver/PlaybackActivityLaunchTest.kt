package com.yahigod.homestashtv.receiver

import android.content.Intent
import com.yahigod.homestashtv.playback.playbackActivityLaunchFlags
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackActivityLaunchTest {
    @Test
    fun `commands and cold-start reconnects converge on the active playback activity`() {
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
            playbackActivityLaunchFlags(),
        )
    }
}
