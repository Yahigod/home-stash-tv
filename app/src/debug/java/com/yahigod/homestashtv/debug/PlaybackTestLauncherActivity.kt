package com.yahigod.homestashtv.debug

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.yahigod.homestashtv.playback.PlaybackActivity
import com.yahigod.homestashtv.playback.PlaybackContract
import com.yahigod.homestashtv.playback.playbackActivityLaunchFlags

/**
 * Debug-only ADB entry point for the single-scene playback proof.
 *
 * Private configuration is forwarded to the non-exported playback activity and
 * remains runtime-only. Release APKs do not contain this exported entry point.
 */
class PlaybackTestLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startActivity(
            Intent(this, PlaybackActivity::class.java).apply {
                addFlags(playbackActivityLaunchFlags())
                putExtra(
                    PlaybackContract.EXTRA_SERVER_URL,
                    intent.getStringExtra(PlaybackContract.EXTRA_SERVER_URL),
                )
                putExtra(
                    PlaybackContract.EXTRA_API_KEY,
                    intent.getStringExtra(PlaybackContract.EXTRA_API_KEY),
                )
                putExtra(
                    PlaybackContract.EXTRA_SCENE_ID,
                    intent.getStringExtra(PlaybackContract.EXTRA_SCENE_ID),
                )
            },
        )
        finish()
    }
}
