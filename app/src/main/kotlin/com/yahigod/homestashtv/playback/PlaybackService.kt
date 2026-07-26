@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.yahigod.homestashtv.playback

import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LOAD_TEST_SCENE -> loadTestScene(intent)
            ACTION_STOP_PLAYBACK -> {
                player.stop()
                stopSelf()
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaSession?.player?.stop()
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
        }
        mediaSession = null
        player.release()

        super.onDestroy()
    }

    private fun loadTestScene(intent: Intent) {
        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL) ?: return
        val apiKey = intent.getStringExtra(EXTRA_API_KEY) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf(STASH_API_KEY_HEADER to apiKey))
            .setUserAgent(USER_AGENT)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .build(),
            )
            .build()

        player.setMediaSource(mediaSourceFactory.createMediaSource(mediaItem))
        player.prepare()
        player.play()
    }

    companion object {
        const val ACTION_LOAD_TEST_SCENE =
            "com.yahigod.homestashtv.action.LOAD_TEST_SCENE"
        const val ACTION_STOP_PLAYBACK =
            "com.yahigod.homestashtv.action.STOP_PLAYBACK"
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_API_KEY = "api_key"
        const val EXTRA_TITLE = "title"

        private const val STASH_API_KEY_HEADER = "ApiKey"
        private const val USER_AGENT = "HomeStashTV/0.1"
    }
}
