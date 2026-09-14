package com.norypt.protect.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.ui.theme.NoryptColors

/**
 * The one card surface every screen uses: gradient face, hairline border, 14 dp corners.
 *
 * [accent] paints a 3 dp bar down the left edge and tints the border — used to mark an armed
 * trigger or an enabled setting. [tint] washes the face for notes and warnings. Both border
 * changes animate over 220 ms so a toggle reads as a state change rather than a repaint.
 */
@Composable
fun NoryptCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    tint: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val borderTarget = accent?.copy(alpha = 0.45f) ?: tint?.copy(alpha = 0.35f) ?: NoryptColors.Border
    val border by animateColorAsState(borderTarget, tween(220), label = "cardBorder")
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NoryptColors.cardGradient)
            .then(if (tint != null) Modifier.background(tint.copy(alpha = 0.08f)) else Modifier)
            .border(1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .height(IntrinsicSize.Min),
    ) {
        if (accent != null) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
        }
        Column(Modifier.weight(1f).padding(contentPadding), content = content)
    }
}

/** Screen title with an optional one-line purpose underneath and an optional trailing slot. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                color = NoryptColors.TextStrong,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
            )
            if (subtitle != null) {
                Text(subtitle, color = NoryptColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Tracked uppercase eyebrow that groups the cards below it. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = NoryptColors.MutedDeep,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(top = 6.dp),
    )
}

/** Small status pill: tier, "DEVICE OWNER", recording state. */
@Composable
fun TagPill(text: String, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.28f), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Tinted note for caveats and warnings; [color] sets both the wash and the text. */
@Composable
fun NoteCard(text: String, color: Color, modifier: Modifier = Modifier, title: String? = null) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (title != null) {
            Text(title.uppercase(), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Text(text, color = color, fontSize = 12.sp, lineHeight = 17.sp)
    }
}
