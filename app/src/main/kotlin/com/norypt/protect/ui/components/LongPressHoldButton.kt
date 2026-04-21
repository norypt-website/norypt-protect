package com.norypt.protect.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.norypt.protect.ui.theme.NoryptColors
import kotlinx.coroutines.launch

/**
 * A destructive-action button that requires the user to hold for [holdMillis]
 * milliseconds. Releases early = cancel (calls [onCancel]). Completes full
 * hold = triggers [onComplete] exactly once.
 */
@Composable
fun LongPressHoldButton(
    label: String,
    holdMillis: Int = 3000,
    modifier: Modifier = Modifier,
    onComplete: () -> Unit,
    onCancel: () -> Unit = {},
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(NoryptColors.Red.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val holdJob = scope.launch {
                        progress.animateTo(1f, tween(durationMillis = holdMillis, easing = androidx.compose.animation.core.LinearEasing))
                        onComplete()
                    }
                    val up = waitForUpOrCancellation()
                    if (progress.value < 1f) {
                        holdJob.cancel()
                        scope.launch { progress.snapTo(0f) }
                        if (up != null) onCancel()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(NoryptColors.Red, RoundedCornerShape(8.dp))
        )
        Text(label, color = NoryptColors.Text)
    }
}
