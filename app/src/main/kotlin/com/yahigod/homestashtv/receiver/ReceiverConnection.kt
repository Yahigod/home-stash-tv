package com.yahigod.homestashtv.receiver

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.yahigod.homestashtv.BuildConfig
import com.yahigod.homestashtv.playback.PlaybackActivity
import com.yahigod.homestashtv.playback.PlaybackContract
import com.yahigod.homestashtv.profiles.AndroidServerProfileRepository
import com.yahigod.homestashtv.profiles.ServerProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

enum class ReceiverConnectionStatus(val message: String) {
    UNPAIRED("Bridge not paired"),
    CONNECTING("Connecting to bridge"),
    CONNECTED("Connected to bridge"),
    DISCONNECTED("Bridge offline; retrying"),
    REVOKED("Pairing revoked"),
}

internal fun interface ReceiverCommandExecutor {
    fun execute(command: PlayQueueCommand)
}

internal class ReceiverCommandProcessor(
    private val receiverId: String,
    private val profileRepository: ServerProfileRepository,
    private val commandLedger: CommandLedger,
    private val commandExecutor: ReceiverCommandExecutor,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun process(text: String): CommandAcknowledgement? {
        val command = runCatching { decodeCommand(text) }.getOrNull() ?: return null
        val now = clock()
        return when {
            command.receiverId != receiverId -> CommandAcknowledgement(
                command.id,
                AcknowledgementStatus.REJECTED,
                now,
                "receiver_mismatch",
            )
            now >= command.expiresAtMs -> CommandAcknowledgement(
                command.id,
                AcknowledgementStatus.EXPIRED,
                now,
            )
            profileRepository.listProfiles().none { it.id == command.profileId } ->
                CommandAcknowledgement(
                    command.id,
                    AcknowledgementStatus.REJECTED,
                    now,
                    "profile_missing",
                )
            !commandLedger.claim(command.id, command.expiresAtMs, now) ->
                CommandAcknowledgement(
                    command.id,
                    AcknowledgementStatus.DUPLICATE,
                    now,
                )
            else -> runCatching {
                commandExecutor.execute(command)
                CommandAcknowledgement(
                    command.id,
                    AcknowledgementStatus.ACCEPTED,
                    now,
                )
            }.getOrElse {
                CommandAcknowledgement(
                    command.id,
                    AcknowledgementStatus.REJECTED,
                    now,
                    "execution_failed",
                )
            }
        }
    }
}

