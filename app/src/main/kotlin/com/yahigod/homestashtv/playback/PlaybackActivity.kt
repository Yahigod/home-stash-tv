@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.yahigod.homestashtv.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.yahigod.homestashtv.profiles.AndroidServerProfileRepository
import com.yahigod.homestashtv.receiver.PlaybackStateReport
import com.yahigod.homestashtv.receiver.PlaybackStateValue
import com.yahigod.homestashtv.receiver.ReceiverRuntime
import com.yahigod.homestashtv.ui.theme.HomeStashTvTheme
import kotlinx.coroutines.delay

class PlaybackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val reconnectOnly = intent.action == ACTION_RECONNECT
        val commandId = intent.getStringExtra(PlaybackContract.EXTRA_COMMAND_ID).orEmpty()
        val profileId = intent.getStringExtra(PlaybackContract.EXTRA_PROFILE_ID).orEmpty()
        val repository = AndroidServerProfileRepository(applicationContext)
        val profile = profileId
            .takeIf { it.isNotBlank() }
            ?.let { id -> repository.listProfiles().singleOrNull { it.id == id } }
        val debugSceneId = intent.getStringExtra(PlaybackContract.EXTRA_SCENE_ID).orEmpty()
        val sceneIds = intent.getStringArrayListExtra(PlaybackContract.EXTRA_SCENE_IDS)
            ?.toList()
            ?: debugSceneId.takeIf { it.isNotBlank() }?.let(::listOf)
            ?: emptyList()
        val serverUrl = profile?.serverUrl
            ?: intent.getStringExtra(PlaybackContract.EXTRA_SERVER_URL).orEmpty()
        val apiKey = profile?.let { repository.getCredential(it.id).orEmpty() }
            ?: intent.getStringExtra(PlaybackContract.EXTRA_API_KEY).orEmpty()
        val configurationError = when {
            reconnectOnly -> null
            profileId.isNotBlank() && profile == null ->
                "The selected Stash profile no longer exists on this TV."
            sceneIds.isEmpty() -> "The playback command did not contain any scenes."
            else -> null
        }

        setContent {
            HomeStashTvTheme {
                BackHandler(onBack = ::stopPlaybackAndFinish)
                QueuePlaybackScreen(
                    commandId = commandId,
                    profileId = profileId,
                    serverUrl = serverUrl,
                    apiKey = apiKey,
                    sceneIds = sceneIds,
                    requestedStartIndex = intent.getIntExtra(
                        PlaybackContract.EXTRA_START_INDEX,
                        0,
                    ),
                    requestedStartPositionMs = intent.getLongExtra(
                        PlaybackContract.EXTRA_START_POSITION_MS,
                        0L,
                    ),
                    continuePlayback = intent.getBooleanExtra(
                        PlaybackContract.EXTRA_CONTINUE,
                        true,
                    ),
                    loop = intent.getBooleanExtra(PlaybackContract.EXTRA_LOOP, false),
                    reshuffle = intent.getBooleanExtra(
                        PlaybackContract.EXTRA_RESHUFFLE,
                        false,
                    ),
                    reconnectOnly = reconnectOnly,
                    configurationError = configurationError,
                    onExit = ::stopPlaybackAndFinish,
                )
            }
        }
    }

    private fun stopPlaybackAndFinish() {
        startService(
            Intent(this, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_STOP_PLAYBACK
            },
        )
        finish()
    }

    companion object {
        const val ACTION_RECONNECT =
            "com.yahigod.homestashtv.action.RECONNECT_PLAYBACK"
    }
}

