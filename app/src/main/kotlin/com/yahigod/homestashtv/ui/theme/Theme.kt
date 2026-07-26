package com.yahigod.homestashtv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val HomeStashTvColors = darkColorScheme(
    primary = Color(0xFF64C7FF),
    onPrimary = Color(0xFF07121D),
    secondary = Color(0xFF9ED9FF),
    background = Color(0xFF07121D),
    onBackground = Color.White,
    surface = Color(0xFF112638),
    onSurface = Color.White,
)

@Composable
internal fun HomeStashTvTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = HomeStashTvColors,
        content = content,
    )
}
