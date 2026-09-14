package com.norypt.protect.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.ui.theme.NoryptColors

/** The one filled action on a screen: accent gradient, 52 dp tall, 12 dp corners. */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = NoryptColors.Muted,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(if (enabled) NoryptColors.accentGradient else SolidColor(NoryptColors.AccentDim)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Outlined secondary action. [color] sets text and border; red for destructive, muted for quiet. */
@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NoryptColors.Accent,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            disabledContentColor = NoryptColors.MutedDeep,
        ),
        border = BorderStroke(1.dp, if (enabled) color.copy(alpha = 0.45f) else NoryptColors.Border),
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
