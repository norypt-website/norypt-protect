package com.norypt.protect.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.norypt.protect.admin.Provisioning
import com.norypt.protect.admin.Tier
import com.norypt.protect.shield.AccessShield
import com.norypt.protect.shield.ShieldKind
import com.norypt.protect.ui.components.NoryptCard
import com.norypt.protect.ui.components.NoteCard
import com.norypt.protect.ui.components.SecondaryButton
import com.norypt.protect.ui.components.SectionLabel
import com.norypt.protect.ui.components.SubScreenScaffold
import com.norypt.protect.ui.components.ToggleCard
import com.norypt.protect.ui.theme.NoryptColors

private const val REFUSED = "Android refused the change."

/** Spyware shield: approved-only accessibility services and keyboards, and the way to the app audit. */
@Composable
fun ShieldSubScreen(onBack: () -> Unit, onOpenAudit: () -> Unit, padding: PaddingValues) {
    val ctx = LocalContext.current
    val isOwner = Provisioning.current(ctx) == Tier.DeviceOwner
    // Bumped after every change, so each section re-reads the stored and the platform state.
    var version by remember { mutableIntStateOf(0) }
    SubScreenScaffold(title = "Spyware shield", onBack = onBack, padding = padding) {
        NoteCard(
            text = "Stalkerware and keyloggers work through accessibility services and keyboards. With a " +
                "switch on, only system ones and the ones you approve can be switched on. To add one later, " +
                "turn the switch off, switch it on in Settings, then turn the switch on again and approve it.",
            color = NoryptColors.Accent,
        )
        ShieldKind.entries.forEach { kind -> ShieldSection(kind, isOwner, version) { version++ } }
        SectionLabel("Apps with powerful access")
        SecondaryButton(label = "Open App audit", onClick = onOpenAudit)
    }
}

@Composable
private fun ShieldSection(kind: ShieldKind, isOwner: Boolean, version: Int, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val on = remember(version) { AccessShield.isOn(ctx, kind) }
    val inForce = remember(version) { AccessShield.inForce(ctx, kind) }
    val approved = remember(version) { AccessShield.approved(ctx, kind) }
    var waiting by remember { mutableStateOf<Set<String>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val names = if (kind == ShieldKind.ACCESSIBILITY) "accessibility services" else "keyboards"
    SectionLabel(names)
    ToggleCard(
        title = "Allow only approved $names",
        subtitle = if (isOwner) "System ones are always allowed." else "Requires Device Owner.",
        checked = on,
        enabled = isOwner,
        requiresDeviceOwner = true,
        onToggle = { wanted ->
            message = toggle(ctx, kind, wanted) { waiting = it }
            onChanged()
        },
    )
    if (on && !inForce) {
        NoteCard(
            text = "Android is not applying this list right now. Turn the switch off and on to review what is switched on.",
            color = NoryptColors.Red,
        )
    }
    approved.sorted().forEach { pkg ->
        ApprovedRow(appLabel(ctx, pkg), pkg, active = on) {
            val removed = AccessShield.removeApproval(ctx, kind, pkg)
            message = if (removed) null else "${appLabel(ctx, pkg)} is still switched on. Turn it off in Settings first."
            onChanged()
        }
    }
    message?.let { NoteCard(text = it, color = NoryptColors.Red) }
    waiting?.let { outside ->
        ApproveDialog(
            ctx = ctx,
            kind = kind,
            outside = outside,
            onApprove = {
                waiting = null
                message = if (AccessShield.enable(ctx, kind, approved + outside)) null else REFUSED
                onChanged()
            },
            onDismiss = { waiting = null },
        )
    }
}

/** Applies the switch and returns what to tell the owner. Outside ones switched on need approval first. */
private fun toggle(ctx: Context, kind: ShieldKind, wanted: Boolean, askApproval: (Set<String>) -> Unit): String? {
    if (!wanted) return if (AccessShield.disable(ctx, kind)) null else REFUSED
    val approved = AccessShield.approved(ctx, kind)
    val waiting = AccessShield.unapproved(AccessShield.enabledOutside(ctx, kind), approved)
    if (waiting.isNotEmpty()) {
        askApproval(waiting)
        return null
    }
    return if (AccessShield.enable(ctx, kind, approved)) null else REFUSED
}

@Composable
private fun ApprovedRow(label: String, pkg: String, active: Boolean, onRemove: () -> Unit) {
    NoryptCard {
        val title = if (active) "Approved: $label" else "Approved for when the switch is on: $label"
        Text(title, color = NoryptColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(pkg, color = NoryptColors.MutedDeep, fontSize = 11.sp)
        SecondaryButton(label = "Remove approval", onClick = onRemove, color = NoryptColors.Muted)
    }
}

@Composable
private fun ApproveDialog(ctx: Context, kind: ShieldKind, outside: Set<String>, onApprove: () -> Unit, onDismiss: () -> Unit) {
    val list = outside.sorted().joinToString("\n") { "• ${appLabel(ctx, it)} ($it)" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Switched on now", color = NoryptColors.TextStrong) },
        text = {
            Text(
                "$list\n\nApprove them to keep them, or turn them off in Settings and come back.",
                color = NoryptColors.Muted,
                fontSize = 13.sp,
            )
        },
        confirmButton = { TextButton(onClick = onApprove) { Text("Approve and turn on", color = NoryptColors.Accent) } },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                openSettings(ctx, kind)
            }) { Text("Open Settings", color = NoryptColors.Muted) }
        },
        containerColor = NoryptColors.Surface2,
    )
}

private fun openSettings(ctx: Context, kind: ShieldKind) {
    val action = if (kind == ShieldKind.ACCESSIBILITY) Settings.ACTION_ACCESSIBILITY_SETTINGS else Settings.ACTION_INPUT_METHOD_SETTINGS
    runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun appLabel(ctx: Context, pkg: String): String = runCatching {
    val pm = ctx.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString()
}.getOrDefault(pkg)
