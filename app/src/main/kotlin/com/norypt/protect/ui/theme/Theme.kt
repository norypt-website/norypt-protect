package com.norypt.protect.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NoryptColorScheme = darkColorScheme(
    primary = NoryptColors.Accent,
    onPrimary = NoryptColors.Bg,
    background = NoryptColors.Bg,
    onBackground = NoryptColors.Text,
    surface = NoryptColors.Surface,
    onSurface = NoryptColors.Text,
    surfaceVariant = NoryptColors.Border,
    onSurfaceVariant = NoryptColors.Muted,
    error = NoryptColors.Red,
    onError = NoryptColors.Bg,
    tertiary = NoryptColors.Green,
)

@Composable
fun NoryptProtectTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NoryptColorScheme,
        typography = NoryptTypography,
        content = content,
    )
}
