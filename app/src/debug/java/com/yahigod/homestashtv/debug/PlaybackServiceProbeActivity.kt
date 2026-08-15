package com.yahigod.homestashtv.debug

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.yahigod.homestashtv.playback.PlaybackActivity
import com.yahigod.homestashtv.playback.PlaybackService
import com.yahigod.homestashtv.playback.playbackActivityLaunchFlags

/**
 * Debug-only entry point for real-device acceptance tests.
 *
 * Starting the private playback service from inside the application preserves
 * the release manifest's security boundary while allowing ADB to probe Media3
 * with either an empty session or a caller-supplied test stream.
 */
class PlaybackServiceProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val streamUrl = intent.getStringExtra(PlaybackService.EXTRA_STREAM_URL)
        if (streamUrl.isNullOrBlank()) {
            startService(Intent(this, PlaybackService::class.java))
            finish()
            return
        }

        startService(
            Intent(this, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_LOAD_TEST_SCENE
                putExtra(PlaybackService.EXTRA_STREAM_URL, streamUrl)
                putExtra(
                    PlaybackService.EXTRA_API_KEY,
                    intent.getStringExtra(PlaybackService.EXTRA_API_KEY).orEmpty(),
                )
                putExtra(
                    PlaybackService.EXTRA_TITLE,
                    intent.getStringExtra(PlaybackService.EXTRA_TITLE).orEmpty(),
                )
            },
        )

        Handler(Looper.getMainLooper()).postDelayed(
            {
                startActivity(
                    Intent(this, PlaybackActivity::class.java).apply {
                        action = PlaybackActivity.ACTION_RECONNECT
                        addFlags(playbackActivityLaunchFlags())
                    },
                )
                finish()
            },
            PLAYER_LAUNCH_DELAY_MS,
        )
    }

    companion object {
        private const val PLAYER_LAUNCH_DELAY_MS = 500L
    }
}
