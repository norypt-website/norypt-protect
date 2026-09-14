package com.norypt.protect.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Palette mirrors the Norypt MDM admin console for brand consistency.
object NoryptColors {
    val Bg = Color(0xFF0A0C10)
    val Surface1 = Color(0xFF0D1117)
    val Surface2 = Color(0xFF141B26)

    /** One step above Surface2: pressed rows, chips, the logo tile. */
    val Surface3 = Color(0xFF1A2333)
    val Border = Color(0xFF1E2530)
    val BorderStrong = Color(0xFF2A3547)
    val Text = Color(0xFFE2E8F0)

    /** Headlines and numbers; a touch brighter than body text so hierarchy reads at a glance. */
    val TextStrong = Color(0xFFF8FAFC)
    val Muted = Color(0xFF8892A4)
    val MutedDeep = Color(0xFF4A5568)
    val Accent = Color(0xFF4A9EFF)

    /** Darker end of the accent gradient. */
    val AccentDeep = Color(0xFF2E7BDF)
    val AccentDim = Color(0xFF0F1E36)
    val Green = Color(0xFF22C55E)
    val Red = Color(0xFFEF4444)
    val Amber = Color(0xFFD29922)

    // Back-compat alias: existing components still reference NoryptColors.Surface.
    val Surface = Surface1

    /** Card face: a faint top light so panels read as surfaces rather than flat rectangles. */
    val cardGradient: Brush
        get() = Brush.verticalGradient(listOf(Surface2, Surface1))

    /** Primary action fill. */
    val accentGradient: Brush
        get() = Brush.horizontalGradient(listOf(Accent, AccentDeep))
}