internal class ReceiverConnection(
    private val pairingRepository: BridgePairingRepository,
    private val profileRepository: ServerProfileRepository,
    private val commandLedger: CommandLedger,
    private val commandExecutor: ReceiverCommandExecutor,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build(),
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val reconnectPolicy = ReconnectPolicy()
    private val running = AtomicBoolean(false)
    private var socket: WebSocket? = null
    private var reconnectScheduled = false
    private val mutableStatus = MutableStateFlow(ReceiverConnectionStatus.UNPAIRED)
    val status: StateFlow<ReceiverConnectionStatus> = mutableStatus

    fun start() {
        if (running.compareAndSet(false, true)) {
            connect()
        }
    }

    fun stop() {
        running.set(false)
        reconnectScheduled = false
        handler.removeCallbacksAndMessages(RECONNECT_TOKEN)
        socket?.close(1000, "App stopped")
        socket = null
    }

    fun reconnectNow() {
        if (!running.get()) {
            running.set(true)
        }
        reconnectScheduled = false
        handler.removeCallbacksAndMessages(RECONNECT_TOKEN)
        socket?.cancel()
        socket = null
        reconnectPolicy.reset()
        connect()
    }

    fun reportPlayback(state: PlaybackStateReport): Boolean =
        socket?.send(encodePlaybackState(state)) == true

    private fun connect() {
        if (!running.get() || socket != null) {
            return
        }
        val pairing = pairingRepository.getPairing()
        val token = pairingRepository.getReceiverToken()
        if (pairing == null || token.isNullOrBlank()) {
            mutableStatus.value = ReceiverConnectionStatus.UNPAIRED
            return
        }
        mutableStatus.value = ReceiverConnectionStatus.CONNECTING
        val request = runCatching {
            Request.Builder()
                .url(websocketUrl(pairing.bridgeUrl))
                .header("Authorization", "Bearer $token")
                .build()
        }.getOrElse {
            mutableStatus.value = ReceiverConnectionStatus.DISCONNECTED
            scheduleReconnect()
            return
        }
        socket = client.newWebSocket(request, Listener(pairing))
    }

    private fun scheduleReconnect() {
        if (!running.get() || reconnectScheduled) {
            return
        }
        reconnectScheduled = true
        handler.postAtTime(
            {
                reconnectScheduled = false
                connect()
            },
            RECONNECT_TOKEN,
            android.os.SystemClock.uptimeMillis() + reconnectPolicy.nextDelayMs(),
        )
    }

    private fun handleCommand(
        webSocket: WebSocket,
        pairing: BridgePairing,
        text: String,
    ) {
        ReceiverCommandProcessor(
            receiverId = pairing.receiverId,
            profileRepository = profileRepository,
            commandLedger = commandLedger,
            commandExecutor = commandExecutor,
            clock = clock,
        ).process(text)?.let {
            webSocket.send(encodeAcknowledgement(it))
        }
    }

    private inner class Listener(
        private val pairing: BridgePairing,
    ) : WebSocketListener() {
        override fun onOpen(
            webSocket: WebSocket,
            response: Response,
        ) {
            socket = webSocket
            reconnectPolicy.reset()
            mutableStatus.value = ReceiverConnectionStatus.CONNECTED
            webSocket.send(
                encodeHello(
                    receiverId = pairing.receiverId,
                    appVersion = BuildConfig.VERSION_NAME,
                    profiles = profileRepository.listProfiles(),
                ),
            )
        }

        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) {
            handleCommand(webSocket, pairing, text)
        }

        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            webSocket.close(code, null)
        }

        override fun onClosed(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            if (socket === webSocket) {
                socket = null
            }
            if (code == REVOKED_CLOSE_CODE) {
                mutableStatus.value = ReceiverConnectionStatus.REVOKED
                return
            }
            mutableStatus.value = ReceiverConnectionStatus.DISCONNECTED
            scheduleReconnect()
        }

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?,
        ) {
            if (socket === webSocket) {
                socket = null
            }
            if (response?.code == 401 || response?.code == 403) {
                mutableStatus.value = ReceiverConnectionStatus.REVOKED
                return
            }
            mutableStatus.value = ReceiverConnectionStatus.DISCONNECTED
            scheduleReconnect()
        }
    }
}

class AndroidReceiverCommandExecutor(
    private val context: Context,
) : ReceiverCommandExecutor {
    override fun execute(command: PlayQueueCommand) {
        context.startActivity(
            Intent(context, PlaybackActivity::class.java).apply {
                addFlags(playbackActivityLaunchFlags())
                putExtra(
                    PlaybackContract.EXTRA_COMMAND_ID,
                    command.id,
                )
                putExtra(PlaybackContract.EXTRA_PROFILE_ID, command.profileId)
                putStringArrayListExtra(
                    PlaybackContract.EXTRA_SCENE_IDS,
                    ArrayList(command.sceneIds),
                )
                putExtra(PlaybackContract.EXTRA_START_INDEX, command.startIndex)
                putExtra(PlaybackContract.EXTRA_START_POSITION_MS, command.startPositionMs)
                putExtra(PlaybackContract.EXTRA_CONTINUE, command.continuePlayback)
                putExtra(PlaybackContract.EXTRA_LOOP, command.loop)
                putExtra(PlaybackContract.EXTRA_RESHUFFLE, command.reshuffle)
            },
        )
    }
}

internal fun playbackActivityLaunchFlags(): Int =
    Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_CLEAR_TOP or
        Intent.FLAG_ACTIVITY_SINGLE_TOP

internal object ReceiverRuntime {
    private var connection: ReceiverConnection? = null

    fun get(context: Context): ReceiverConnection = synchronized(this) {
        connection ?: run {
            val appContext = context.applicationContext
            val profiles = AndroidServerProfileRepository(appContext)
            ReceiverConnection(
                pairingRepository = AndroidBridgePairingRepository(appContext),
                profileRepository = profiles,
                commandLedger = AndroidCommandLedger(appContext),
                commandExecutor = AndroidReceiverCommandExecutor(appContext),
            ).also { connection = it }
        }
    }

    fun reportPlayback(
        context: Context,
        state: PlaybackStateReport,
    ): Boolean = get(context).reportPlayback(state)
}

private const val REVOKED_CLOSE_CODE = 4003
private val RECONNECT_TOKEN = Any()