@Composable
private fun QueuePlaybackScreen(
    commandId: String,
    profileId: String,
    serverUrl: String,
    apiKey: String,
    sceneIds: List<String>,
    requestedStartIndex: Int,
    requestedStartPositionMs: Long,
    continuePlayback: Boolean,
    loop: Boolean,
    reshuffle: Boolean,
    reconnectOnly: Boolean,
    configurationError: String?,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val surfaceFocusRequester = remember { FocusRequester() }
    val primaryControlFocusRequester = remember { FocusRequester() }
    var title by remember { mutableStateOf<String?>(null) }
    var resolutionError by remember { mutableStateOf(configurationError) }
    var queueWarning by remember { mutableStateOf<String?>(null) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var controllerError by remember { mutableStateOf<String?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var controlFeedback by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var queueIndex by remember { mutableIntStateOf(0) }
    var queueSize by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var overlayVisible by remember { mutableStateOf(true) }
    var overlayInteraction by remember { mutableIntStateOf(0) }

    DisposableEffect(context) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java),
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        var disposed = false

        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }
                    .onSuccess {
                        if (!disposed) {
                            controller = it
                            if (reconnectOnly && it.currentMediaItem == null) {
                                controllerError = "No active queue is available to resume."
                            }
                        }
                    }
                    .onFailure {
                        if (!disposed) {
                            controllerError =
                                "Could not connect to the playback service. Restart the app."
                        }
                    }
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            disposed = true
            MediaController.releaseFuture(controllerFuture)
        }
    }

    LaunchedEffect(
        commandId,
        profileId,
        serverUrl,
        apiKey,
        sceneIds,
        requestedStartIndex,
        requestedStartPositionMs,
        continuePlayback,
        loop,
        reshuffle,
        reconnectOnly,
        configurationError,
    ) {
        if (reconnectOnly || configurationError != null) {
            return@LaunchedEffect
        }
        if (requestedStartIndex !in sceneIds.indices) {
            resolutionError = "The requested starting scene is outside the queue."
            reportResolutionFailure(context, commandId, "invalid_queue")
            return@LaunchedEffect
        }

        if (commandId.isNotBlank()) {
            ReceiverRuntime.reportPlayback(
                context,
                PlaybackStateReport(
                    commandId = commandId,
                    state = PlaybackStateValue.RESOLVING,
                    atMs = System.currentTimeMillis(),
                ),
            )
        }
        runCatching {
            StashSceneResolver().resolveQueue(
                serverUrl = serverUrl,
                apiKey = apiKey,
                sceneIds = sceneIds,
                requestedStartIndex = requestedStartIndex,
            )
        }.onSuccess { queue ->
            title = queue.sources[queue.startIndex].title
            queueWarning = queue.skippedScenes
                .takeIf { it.isNotEmpty() }
                ?.let {
                    "${it.size} unavailable scene${if (it.size == 1) " was" else "s were"} skipped."
                }

            if (commandId.isBlank()) {
                val source = queue.sources[queue.startIndex]
                context.startService(
                    Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_LOAD_TEST_SCENE
                        putExtra(PlaybackService.EXTRA_STREAM_URL, source.streamUrl)
                        putExtra(PlaybackService.EXTRA_API_KEY, apiKey)
                        putExtra(PlaybackService.EXTRA_TITLE, source.title)
                    },
                )
            } else {
                context.startService(
                    Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_LOAD_QUEUE
                        putExtra(PlaybackService.EXTRA_COMMAND_ID, commandId)
                        putExtra(PlaybackService.EXTRA_PROFILE_ID, profileId)
                        putStringArrayListExtra(
                            PlaybackService.EXTRA_SCENE_IDS,
                            ArrayList(queue.sources.map { it.sceneId }),
                        )
                        putStringArrayListExtra(
                            PlaybackService.EXTRA_TITLES,
                            ArrayList(queue.sources.map { it.title }),
                        )
                        putStringArrayListExtra(
                            PlaybackService.EXTRA_STREAM_URLS,
                            ArrayList(queue.sources.map { it.streamUrl }),
                        )
                        putExtra(PlaybackService.EXTRA_START_INDEX, queue.startIndex)
                        putExtra(
                            PlaybackService.EXTRA_START_POSITION_MS,
                            requestedStartPositionMs.takeIf {
                                queue.startPositionApplies
                            } ?: 0L,
                        )
                        putExtra(PlaybackService.EXTRA_CONTINUE, continuePlayback)
                        putExtra(PlaybackService.EXTRA_LOOP, loop)
                        putExtra(PlaybackService.EXTRA_RESHUFFLE, reshuffle)
                        putStringArrayListExtra(
                            PlaybackService.EXTRA_SKIPPED_SCENE_IDS,
                            ArrayList(queue.skippedScenes.map { it.sceneId }),
                        )
                    },
                )
            }
        }.onFailure {
            resolutionError = when (it) {
                is QueueResolutionException,
                is SceneResolutionException,
                -> it.message
                else -> "Could not prepare this queue for playback."
            }
            reportResolutionFailure(context, commandId, "queue_resolution_failed")
        }
    }

    DisposableEffect(controller) {
        val activeController = controller
        if (activeController == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playbackError = actionablePlaybackError(error)
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    playbackError = null
                    title = mediaItem?.mediaMetadata?.title?.toString()
                }
            }
            activeController.addListener(listener)
            activeController.playerError?.let {
                playbackError = actionablePlaybackError(it)
            }
            onDispose { activeController.removeListener(listener) }
        }
    }

    LaunchedEffect(controller) {
        while (true) {
            controller?.let {
                title = it.currentMediaItem?.mediaMetadata?.title?.toString()
                positionMs = it.currentPosition.coerceAtLeast(0L)
                durationMs = it.duration.coerceAtLeast(0L)
                queueIndex = it.currentMediaItemIndex.coerceAtLeast(0)
                queueSize = it.mediaItemCount
                isPlaying = it.isPlaying
            }
            delay(PROGRESS_UPDATE_MS)
        }
    }

    LaunchedEffect(controlFeedback) {
        if (controlFeedback != null) {
            delay(CONTROL_FEEDBACK_MS)
            controlFeedback = null
        }
    }

    val error = resolutionError ?: controllerError ?: playbackError
    val activeController = controller
    val canAutoHideOverlay = shouldAutoHidePlaybackOverlay(
        isPlaying = isPlaying,
        hasPlaybackError = error != null,
        hasControlFeedback = controlFeedback != null,
    )
    val showOverlay = overlayVisible || !canAutoHideOverlay

    LaunchedEffect(canAutoHideOverlay, overlayInteraction) {
        if (canAutoHideOverlay) {
            overlayVisible = true
            delay(PLAYBACK_OVERLAY_TIMEOUT_MS)
            overlayVisible = false
        } else {
            overlayVisible = true
        }
    }

    LaunchedEffect(showOverlay, activeController, error) {
        if (showOverlay && activeController != null && error == null) {
            primaryControlFocusRequester.requestFocus()
        } else {
            surfaceFocusRequester.requestFocus()
        }
    }

    val onOverlayInteraction = {
        overlayVisible = true
        overlayInteraction += 1
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(surfaceFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                handlePlaybackKey(
                    event = event.nativeKeyEvent,
                    controller = controller,
                    onInteraction = onOverlayInteraction,
                    onFeedback = { controlFeedback = it },
                    onExit = onExit,
                )
            },
    ) {
        if (activeController != null && error == null) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        useController = false
                        keepScreenOn = true
                        isFocusable = false
                        setKeepContentOnPlayerReset(true)
                    }
                },
                update = { playerView ->
                    if (playerView.player !== activeController) {
                        playerView.player = activeController

                        if (
                            reconnectOnly &&
                            shouldPrimePausedVideoFrame(
                                hasMediaItem = activeController.currentMediaItem != null,
                                playbackState = activeController.playbackState,
                                playWhenReady = activeController.playWhenReady,
                            )
                        ) {
                            playerView.postOnAnimation {
                                if (
                                    playerView.player === activeController &&
                                    shouldPrimePausedVideoFrame(
                                        hasMediaItem =
                                        activeController.currentMediaItem != null,
                                        playbackState = activeController.playbackState,
                                        playWhenReady = activeController.playWhenReady,
                                    )
                                ) {
                                    activeController.seekTo(
                                        activeController.currentMediaItemIndex,
                                        activeController.currentPosition,
                                    )
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showOverlay) {
            PlaybackOverlay(
                title = title,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                queueIndex = queueIndex,
                queueSize = queueSize,
                warning = queueWarning,
                controlFeedback = controlFeedback,
                error = error,
                controlsReady = activeController != null,
                primaryControlFocusRequester = primaryControlFocusRequester,
                onTogglePlayback = {
                    onOverlayInteraction()
                    togglePlayback(controller)
                },
            )
        }
    }
}

@Composable
private fun PlaybackOverlay(
    title: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    queueIndex: Int,
    queueSize: Int,
    warning: String?,
    controlFeedback: String?,
    error: String?,
    controlsReady: Boolean,
    primaryControlFocusRequester: FocusRequester,
    onTogglePlayback: () -> Unit,
) {
    val status = when {
        error != null -> "Playback stopped"
        title == null -> "Resolving queue from Stash…"
        isPlaying -> "Playing"
        else -> "Paused or buffering"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (error == null) {
                    Brush.verticalGradient(
                        listOf(
                            Color(0xA6000000),
                            Color.Transparent,
                            Color.Transparent,
                            Color(0xC9000000),
                        ),
                    )
                } else {
                    Brush.radialGradient(
                        listOf(Color(0xFF2A1720), Color(0xFF08090C)),
                    )
                },
            )
            .padding(horizontal = 64.dp, vertical = 48.dp),
    ) {
        Column(modifier = Modifier.align(Alignment.TopStart)) {
            Text(
                text = title ?: "HOME STASH TV",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildString {
                    append(status)
                    if (queueSize > 0) {
                        append("  •  Queue ${queueIndex + 1}/$queueSize")
                    }
                },
                color = Color(0xFFB7E6FF),
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            warning?.let {
                Text(
                    text = it,
                    color = Color(0xFFFFD180),
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        if (error != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.72f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Unable to play this queue",
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = error,
                    color = Color(0xFFFFC8D2),
                    fontSize = 24.sp,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    text = "Press BACK to return",
                    color = Color(0xFFC5D3DF),
                    fontSize = 20.sp,
                    modifier = Modifier.padding(top = 28.dp),
                )
            }
        } else {
            controlFeedback?.let {
                Text(
                    text = it,
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                    color = Color.White,
                    fontSize = 22.sp,
                )
                if (controlsReady) {
                    Button(
                        onClick = onTogglePlayback,
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .focusRequester(primaryControlFocusRequester),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF20384D),
                            focusedContainerColor = Color(0xFFE9F7FF),
                            contentColor = Color.White,
                            focusedContentColor = Color(0xFF07121D),
                        ),
                        border = ButtonDefaults.border(
                            focusedBorder = Border(
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 3.dp,
                                    color = Color(0xFF64C7FF),
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ),
                        ),
                    ) {
                        Text(
                            text = if (isPlaying) "Pause" else "Play",
                            fontSize = 22.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        )
                    }
                }
                Text(
                    text = "◀/▶ Seek     OK Play/Pause     ▲ Audio     ▼ Subtitles     BACK Exit",
                    color = Color(0xFFD2DFE9),
                    fontSize = 20.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

private fun handlePlaybackKey(
    event: KeyEvent,
    controller: MediaController?,
    onInteraction: () -> Unit,
    onFeedback: (String) -> Unit,
    onExit: () -> Unit,
): Boolean {
    val decision = playbackKeyDecision(
        keyCode = event.keyCode,
        repeatCount = event.repeatCount,
        action = event.action,
    )
    if (decision.revealOverlay) {
        onInteraction()
    }
    when (decision.command) {
        PlaybackRemoteCommand.TOGGLE_PLAY_PAUSE -> togglePlayback(controller)
        PlaybackRemoteCommand.SEEK_BACKWARD ->
            controller?.seekTo((controller.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(0L))
        PlaybackRemoteCommand.SEEK_FORWARD ->
            controller?.seekTo(controller.currentPosition + SEEK_INCREMENT_MS)
        PlaybackRemoteCommand.NEXT_AUDIO_TRACK ->
            onFeedback(controller?.let(::selectNextAudioTrack) ?: "Audio is not ready.")
        PlaybackRemoteCommand.NEXT_SUBTITLE_TRACK ->
            onFeedback(controller?.let(::selectNextSubtitleTrack) ?: "Subtitles are not ready.")
        PlaybackRemoteCommand.NEXT_MEDIA_ITEM -> controller?.seekToNextMediaItem()
        PlaybackRemoteCommand.PREVIOUS_MEDIA_ITEM -> controller?.seekToPreviousMediaItem()
        PlaybackRemoteCommand.EXIT -> onExit()
        null -> Unit
    }
    return decision.consumed
}

private fun togglePlayback(controller: MediaController?) {
    controller?.let {
        if (it.isPlaying) it.pause() else it.play()
    }
}

private data class SelectableTrack(
    val group: Tracks.Group,
    val index: Int,
)

private fun selectNextAudioTrack(controller: MediaController): String {
    val tracks = selectableTracks(controller, C.TRACK_TYPE_AUDIO)
    if (tracks.isEmpty()) {
        return "No alternate audio tracks."
    }
    val selectedIndex = tracks.indexOfFirst { it.group.isTrackSelected(it.index) }
    val next = tracks[(selectedIndex + 1).mod(tracks.size)]
    controller.trackSelectionParameters = controller.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        .setOverrideForType(TrackSelectionOverride(next.group.mediaTrackGroup, next.index))
        .build()
    return "Audio: ${trackLabel(next.group.getTrackFormat(next.index))}"
}

private fun selectNextSubtitleTrack(controller: MediaController): String {
    val tracks = selectableTracks(controller, C.TRACK_TYPE_TEXT)
    if (tracks.isEmpty()) {
        return "No subtitle tracks."
    }
    val selectedIndex = tracks.indexOfFirst { it.group.isTrackSelected(it.index) }
    if (selectedIndex == tracks.lastIndex) {
        controller.trackSelectionParameters = controller.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        return "Subtitles: Off"
    }

    val next = tracks[if (selectedIndex < 0) 0 else selectedIndex + 1]
    controller.trackSelectionParameters = controller.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setOverrideForType(TrackSelectionOverride(next.group.mediaTrackGroup, next.index))
        .build()
    return "Subtitles: ${trackLabel(next.group.getTrackFormat(next.index))}"
}

private fun selectableTracks(
    controller: MediaController,
    type: Int,
): List<SelectableTrack> =
    controller.currentTracks.groups
        .filter { it.type == type }
        .flatMap { group ->
            (0 until group.length)
                .filter { group.isTrackSupported(it) }
                .map { SelectableTrack(group, it) }
        }

private fun trackLabel(format: Format): String =
    format.label
        ?: format.language?.uppercase()
        ?: format.id
        ?: "Track"

private fun actionablePlaybackError(error: PlaybackException): String =
    when (error.errorCode) {
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        -> "The TV decoder cannot play this file's video or audio format."

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> "Stash could not serve the media. Check the local network and API access."

        else -> "Media3 reported ${error.errorCodeName}. Try another scene or file format."
    }

private fun reportResolutionFailure(
    context: Context,
    commandId: String,
    errorCode: String,
) {
    if (commandId.isBlank()) {
        return
    }
    ReceiverRuntime.reportPlayback(
        context,
        PlaybackStateReport(
            commandId = commandId,
            state = PlaybackStateValue.FAILED,
            atMs = System.currentTimeMillis(),
            errorCode = errorCode,
        ),
    )
}

internal fun shouldPrimePausedVideoFrame(
    hasMediaItem: Boolean,
    playbackState: Int,
    playWhenReady: Boolean,
): Boolean =
    hasMediaItem &&
        playbackState == Player.STATE_READY &&
        !playWhenReady

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private const val SEEK_INCREMENT_MS = 10_000L
private const val PROGRESS_UPDATE_MS = 500L
private const val CONTROL_FEEDBACK_MS = 2_500L
