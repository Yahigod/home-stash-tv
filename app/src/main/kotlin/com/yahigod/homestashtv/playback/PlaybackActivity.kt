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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.yahigod.homestashtv.ui.theme.HomeStashTvTheme
import kotlinx.coroutines.delay

class PlaybackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serverUrl = intent.getStringExtra(PlaybackContract.EXTRA_SERVER_URL).orEmpty()
        val apiKey = intent.getStringExtra(PlaybackContract.EXTRA_API_KEY).orEmpty()
        val sceneId = intent.getStringExtra(PlaybackContract.EXTRA_SCENE_ID).orEmpty()
        val reconnectOnly = intent.action == ACTION_RECONNECT

        setContent {
            HomeStashTvTheme {
                BackHandler(onBack = ::stopPlaybackAndFinish)
                SingleScenePlaybackScreen(
                    serverUrl = serverUrl,
                    apiKey = apiKey,
                    sceneId = sceneId,
                    reconnectOnly = reconnectOnly,
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
private fun SingleScenePlaybackScreen(
    serverUrl: String,
    apiKey: String,
    sceneId: String,
    reconnectOnly: Boolean,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var title by remember { mutableStateOf<String?>(null) }
    var resolutionError by remember { mutableStateOf<String?>(null) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var controllerError by remember { mutableStateOf<String?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }

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
                                controllerError = "No active scene is available to resume."
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

    LaunchedEffect(serverUrl, apiKey, sceneId, reconnectOnly) {
        if (reconnectOnly) {
            return@LaunchedEffect
        }

        runCatching {
            StashSceneResolver().resolve(serverUrl, apiKey, sceneId)
        }.onSuccess { resolvedSource ->
            title = resolvedSource.title
            context.startService(
                Intent(context, PlaybackService::class.java).apply {
                    action = PlaybackService.ACTION_LOAD_TEST_SCENE
                    putExtra(PlaybackService.EXTRA_STREAM_URL, resolvedSource.streamUrl)
                    putExtra(PlaybackService.EXTRA_API_KEY, apiKey)
                    putExtra(PlaybackService.EXTRA_TITLE, resolvedSource.title)
                },
            )
        }.onFailure {
            resolutionError = if (it is SceneResolutionException) {
                it.message
            } else {
                "Could not prepare this scene for playback."
            }
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
                isPlaying = it.isPlaying
            }
            delay(PROGRESS_UPDATE_MS)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val error = resolutionError ?: controllerError ?: playbackError
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                handlePlaybackKey(
                    event = event.nativeKeyEvent,
                    controller = controller,
                    onExit = onExit,
                )
            },
    ) {
        if (controller != null && error == null) {
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
                    if (playerView.player !== controller) {
                        playerView.player = controller

                        if (
                            reconnectOnly &&
                            shouldPrimePausedVideoFrame(
                                hasMediaItem = controller.currentMediaItem != null,
                                playbackState = controller.playbackState,
                                playWhenReady = controller.playWhenReady,
                            )
                        ) {
                            playerView.postOnAnimation {
                                if (
                                    playerView.player === controller &&
                                    shouldPrimePausedVideoFrame(
                                        hasMediaItem = controller.currentMediaItem != null,
                                        playbackState = controller.playbackState,
                                        playWhenReady = controller.playWhenReady,
                                    )
                                ) {
                                    controller.seekTo(
                                        controller.currentMediaItemIndex,
                                        controller.currentPosition,
                                    )
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        PlaybackOverlay(
            title = title,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            error = error,
        )
    }
}

@Composable
private fun PlaybackOverlay(
    title: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    error: String?,
) {
    val status = when {
        error != null -> "Playback stopped"
        title == null -> "Resolving scene from Stash…"
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
                text = status,
                color = Color(0xFFB7E6FF),
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (error != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.72f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Unable to play this scene",
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
                Text(
                    text = "◀ 10s     OK  Play/Pause     10s ▶     BACK  Exit",
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
    onExit: () -> Unit,
): Boolean {
    if (event.action != KeyEvent.ACTION_DOWN) {
        return false
    }

    return when (event.keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        -> {
            if (event.repeatCount == 0) {
                controller?.let {
                    if (it.isPlaying) it.pause() else it.play()
                }
            }
            true
        }

        KeyEvent.KEYCODE_DPAD_LEFT -> {
            controller?.seekTo((controller.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(0L))
            true
        }

        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            controller?.seekTo(controller.currentPosition + SEEK_INCREMENT_MS)
            true
        }

        KeyEvent.KEYCODE_BACK -> {
            onExit()
            true
        }

        else -> false
    }
}

private fun actionablePlaybackError(error: PlaybackException): String = when (error.errorCode) {
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
