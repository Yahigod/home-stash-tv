package com.yahigod.homestashtv.debug

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.yahigod.homestashtv.playback.PlaybackService

/**
 * Debug-only entry point for real-device acceptance tests.
 *
 * Starting the private playback service from inside the application preserves
 * the release manifest's security boundary while allowing ADB to prove that
 * Media3 can initialize on the target TV without requesting media.
 */
class PlaybackServiceProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, PlaybackService::class.java))
        finish()
    }
}
