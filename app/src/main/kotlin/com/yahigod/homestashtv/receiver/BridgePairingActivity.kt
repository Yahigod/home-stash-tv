package com.yahigod.homestashtv.receiver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.yahigod.homestashtv.ui.theme.HomeStashTvTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BridgePairingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = AndroidBridgePairingRepository(applicationContext)
        setContent {
            HomeStashTvTheme {
                BridgePairingScreen(
                    repository = repository,
                    onClose = ::finish,
                    onPaired = {
                        ReceiverRuntime.get(applicationContext).reconnectNow()
                    },
                )
            }
        }
    }
}

@Composable
internal fun BridgePairingScreen(
    repository: BridgePairingRepository,
    onClose: () -> Unit,
    onPaired: () -> Unit,
    client: BridgePairingClient = remember { BridgePairingClient() },
) {
    var savedPairing by remember { mutableStateOf(repository.getPairing()) }
    var bridgeUrl by remember { mutableStateOf(savedPairing?.bridgeUrl.orEmpty()) }
    var deviceName by remember {
        mutableStateOf(savedPairing?.deviceName ?: "Living Room TV")
    }
    var pendingPairing by remember { mutableStateOf<PendingPairing?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val initialFocus = remember { FocusRequester() }

    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) {
        initialFocus.requestFocus()
    }

    LaunchedEffect(pendingPairing) {
        val pending = pendingPairing ?: return@LaunchedEffect
        while (System.currentTimeMillis() < pending.expiresAtMs) {
            delay(PAIRING_POLL_INTERVAL_MS)
            when (val result = client.pollPairing(bridgeUrl, pending.pairingId)) {
                PairingPollResult.Pending -> Unit
                is PairingPollResult.Approved -> {
                    val pairing = BridgePairing(
                        bridgeUrl = normalizeBridgeUrl(bridgeUrl),
                        receiverId = result.receiverId,
                        deviceName = deviceName.trim(),
                    )
                    runCatching {
                        repository.savePairing(pairing, result.receiverToken)
                    }.onSuccess {
                        savedPairing = pairing
                        pendingPairing = null
                        busy = false
                        statusMessage = "Pairing approved. Receiver connection started."
                        statusIsError = false
                        onPaired()
                    }.onFailure {
                        pendingPairing = null
                        busy = false
                        statusMessage = "The receiver credential could not be stored securely."
                        statusIsError = true
                    }
                    return@LaunchedEffect
                }
                is PairingPollResult.Failed -> {
                    pendingPairing = null
                    busy = false
                    statusMessage = result.message
                    statusIsError = true
                    return@LaunchedEffect
                }
            }
        }
        pendingPairing = null
        busy = false
        statusMessage = "The pairing code expired. Start pairing again."
        statusIsError = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF17304A), Color(0xFF07121D)),
                    radius = 1_300f,
                ),
            )
            .padding(horizontal = 96.dp, vertical = 54.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "BRIDGE PAIRING",
            color = Color(0xFFB7E6FF),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 4.sp,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (savedPairing == null) "Pair this receiver" else "Receiver paired",
            color = Color.White,
            fontSize = 46.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (savedPairing == null) {
                "A short code will appear here. Approve it on the bridge host."
            } else {
                "${savedPairing?.deviceName} · ${savedPairing?.bridgeUrl}"
            },
            color = Color(0xFFC5D3DF),
            fontSize = 23.sp,
        )
        Spacer(modifier = Modifier.height(28.dp))

        if (savedPairing != null) {
            statusMessage?.let {
                PairingStatus(it, statusIsError)
                Spacer(modifier = Modifier.height(20.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                PairingButton(
                    onClick = {
                        repository.clearPairing()
                        onPaired()
                        savedPairing = null
                        statusMessage = "Local pairing removed."
                        statusIsError = false
                    },
                    modifier = Modifier.focusRequester(initialFocus),
                    destructive = true,
                ) {
                    Text("Forget pairing", fontSize = 22.sp)
                }
                PairingButton(onClick = onClose) {
                    Text("Back", fontSize = 22.sp)
                }
            }
            return@Column
        }

        PairingTextField(
            label = "Bridge address",
            value = bridgeUrl,
            onValueChange = { bridgeUrl = it },
            placeholder = "http://bridge.local:8791",
            modifier = Modifier.focusRequester(initialFocus),
        )
        Spacer(modifier = Modifier.height(18.dp))
        PairingTextField(
            label = "Device name",
            value = deviceName,
            onValueChange = { deviceName = it },
            placeholder = "Living Room TV",
        )
        Spacer(modifier = Modifier.height(24.dp))

        pendingPairing?.let {
            Text(
                text = "PAIRING CODE",
                color = Color(0xFFB7E6FF),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
            )
            Text(
                text = it.code,
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 12.sp,
            )
            Text(
                text = "Waiting for approval…",
                color = Color(0xFFC5D3DF),
                fontSize = 22.sp,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        statusMessage?.let {
            PairingStatus(it, statusIsError)
            Spacer(modifier = Modifier.height(20.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            PairingButton(
                onClick = {
                    when {
                        deviceName.isBlank() -> {
                            statusMessage = "Enter a device name."
                            statusIsError = true
                        }
                        else -> {
                            busy = true
                            statusMessage = "Requesting a one-time code…"
                            statusIsError = false
                            scope.launch {
                                runCatching {
                                    client.startPairing(bridgeUrl, deviceName)
                                }.onSuccess {
                                    pendingPairing = it
                                    statusMessage = null
                                }.onFailure {
                                    busy = false
                                    statusMessage = it.message ?: "Could not reach the bridge."
                                    statusIsError = true
                                }
                            }
                        }
                    }
                },
                enabled = !busy,
            ) {
                Text(if (busy) "Pairing…" else "Start pairing", fontSize = 22.sp)
            }
            PairingButton(onClick = onClose) {
                Text("Back", fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun PairingStatus(
    message: String,
    isError: Boolean,
) {
    Text(
        text = message,
        color = if (isError) Color(0xFFFFB4AB) else Color(0xFF9DE7B1),
        fontSize = 21.sp,
    )
}

@Composable
private fun PairingTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, color = Color(0xFFC5D3DF), fontSize = 20.sp)
        Spacer(modifier = Modifier.height(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 24.sp),
            cursorBrush = SolidColor(Color(0xFF64C7FF)),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .background(Color(0xFF102536), RoundedCornerShape(10.dp))
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) Color(0xFF64C7FF) else Color(0xFF42627A),
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 18.dp, vertical = 15.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = Color(0xFF718797), fontSize = 24.sp)
                }
                inner()
            },
        )
    }
}

@Composable
private fun PairingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = if (destructive) Color(0xFF6D2B33) else Color(0xFF20384D),
            focusedContainerColor = if (destructive) Color(0xFFFFDAD6) else Color(0xFFE9F7FF),
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
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
        ) {
            content()
        }
    }
}

private const val PAIRING_POLL_INTERVAL_MS = 2_000L
