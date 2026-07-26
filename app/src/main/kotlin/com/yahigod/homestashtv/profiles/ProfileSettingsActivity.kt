package com.yahigod.homestashtv.profiles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.yahigod.homestashtv.ui.theme.HomeStashTvTheme
import kotlinx.coroutines.launch

class ProfileSettingsActivity : ComponentActivity() {
    private lateinit var repository: ServerProfileRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AndroidServerProfileRepository(applicationContext)

        setContent {
            HomeStashTvTheme {
                ProfileSettings(
                    repository = repository,
                    onClose = ::finish,
                )
            }
        }
    }
}

@Composable
internal fun ProfileSettings(
    repository: ServerProfileRepository,
    onClose: () -> Unit,
) {
    var profiles by remember { mutableStateOf(repository.listProfiles()) }
    var editingProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var addingProfile by remember { mutableStateOf(false) }

    fun refresh() {
        profiles = repository.listProfiles()
    }

    if (addingProfile || editingProfile != null) {
        ProfileEditor(
            existingProfile = editingProfile,
            repository = repository,
            onSaved = {
                addingProfile = false
                editingProfile = null
                refresh()
            },
            onDeleted = {
                addingProfile = false
                editingProfile = null
                refresh()
            },
            onCancel = {
                addingProfile = false
                editingProfile = null
            },
        )
    } else {
        BackHandler(onBack = onClose)
        ProfileList(
            profiles = profiles,
            onAdd = { addingProfile = true },
            onEdit = { editingProfile = it },
            onClose = onClose,
        )
    }
}

