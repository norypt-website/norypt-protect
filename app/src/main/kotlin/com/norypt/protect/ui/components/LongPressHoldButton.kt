package com.norypt.protect.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.norypt.protect.ui.theme.NoryptColors
import kotlinx.coroutines.launch

/**
 * A destructive-action button that requires the user to hold for [holdMillis]
 * milliseconds. Releasing early = cancel ([onCancel]). Completes full hold =
 * triggers [onComplete] exactly once.
 *
 * The gesture loop consumes every pointer event while the finger is down so a
 * surrounding `Modifier.verticalScroll` can't steal the press on the slightest
 * movement and prematurely cancel the hold.
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
    val onCompleteState = rememberUpdatedState(onComplete)
    val onCancelState = rememberUpdatedState(onCancel)

    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(NoryptColors.Red.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .pointerInput(holdMillis) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    var completed = false
                    val holdJob = scope.launch {
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = holdMillis, easing = LinearEasing),
                        )
                        if (progress.value >= 1f) {
                            completed = true
                            onCompleteState.value()
                        }
                    }

                    // Consume every pointer event in the Initial pass so the
                    // surrounding scrollable can never claim this gesture.
                    var released = false
                    while (!released) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                        if (event.changes.all { !it.pressed }) {
                            released = true
                        }
                    }

                    if (!completed) {
                        holdJob.cancel()
                        scope.launch { progress.snapTo(0f) }
                        onCancelState.value()
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
