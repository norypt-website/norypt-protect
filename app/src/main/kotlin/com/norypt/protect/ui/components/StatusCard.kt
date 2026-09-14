package com.norypt.protect.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.ui.theme.NoryptColors

enum class StatusLevel { Armed, Partial, Disabled }

/**
 * The hero card on Home. The whole card takes the state's colour as a wash and a border,
 * with a haloed indicator, so the protection state is readable from across a room; the
 * colour change animates so a tier change is seen happening rather than snapping.
 */
@Composable
fun StatusCard(level: StatusLevel, title: String, subtitle: String, modifier: Modifier = Modifier) {
    val target = when (level) {
        StatusLevel.Armed -> NoryptColors.Green
        StatusLevel.Partial -> NoryptColors.Amber
        StatusLevel.Disabled -> NoryptColors.Red
    }
    val tint by animateColorAsState(target, tween(300), label = "statusTint")
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.20f), NoryptColors.Surface1)))
            .border(1.dp, tint.copy(alpha = 0.40f), shape)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(tint.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .background(tint, CircleShape),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                color = NoryptColors.TextStrong,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = NoryptColors.Muted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
