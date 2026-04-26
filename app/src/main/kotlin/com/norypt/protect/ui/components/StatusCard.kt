package com.norypt.protect.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.norypt.protect.ui.theme.NoryptColors

enum class StatusLevel { Armed, Partial, Disabled }

@Composable
fun StatusCard(level: StatusLevel, title: String, subtitle: String, modifier: Modifier = Modifier) {
    val dotColor = when (level) {
        StatusLevel.Armed -> NoryptColors.Green
        StatusLevel.Partial -> NoryptColors.Amber
        StatusLevel.Disabled -> NoryptColors.Red
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(NoryptColors.Surface, RoundedCornerShape(8.dp))
            .border(1.dp, NoryptColors.Border, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = NoryptColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = NoryptColors.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
