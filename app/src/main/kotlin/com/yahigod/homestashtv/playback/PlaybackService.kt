@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.yahigod.homestashtv.playback

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.yahigod.homestashtv.profiles.AndroidServerProfileRepository
import com.yahigod.homestashtv.receiver.PlaybackStateReport
import com.yahigod.homestashtv.receiver.PlaybackStateValue
import com.yahigod.homestashtv.receiver.ReceiverRuntime

class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private lateinit var stateStore: PlaybackQueueStateStore
    private lateinit var profiles: AndroidServerProfileRepository
    private val handler = Handler(Looper.getMainLooper())

    private var activeCommandId = ""
    private var activeProfileId = ""
    private var activeSources = emptyList<ScenePlaybackSource>()
    private var loop = false
    private var reshuffle = false
    private var skippedSceneIds = emptyList<String>()
    private var handlingError = false
    private var networkRetry: Runnable? = null
    private var terminalState = false
    private var lastReportKey: String? = null

    private val stateCheckpoint = object : Runnable {
        override fun run() {
            persistCurrentState()
            if (player.isPlaying) {
                report(PlaybackStateValue.PLAYING, force = true)
            }
            handler.postDelayed(this, STATE_CHECKPOINT_INTERVAL_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int,
        ) {
            if (terminalState) {
                return
            }
            persistCurrentState()
            report(if (player.isPlaying) PlaybackStateValue.PLAYING else PlaybackStateValue.PAUSED)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (terminalState) {
                return
            }
            persistCurrentState()
            report(if (isPlaying) PlaybackStateValue.PLAYING else PlaybackStateValue.PAUSED)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                onQueueEnded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            skipFailedScene(error)
        }
    }

    override fun onCreate() {
        super.onCreate()

        stateStore = AndroidPlaybackQueueStateStore(this)
        profiles = AndroidServerProfileRepository(this)
        player = ExoPlayer.Builder(this).build().also {
            it.addListener(playerListener)
        }
        mediaSession = MediaSession.Builder(this, player).build()
        restorePausedQueue()
        handler.postDelayed(stateCheckpoint, STATE_CHECKPOINT_INTERVAL_MS)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_LOAD_QUEUE -> loadQueue(intent)
            ACTION_LOAD_TEST_SCENE -> loadTestScene(intent)
            ACTION_STOP_PLAYBACK -> stopPlayback(manual = true)
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopPlayback(manual = true)
    }

    override fun onDestroy() {
        handler.removeCallbacks(stateCheckpoint)
        networkRetry?.let(handler::removeCallbacks)
        persistCurrentState()
        player.removeListener(playerListener)
        mediaSession?.release()
        mediaSession = null
        player.release()

        super.onDestroy()
    }

    private fun loadQueue(intent: Intent) {
        val commandId = intent.getStringExtra(EXTRA_COMMAND_ID).orEmpty()
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty()
        val sceneIds = intent.getStringArrayListExtra(EXTRA_SCENE_IDS).orEmpty()
        val titles = intent.getStringArrayListExtra(EXTRA_TITLES).orEmpty()
        val streamUrls = intent.getStringArrayListExtra(EXTRA_STREAM_URLS).orEmpty()
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, -1)
        val startPositionMs = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L)

        if (
            commandId.isBlank() ||
            profileId.isBlank() ||
            sceneIds.isEmpty() ||
            sceneIds.size != titles.size ||
            sceneIds.size != streamUrls.size ||
            startIndex !in sceneIds.indices
        ) {
            report(
                PlaybackStateValue.FAILED,
                commandIdOverride = commandId,
                errorCode = "invalid_queue",
                force = true,
            )
            return
        }

        val resolvedSources = sceneIds.indices.map { index ->
            ScenePlaybackSource(
                sceneId = sceneIds[index],
                title = titles[index],
                streamUrl = streamUrls[index],
            )
        }
        val (sources, effectiveStartIndex) = effectiveQueue(
            sources = resolvedSources,
            startIndex = startIndex,
            continuePlayback = intent.getBooleanExtra(EXTRA_CONTINUE, true),
        )

        activeCommandId = commandId
        activeProfileId = profileId
        loop = intent.getBooleanExtra(EXTRA_LOOP, false)
        reshuffle = intent.getBooleanExtra(EXTRA_RESHUFFLE, false)
        skippedSceneIds = intent.getStringArrayListExtra(EXTRA_SKIPPED_SCENE_IDS).orEmpty()
        terminalState = false
        lastReportKey = null
        playCycle(
            sources = sources,
            startIndex = effectiveStartIndex,
            startPositionMs = startPositionMs,
            shouldPlay = true,
        )
    }

    private fun loadTestScene(intent: Intent) {
        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL) ?: return
        val apiKey = intent.getStringExtra(EXTRA_API_KEY).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val source = ScenePlaybackSource("debug", title, streamUrl)

        activeCommandId = ""
        activeProfileId = ""
        activeSources = listOf(source)
        loop = false
        reshuffle = false
        skippedSceneIds = emptyList()
        terminalState = false
        lastReportKey = null
        player.setMediaSources(
            mediaSourcesFor(activeSources, apiKey),
            0,
            0L,
        )
        player.prepare()
        player.play()
    }

    private fun restorePausedQueue() {
        val state = stateStore.load() ?: return
        val apiKey = profiles.getCredential(state.profileId).orEmpty()
        activeCommandId = state.commandId
        activeProfileId = state.profileId
        activeSources = state.sources
        loop = state.loop
        reshuffle = state.reshuffle
        skippedSceneIds = emptyList()
        terminalState = false
        lastReportKey = null

        player.setMediaSources(
            mediaSourcesFor(activeSources, apiKey),
            state.currentIndex,
            state.positionMs,
        )
        player.prepare()
        // A process restart restores the exact queue and position, but waits
        // for an explicit user action before playing.
        player.pause()
        persistCurrentState()
    }

    private fun playCycle(
        sources: List<ScenePlaybackSource>,
        startIndex: Int,
        startPositionMs: Long,
        shouldPlay: Boolean,
    ) {
        terminalState = false
        activeSources = sources
        val apiKey = profiles.getCredential(activeProfileId).orEmpty()
        player.setMediaSources(
            mediaSourcesFor(sources, apiKey),
            startIndex,
            startPositionMs.coerceAtLeast(0L),
        )
        player.prepare()
        if (shouldPlay) {
            player.play()
        } else {
            player.pause()
        }
        persistCurrentState()
        report(
            if (shouldPlay) PlaybackStateValue.PLAYING else PlaybackStateValue.PAUSED,
            force = true,
        )
    }

    private fun mediaSourcesFor(
        sources: List<ScenePlaybackSource>,
        apiKey: String,
    ): List<MediaSource> {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(stashAuthorizationHeaders(apiKey))
            .setUserAgent(USER_AGENT)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        return sources.map { source ->
            val mediaItem = MediaItem.Builder()
                .setMediaId(source.sceneId)
                .setUri(source.streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(source.title)
                        .build(),
                )
                .build()
            mediaSourceFactory.createMediaSource(mediaItem)
        }
    }

    private fun onQueueEnded() {
        if (activeSources.isEmpty()) {
            return
        }
        if (!loop) {
            terminalState = true
            persistCurrentState()
            report(PlaybackStateValue.COMPLETED, force = true)
            return
        }

        val nextCycle = buildNextQueueCycle(
            currentCycle = activeSources,
            previousFinalSceneId = activeSources.last().sceneId,
            reshuffle = reshuffle,
        )
        playCycle(
            sources = nextCycle,
            startIndex = 0,
            startPositionMs = 0L,
            shouldPlay = true,
        )
    }

    private fun skipFailedScene(error: PlaybackException) {
        if (handlingError || activeSources.isEmpty()) {
            return
        }
        handlingError = true
        if (isNetworkInterruption(error)) {
            persistCurrentState()
            report(
                PlaybackStateValue.PAUSED,
                errorCode = "network_interrupted",
                force = true,
            )
            val retry = Runnable {
                networkRetry = null
                if (activeSources.isEmpty()) {
                    handlingError = false
                    return@Runnable
                }
                handlingError = false
                player.prepare()
                player.play()
            }
            networkRetry = retry
            handler.postDelayed(retry, NETWORK_RETRY_INTERVAL_MS)
            return
        }

        val failedIndex = player.currentMediaItemIndex
            .coerceIn(activeSources.indices)
        val failedSceneId = activeSources[failedIndex].sceneId
        skippedSceneIds = (skippedSceneIds + failedSceneId).distinct()
        val remaining = activeSources.toMutableList().apply {
            removeAt(failedIndex)
        }

        if (remaining.isEmpty()) {
            terminalState = true
            activeSources = emptyList()
            stateStore.clear()
            report(
                PlaybackStateValue.FAILED,
                errorCode = actionablePlaybackErrorCode(error),
                force = true,
            )
            handlingError = false
            return
        }

        playCycle(
            sources = remaining,
            startIndex = failedIndex.coerceAtMost(remaining.lastIndex),
            startPositionMs = 0L,
            shouldPlay = true,
        )
        report(
            PlaybackStateValue.PLAYING,
            errorCode = "scene_skipped",
            force = true,
        )
        handlingError = false
    }

    private fun stopPlayback(manual: Boolean) {
        networkRetry?.let(handler::removeCallbacks)
        networkRetry = null
        handlingError = false
        terminalState = true
        if (manual) {
            report(PlaybackStateValue.STOPPED, force = true)
            stateStore.clear()
        } else {
            persistCurrentState()
        }
        player.stop()
        player.clearMediaItems()
        activeSources = emptyList()
        activeCommandId = ""
        activeProfileId = ""
        stopSelf()
    }

    private fun persistCurrentState() {
        if (
            activeCommandId.isBlank() ||
            activeProfileId.isBlank() ||
            activeSources.isEmpty() ||
            player.currentMediaItemIndex !in activeSources.indices
        ) {
            return
        }
        stateStore.save(
            PlaybackQueueState(
                commandId = activeCommandId,
                profileId = activeProfileId,
                sources = activeSources,
                currentIndex = player.currentMediaItemIndex,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                loop = loop,
                reshuffle = reshuffle,
                wasPlaying = player.isPlaying,
            ),
        )
    }

    private fun report(
        state: PlaybackStateValue,
        commandIdOverride: String? = null,
        errorCode: String? = null,
        force: Boolean = false,
    ) {
        val commandId = commandIdOverride ?: activeCommandId
        if (commandId.isBlank()) {
            return
        }
        val sceneId = player.currentMediaItem?.mediaId
        val queueIndex = player.currentMediaItemIndex
            .takeIf { it in activeSources.indices }
        val positionMs = player.currentPosition
            .coerceAtLeast(0L)
            .takeIf { queueIndex != null }
        val reportKey = listOf(
            state.wireValue,
            sceneId.orEmpty(),
            queueIndex?.toString().orEmpty(),
            errorCode.orEmpty(),
            skippedSceneIds.joinToString(","),
        ).joinToString("|")
        if (!force && reportKey == lastReportKey) {
            return
        }
        lastReportKey = reportKey
        ReceiverRuntime.reportPlayback(
            this,
            PlaybackStateReport(
                commandId = commandId,
                state = state,
                atMs = System.currentTimeMillis(),
                sceneId = sceneId,
                queueIndex = queueIndex,
                positionMs = positionMs,
                errorCode = errorCode,
                skippedSceneIds = skippedSceneIds,
            ),
        )
    }

    companion object {
        const val ACTION_LOAD_QUEUE =
            "com.yahigod.homestashtv.action.LOAD_QUEUE"
        const val ACTION_LOAD_TEST_SCENE =
            "com.yahigod.homestashtv.action.LOAD_TEST_SCENE"
        const val ACTION_STOP_PLAYBACK =
            "com.yahigod.homestashtv.action.STOP_PLAYBACK"

        const val EXTRA_COMMAND_ID = "command_id"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_SCENE_IDS = "scene_ids"
        const val EXTRA_TITLES = "titles"
        const val EXTRA_STREAM_URLS = "stream_urls"
        const val EXTRA_START_INDEX = "start_index"
        const val EXTRA_START_POSITION_MS = "start_position_ms"
        const val EXTRA_CONTINUE = "continue"
        const val EXTRA_LOOP = "loop"
        const val EXTRA_RESHUFFLE = "reshuffle"
        const val EXTRA_SKIPPED_SCENE_IDS = "skipped_scene_ids"

        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_API_KEY = "api_key"
        const val EXTRA_TITLE = "title"

        private const val USER_AGENT = "HomeStashTV/0.1"
        private const val STATE_CHECKPOINT_INTERVAL_MS = 5_000L
        private const val NETWORK_RETRY_INTERVAL_MS = 5_000L
    }
}

private fun isNetworkInterruption(error: PlaybackException): Boolean =
    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

private fun actionablePlaybackErrorCode(error: PlaybackException): String =
    when (error.errorCode) {
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        -> "unsupported_media"

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "media_http_error"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> "network_interrupted"

        else -> "playback_failed"
    }
