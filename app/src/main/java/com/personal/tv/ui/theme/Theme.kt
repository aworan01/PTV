package com.personal.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary           = BlueAccent,
    onPrimary         = TextPrimary,
    primaryContainer  = NavySelected,
    secondary         = ProYellow,
    onSecondary       = NavyDeep,
    background        = NavyMid,
    onBackground      = TextPrimary,
    surface           = NavySurface,
    onSurface         = TextPrimary,
    surfaceVariant    = NavyBorder,
    onSurfaceVariant  = TextSecondary,
    error             = LiveRed,
    onError           = TextPrimary,
    outline           = NavyBorder,
)

@Composable
fun PersonalTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
