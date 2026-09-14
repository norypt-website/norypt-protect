package com.norypt.protect.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NoryptColorScheme = darkColorScheme(
    primary = NoryptColors.Accent,
    onPrimary = NoryptColors.Bg,
    primaryContainer = NoryptColors.AccentDim,
    onPrimaryContainer = NoryptColors.Accent,
    secondary = NoryptColors.Muted,
    onSecondary = NoryptColors.Bg,
    background = NoryptColors.Bg,
    onBackground = NoryptColors.Text,
    surface = NoryptColors.Surface,
    onSurface = NoryptColors.Text,
    surfaceVariant = NoryptColors.Surface2,
    onSurfaceVariant = NoryptColors.Muted,
    // Dialogs and sheets pick their containers from these; keeping them on-palette means
    // nothing pops up in Material's default purple-grey.
    surfaceContainerLowest = NoryptColors.Bg,
    surfaceContainerLow = NoryptColors.Surface1,
    surfaceContainer = NoryptColors.Surface1,
    surfaceContainerHigh = NoryptColors.Surface2,
    surfaceContainerHighest = NoryptColors.Surface3,
    outline = NoryptColors.BorderStrong,
    outlineVariant = NoryptColors.Border,
    error = NoryptColors.Red,
    onError = NoryptColors.Bg,
    tertiary = NoryptColors.Green,
)

private val NoryptShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun NoryptProtectTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NoryptColorScheme,
        typography = NoryptTypography,
        shapes = NoryptShapes,
        content = content,
    )
}
