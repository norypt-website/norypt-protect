package com.norypt.protect.ui.theme

import androidx.compose.ui.graphics.Color

// Palette mirrors the Norypt MDM admin console for brand consistency.
object NoryptColors {
    val Bg = Color(0xFF0A0C10)
    val Surface1 = Color(0xFF0D1117)
    val Surface2 = Color(0xFF141B26)
    val Border = Color(0xFF1E2530)
    val Text = Color(0xFFE2E8F0)
    val Muted = Color(0xFF8892A4)
    val MutedDeep = Color(0xFF4A5568)
    val Accent = Color(0xFF4A9EFF)
    val AccentDim = Color(0xFF0F1E36)
    val Green = Color(0xFF22C55E)
    val Red = Color(0xFFEF4444)
    val Amber = Color(0xFFD29922)

    // Back-compat alias: existing components still reference NoryptColors.Surface.
    val Surface = Surface1
}
