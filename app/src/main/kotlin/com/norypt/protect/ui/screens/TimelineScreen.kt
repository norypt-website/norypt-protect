package com.norypt.protect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.norypt.protect.admin.Provisioning
import com.norypt.protect.admin.Tier
import com.norypt.protect.platform.PlatformInfo
import com.norypt.protect.security.AppPin
import com.norypt.protect.timeline.Severity
import com.norypt.protect.timeline.TamperEvent
import com.norypt.protect.timeline.TamperLog
import com.norypt.protect.timeline.TimelineFormat
import com.norypt.protect.ui.components.NoryptCard
import com.norypt.protect.ui.components.NoteCard
import com.norypt.protect.ui.components.PinEntryDialog
import com.norypt.protect.ui.components.ScreenHeader
import com.norypt.protect.ui.components.SecondaryButton
import com.norypt.protect.ui.components.TagPill
import com.norypt.protect.ui.components.ToggleCard
import com.norypt.protect.ui.theme.NoryptColors

/**
 * The tamper timeline: what happened to this phone while it was out of the owner's hands.
 * Recording is opt-in; the screen says plainly what the timeline can and cannot see.
 */
@Composable
fun TimelineScreen(padding: PaddingValues) {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(TamperLog.isEnabled(ctx)) }
    var events by remember { mutableStateOf(TamperLog.all(ctx)) }
    var notableOnly by remember { mutableStateOf(false) }
    var showClearPin by remember { mutableStateOf(false) }
    var tier by remember { mutableStateOf(Provisioning.current(ctx)) }
    val isGraphene = remember { PlatformInfo.isGrapheneOS(ctx) }

    // Events arrive from receivers and the service tick, so re-read whenever the screen returns.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                tier = Provisioning.current(ctx)
                enabled = TamperLog.isEnabled(ctx)
                events = TamperLog.all(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val shown = if (notableOnly) events.filter { it.severity != Severity.Info } else events

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(NoryptColors.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        headerItems(
            enabled = enabled,
            tier = tier,
            isGraphene = isGraphene,
            onToggle = { on ->
                TamperLog.setEnabled(ctx, on)
                enabled = TamperLog.isEnabled(ctx)
                events = TamperLog.all(ctx)
            },
        )
        item {
            FilterRow(
                total = events.size,
                alerts = events.count { it.severity == Severity.Alert },
                notableOnly = notableOnly,
                onToggle = { notableOnly = !notableOnly },
            )
        }
        eventItems(shown, enabled)
        item {
            Spacer(Modifier.height(4.dp))
            SecondaryButton(
                label = "Clear timeline (App PIN)",
                onClick = { showClearPin = true },
                enabled = events.isNotEmpty(),
                color = NoryptColors.Red,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClearPin) {
        PinEntryDialog(
            title = "Enter App PIN to clear the timeline",
            onConfirm = { pin ->
                if (AppPin.verify(ctx, pin)) {
                    showClearPin = false
                    TamperLog.clear(ctx)
                    events = TamperLog.all(ctx)
                }
            },
            onDismiss = { showClearPin = false },
        )
    }
}

private fun LazyListScope.headerItems(
    enabled: Boolean,
    tier: Tier,
    isGraphene: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    item {
        ScreenHeader(
            title = "Timeline",
            subtitle = "What happened to this phone while it was out of your hands.",
            trailing = {
                if (enabled) TagPill("RECORDING", NoryptColors.Green) else TagPill("OFF", NoryptColors.MutedDeep)
            },
        )
    }
    item {
        ToggleCard(
            title = "Record timeline",
            subtitle = if (enabled) {
                "Recording. Boots, unlocks, failed unlocks, USB, SIM, biometric and credential changes " +
                    "are written to an encrypted log that never leaves this device."
            } else {
                "Off. Nothing is recorded until you turn this on. Turn it on before handing the phone " +
                    "over or leaving it behind."
            },
            checked = enabled,
            enabled = true,
            accent = NoryptColors.Green,
            onToggle = onToggle,
        )
    }
    item { ScopeCard(isGraphene) }
    when (tier) {
        Tier.None -> item {
            NoteCard(
                "Monitoring runs only while Norypt Protect is a device admin. Enable it from the Home tab.",
                NoryptColors.Amber,
            )
        }
        Tier.DeviceAdmin -> item {
            NoteCard(
                "On Device Admin tier, failed unlocks are detected by polling about every 30 seconds " +
                    "while monitoring runs. Device Owner receives the exact callback.",
                NoryptColors.Muted,
            )
        }
        Tier.DeviceOwner -> Unit
    }
}

private fun LazyListScope.eventItems(shown: List<TamperEvent>, enabled: Boolean) {
    if (shown.isEmpty()) {
        item {
            Text(
                if (enabled) "Nothing recorded yet." else "No events.",
                color = NoryptColors.Muted,
                fontSize = 13.sp,
            )
        }
        return
    }
    shown.groupBy { TimelineFormat.date(it.epochMs) }.forEach { (day, list) ->
        item {
            Text(
                day,
                color = NoryptColors.MutedDeep,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        items(list) { EventRow(it) }
    }
}

@Composable
private fun FilterRow(total: Int, alerts: Int, notableOnly: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$total events · $alerts alerts",
            color = NoryptColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = notableOnly,
            onClick = onToggle,
            label = { Text("Notable only", fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = NoryptColors.AccentDim,
                selectedLabelColor = NoryptColors.Accent,
                labelColor = NoryptColors.Muted,
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = notableOnly,
                borderColor = NoryptColors.BorderStrong,
                selectedBorderColor = NoryptColors.Accent.copy(alpha = 0.5f),
            ),
        )
    }
}

@Composable
private fun ScopeCard(isGraphene: Boolean) {
    NoryptCard(tint = NoryptColors.Amber, contentPadding = PaddingValues(14.dp)) {
        Text(
            "WHAT THIS CAN AND CANNOT SEE",
            color = NoryptColors.Amber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "It sees only what Android reports to an app: boots, unlocks and failed unlocks, USB " +
                "connections, SIM changes, fingerprint or face enrollment changes, screen-lock " +
                "changes, USB debugging, and clock changes. Shutdowns cannot be observed on modern " +
                "Android, so each boot entry says when the phone was last seen running instead.",
            color = NoryptColors.Text,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "It cannot see anything done at bootloader or firmware level, a modified operating " +
                "system, or a hardware attack such as chip-off. A phone imaged through a bootloader " +
                "exploit and put back shows nothing here.",
            color = NoryptColors.Text,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "For that, use hardware attestation: GrapheneOS Auditor verifies this phone's OS and " +
                "firmware from a second device" +
                if (isGraphene) ", and is preinstalled on GrapheneOS." else "; it also works on many stock devices.",
            color = NoryptColors.Text,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Entries are stored encrypted on this device, never leave it, and are cleared with your " +
                "App PIN. Someone with root access could alter them.",
            color = NoryptColors.Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun EventRow(e: TamperEvent) {
    val dot = when (e.severity) {
        Severity.Alert -> NoryptColors.Red
        Severity.Notable -> NoryptColors.Amber
        Severity.Info -> NoryptColors.MutedDeep
    }
    NoryptCard(
        accent = if (e.severity == Severity.Alert) NoryptColors.Red else null,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .size(10.dp)
                    .background(dot, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        e.kind.label,
                        color = NoryptColors.Text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(TimelineFormat.time(e.epochMs), color = NoryptColors.Muted, fontSize = 11.sp)
                }
                if (e.detail.isNotBlank()) {
                    Text(e.detail, color = NoryptColors.Muted, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}
