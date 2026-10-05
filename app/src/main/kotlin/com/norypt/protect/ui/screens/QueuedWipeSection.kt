package com.norypt.protect.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.prefs.ProtectPrefsKeys
import com.norypt.protect.ui.components.NoryptCard
import com.norypt.protect.ui.components.PinEntryDialog
import com.norypt.protect.ui.components.SecondaryButton
import com.norypt.protect.ui.theme.NoryptColors
import kotlinx.coroutines.delay

private const val QUEUE_POLL_MS = 5_000L

/**
 * A wipe that failed and is waiting for its automatic retry, with a way for the owner to call it
 * off behind the App PIN. Shown only while one is queued; the retry tick may settle it at any time,
 * so the queue is re-read while the screen is open.
 */
@Composable
fun QueuedWipeSection() {
    val ctx = LocalContext.current
    var reason by remember { mutableStateOf(ProtectPrefs.pendingWipeReason(ctx)) }
    var dryRunQueued by remember { mutableStateOf(ProtectPrefsKeys.pendingWipeDryRun(ProtectPrefs.store(ctx))) }
    var confirming by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(QUEUE_POLL_MS)
            reason = ProtectPrefs.pendingWipeReason(ctx)
            dryRunQueued = ProtectPrefsKeys.pendingWipeDryRun(ProtectPrefs.store(ctx))
        }
    }

    val queued = reason ?: return
    NoryptCard(accent = NoryptColors.Red) {
        Text(
            "A wipe is queued ($queued) and retries automatically",
            color = NoryptColors.TextStrong,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "It failed and is tried again automatically for up to an hour after the trigger." +
                if (dryRunQueued) " It was triggered in dry-run: nothing will be erased." else "",
            color = NoryptColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(10.dp))
        SecondaryButton(label = "Cancel queued wipe", onClick = { confirming = true }, color = NoryptColors.Red)
    }
    if (confirming) {
        PinEntryDialog(
            title = "Enter App PIN to cancel the queued wipe",
            onVerified = {
                PanicHandler.cancelQueuedWipe(ctx)
                reason = ProtectPrefs.pendingWipeReason(ctx)
                confirming = false
            },
            onDismiss = { confirming = false },
        )
    }
}
