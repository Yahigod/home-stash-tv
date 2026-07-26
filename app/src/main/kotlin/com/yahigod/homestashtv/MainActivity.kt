package com.yahigod.homestashtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.yahigod.homestashtv.ui.theme.HomeStashTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HomeStashTvTheme {
                BackHandler(onBack = ::exitApp)
                ReceiverHomeScreen(onExit = ::exitApp)
            }
        }
    }

    private fun exitApp() {
        finishAndRemoveTask()
    }
}

@Composable
internal fun ReceiverHomeScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exitFocusRequester = remember { FocusRequester() }
    var exitHasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        exitFocusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF17304A), Color(0xFF07121D)),
                    radius = 1_200f,
                ),
            )
            .padding(horizontal = 96.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "HOME STASH TV",
                color = Color(0xFFB7E6FF),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 5.sp,
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Receiver ready",
                color = Color.White,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = ReceiverStatus.IDLE.message,
                color = Color(0xFFC5D3DF),
                fontSize = 26.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(52.dp))
            Button(
                onClick = onExit,
                modifier = Modifier
                    .focusRequester(exitFocusRequester)
                    .onFocusChanged { exitHasFocus = it.isFocused }
                    .semantics {
                        contentDescription =
                            if (exitHasFocus) "Exit app, focused" else "Exit app"
                    },
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
                    text = "Exit",
                    fontSize = 24.sp,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                )
            }
        }
    }
}
