package com.norypt.protect.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.norypt.protect.shield.AppAudit
import com.norypt.protect.shield.AppFacts
import com.norypt.protect.shield.AuditEntry
import com.norypt.protect.shield.AuditRules
import com.norypt.protect.ui.components.NoryptCard
import com.norypt.protect.ui.components.NoteCard
import com.norypt.protect.ui.components.SecondaryButton
import com.norypt.protect.ui.components.SubScreenScaffold
import com.norypt.protect.ui.components.ToggleCard
import com.norypt.protect.ui.theme.NoryptColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Apps with powerful access, each with a way to the system page where it can be taken away. */
@Composable
fun AppAuditSubScreen(onBack: () -> Unit, padding: PaddingValues) {
    val ctx = LocalContext.current
    var facts by remember { mutableStateOf<List<AppFacts>?>(null) }
    var showSystem by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val listenersReadable = remember { AppAudit.notificationListeners(ctx) != null }
    // Hundreds of package queries: off the main thread.
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { runCatching { AppAudit.collect(ctx) } }
        facts = result.getOrNull()
        failed = result.isFailure
    }
    SubScreenScaffold(title = "App audit", onBack = onBack, padding = padding) {
        NoteCard(
            text = "Apps with the kinds of access spyware uses. This finds risky access, not mercenary spyware " +
                "such as Pegasus, which hides inside the system and needs a forensic examination; Norypt " +
                "offers a phone analysis for that.",
            color = NoryptColors.Amber,
        )
        ToggleCard(
            title = "Show system apps",
            subtitle = "Apps that came with the phone.",
            checked = showSystem,
            enabled = true,
            onToggle = { showSystem = it },
        )
        if (!listenersReadable) {
            SecondaryButton(
                label = "Check notification access in Settings",
                onClick = { startPage(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                color = NoryptColors.Muted,
            )
        }
        val current = facts
        if (failed) {
            Text("Could not read the installed apps. Try again later.", color = NoryptColors.Red, fontSize = 13.sp)
        } else if (current == null) {
            Text("Reading installed apps…", color = NoryptColors.Muted, fontSize = 13.sp)
        } else {
            val entries = AuditRules.entries(current, showSystem, ctx.packageName)
            if (entries.isEmpty()) Text("Nothing to review.", color = NoryptColors.Muted, fontSize = 13.sp)
            entries.forEach { AuditCard(ctx, it) }
        }
    }
}

@Composable
private fun AuditCard(ctx: Context, entry: AuditEntry) {
    NoryptCard {
        Text(entry.label, color = NoryptColors.TextStrong, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(entry.packageName, color = NoryptColors.MutedDeep, fontSize = 11.sp)
        entry.reasons.forEach { Text("• $it", color = NoryptColors.Muted, fontSize = 12.sp) }
        SecondaryButton(
            label = "App info",
            onClick = {
                val uri = Uri.fromParts("package", entry.packageName, null)
                startPage(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
            },
            color = NoryptColors.Muted,
        )
    }
}

private fun startPage(ctx: Context, intent: Intent) {
    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