@Composable
private fun ProfileList(
    profiles: List<ServerProfile>,
    onAdd: () -> Unit,
    onEdit: (ServerProfile) -> Unit,
    onClose: () -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(profiles) {
        initialFocus.requestFocus()
    }

    ProfileBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeading(
                title = "Stash servers",
                subtitle = "Credentials are encrypted on this TV and never shown again.",
            )
            Spacer(modifier = Modifier.height(30.dp))

            if (profiles.isEmpty()) {
                Text(
                    text = "No server profiles configured.",
                    color = Color(0xFFC5D3DF),
                    fontSize = 25.sp,
                )
                Spacer(modifier = Modifier.height(30.dp))
            } else {
                profiles.forEachIndexed { index, profile ->
                    TvButton(
                        onClick = { onEdit(profile) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (index == 0) {
                                    Modifier.focusRequester(initialFocus)
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = profile.name,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = profile.serverUrl,
                                fontSize = 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                TvButton(
                    onClick = onAdd,
                    modifier = if (profiles.isEmpty()) {
                        Modifier.focusRequester(initialFocus)
                    } else {
                        Modifier
                    },
                ) {
                    Text("Add server", fontSize = 23.sp)
                }
                TvButton(onClick = onClose) {
                    Text("Back", fontSize = 23.sp)
                }
            }
        }
    }
}

@Composable
private fun ProfileEditor(
    existingProfile: ServerProfile?,
    repository: ServerProfileRepository,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val nameFocus = remember { FocusRequester() }
    var name by remember(existingProfile) { mutableStateOf(existingProfile?.name.orEmpty()) }
    var serverUrl by remember(existingProfile) {
        mutableStateOf(existingProfile?.serverUrl.orEmpty())
    }
    var apiKey by remember(existingProfile) { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler(onBack = onCancel)
    LaunchedEffect(Unit) {
        nameFocus.requestFocus()
    }

    fun currentCredential(): String? =
        apiKey.takeIf { it.isNotBlank() }
            ?: existingProfile?.let { repository.getCredential(it.id) }

    ProfileBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeading(
                title = if (existingProfile == null) "Add Stash server" else "Edit Stash server",
                subtitle = if (existingProfile == null) {
                    "Use the server address reachable from this TV."
                } else {
                    "Leave the API key blank to keep the encrypted key already stored."
                },
            )
            Spacer(modifier = Modifier.height(24.dp))

            ProfileTextField(
                label = "Profile name",
                value = name,
                onValueChange = { name = it },
                placeholder = "Normal Stash",
                modifier = Modifier.focusRequester(nameFocus),
            )
            Spacer(modifier = Modifier.height(18.dp))
            ProfileTextField(
                label = "Server address",
                value = serverUrl,
                onValueChange = { serverUrl = it },
                placeholder = "http://stash.local",
                keyboardType = KeyboardType.Uri,
            )
            Spacer(modifier = Modifier.height(18.dp))
            ProfileTextField(
                label = if (existingProfile == null) "API key" else "New API key (optional)",
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = if (existingProfile == null) "Required" else "Stored securely",
                keyboardType = KeyboardType.Password,
                visualTransformation = PasswordVisualTransformation(),
            )
            Spacer(modifier = Modifier.height(24.dp))

            statusMessage?.let {
                Text(
                    text = it,
                    color = if (statusIsError) Color(0xFFFFB4AB) else Color(0xFF9DE7B1),
                    fontSize = 21.sp,
                )
                Spacer(modifier = Modifier.height(18.dp))
            }

            if (confirmDelete && existingProfile != null) {
                Text(
                    text = "Delete ${existingProfile.name} and its local credential?",
                    color = Color(0xFFFFDAD6),
                    fontSize = 23.sp,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    TvButton(
                        onClick = {
                            repository.deleteProfile(existingProfile.id)
                            onDeleted()
                        },
                        destructive = true,
                    ) {
                        Text("Delete profile", fontSize = 22.sp)
                    }
                    TvButton(onClick = { confirmDelete = false }) {
                        Text("Keep profile", fontSize = 22.sp)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    TvButton(
                        onClick = {
                            val credential = currentCredential()
                            if (credential.isNullOrBlank()) {
                                statusMessage = "Enter an API key before testing this profile."
                                statusIsError = true
                                return@TvButton
                            }
                            isTesting = true
                            statusMessage = "Testing connection…"
                            statusIsError = false
                            scope.launch {
                                when (
                                    val result = StashConnectionTester().test(
                                        serverUrl,
                                        credential,
                                    )
                                ) {
                                    ConnectionTestResult.Success -> {
                                        statusMessage = "Connection successful."
                                        statusIsError = false
                                    }
                                    is ConnectionTestResult.Failure -> {
                                        statusMessage = result.message
                                        statusIsError = true
                                    }
                                }
                                isTesting = false
                            }
                        },
                        enabled = !isTesting,
                    ) {
                        Text(
                            if (isTesting) "Testing…" else "Test connection",
                            fontSize = 22.sp,
                        )
                    }
                    TvButton(
                        onClick = {
                            val normalizedUrl = runCatching {
                                normalizeServerUrl(serverUrl)
                            }.getOrNull()
                            when {
                                name.isBlank() -> {
                                    statusMessage = "Enter a profile name."
                                    statusIsError = true
                                }
                                normalizedUrl == null -> {
                                    statusMessage =
                                        "Enter a complete HTTP or HTTPS server address."
                                    statusIsError = true
                                }
                                currentCredential().isNullOrBlank() -> {
                                    statusMessage = "Enter an API key."
                                    statusIsError = true
                                }
                                else -> {
                                    runCatching {
                                        repository.saveProfile(
                                            ServerProfile(
                                                id = existingProfile?.id ?: newProfileId(),
                                                name = name.trim(),
                                                serverUrl = normalizedUrl,
                                            ),
                                            newCredential = apiKey.trim().takeIf {
                                                it.isNotBlank()
                                            },
                                        )
                                    }.onSuccess {
                                        onSaved()
                                    }.onFailure {
                                        statusMessage =
                                            "The profile could not be saved securely."
                                        statusIsError = true
                                    }
                                }
                            }
                        },
                    ) {
                        Text("Save", fontSize = 22.sp)
                    }
                    TvButton(onClick = onCancel) {
                        Text("Cancel", fontSize = 22.sp)
                    }
                }
                if (existingProfile != null) {
                    Spacer(modifier = Modifier.height(18.dp))
                    TvButton(
                        onClick = { confirmDelete = true },
                        destructive = true,
                    ) {
                        Text("Delete", fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Color(0xFFD7E5F0),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .background(Color(0xFF102535), RoundedCornerShape(10.dp))
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) Color(0xFF64C7FF) else Color(0xFF446174),
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 18.dp, vertical = 15.dp),
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 22.sp),
            cursorBrush = SolidColor(Color(0xFF64C7FF)),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                autoCorrectEnabled = false,
            ),
            visualTransformation = visualTransformation,
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Color(0xFF7890A1),
                            fontSize = 22.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun ProfileBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF17304A), Color(0xFF07121D)),
                    radius = 1_200f,
                ),
            )
            .padding(horizontal = 84.dp, vertical = 48.dp),
    ) {
        content()
    }
}

@Composable
private fun ScreenHeading(
    title: String,
    subtitle: String,
) {
    Text(
        text = "HOME STASH TV",
        color = Color(0xFFB7E6FF),
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 4.sp,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = title,
        color = Color.White,
        fontSize = 40.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = subtitle,
        color = Color(0xFFC5D3DF),
        fontSize = 21.sp,
    )
}

@Composable
private fun TvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.colors(
            containerColor = if (destructive) Color(0xFF5A2424) else Color(0xFF20384D),
            focusedContainerColor = if (destructive) Color(0xFFFFDAD6) else Color(0xFFE9F7FF),
            contentColor = Color.White,
            focusedContentColor = Color(0xFF07121D),
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(
                    width = 3.dp,
                    color = if (destructive) Color(0xFFFF8A80) else Color(0xFF64C7FF),
                ),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)) {
            content()
        }
    }
}
