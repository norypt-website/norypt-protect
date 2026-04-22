package com.norypt.protect.ui.screens

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.admin.Provisioning
import com.norypt.protect.admin.Tier
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.triggers.Trigger
import com.norypt.protect.triggers.TriggerRegistry
import com.norypt.protect.ui.theme.NoryptColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggersScreen(padding: PaddingValues) {
    val ctx = LocalContext.current
    var configuring: Trigger? by remember { mutableStateOf(null) }

    // Track enabled state per trigger so the Switch animates without re-reading prefs each tick.
    val enabledMap = remember {
        mutableStateMapOf<String, Boolean>().apply {
            TriggerRegistry.all.forEach { put(it.id, ProtectPrefs.isTriggerEnabled(ctx, it.id)) }
        }
    }
    val currentTier = remember { Provisioning.current(ctx) }

    Column(
        Modifier
            .fillMaxSize()
            .background(NoryptColors.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "TRIGGERS",
            color = NoryptColors.MutedDeep,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        TriggerRegistry.all.forEach { trigger ->
            TriggerRow(
                trigger = trigger,
                enabled = enabledMap[trigger.id] == true,
                tierMet = trigger.requiredTier <= currentTier,
                onToggle = { newValue ->
                    enabledMap[trigger.id] = newValue
                    if (newValue) trigger.arm(ctx) else trigger.disarm(ctx)
                },
                onConfigure = { configuring = trigger },
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    val cur = configuring
    if (cur != null) {
        ModalBottomSheet(
            onDismissRequest = { configuring = null },
            containerColor = NoryptColors.Surface1,
        ) {
            ConfigSheet(trigger = cur, onDone = { configuring = null })
        }
    }
}

@Composable
private fun TriggerRow(
    trigger: Trigger,
    enabled: Boolean,
    tierMet: Boolean,
    onToggle: (Boolean) -> Unit,
    onConfigure: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NoryptColors.Surface2)
            .border(1.dp, NoryptColors.Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onConfigure)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    trigger.label,
                    color = NoryptColors.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    trigger.id,
                    color = NoryptColors.MutedDeep,
                    fontSize = 11.sp,
                )
                if (!tierMet) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NoryptColors.Amber.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "DEVICE OWNER",
                            color = NoryptColors.Amber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(trigger.description, color = NoryptColors.Muted, fontSize = 12.sp)
        }
        Switch(
            checked = enabled,
            enabled = tierMet,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NoryptColors.Bg,
                checkedTrackColor = NoryptColors.Accent,
                uncheckedThumbColor = NoryptColors.Muted,
                uncheckedTrackColor = NoryptColors.Surface1,
                uncheckedBorderColor = NoryptColors.Border,
                disabledCheckedThumbColor = NoryptColors.MutedDeep,
                disabledCheckedTrackColor = NoryptColors.AccentDim,
                disabledUncheckedThumbColor = NoryptColors.MutedDeep,
                disabledUncheckedTrackColor = NoryptColors.Surface1,
                disabledUncheckedBorderColor = NoryptColors.Border,
            ),
        )
    }
}

@Composable
private fun ConfigSheet(trigger: Trigger, onDone: () -> Unit) {
    val ctx = LocalContext.current

    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            trigger.label,
            color = NoryptColors.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(trigger.description, color = NoryptColors.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))

        when (trigger.id) {
            "A6" -> {
                var code by remember { mutableStateOf(ProtectPrefs.smsSecretCode(ctx).orEmpty()) }
                ConfigTextField(
                    label = "Secret SMS code",
                    value = code,
                    onChange = {
                        code = it
                        ProtectPrefs.setSmsSecretCode(ctx, it.ifEmpty { null })
                    },
                )
                Spacer(Modifier.height(12.dp))
                PermissionToggleRow(
                    label = "SMS permission (RECEIVE_SMS)",
                    permission = Manifest.permission.RECEIVE_SMS,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "On Device Owner tier the permission is auto-granted with no prompt. On Device Admin tier the toggle launches the standard system dialog; revoke from system Settings if needed.",
                    color = NoryptColors.MutedDeep,
                    fontSize = 11.sp,
                )
            }
            "A8" -> {
                var minutes by remember { mutableStateOf(ProtectPrefs.maxUnlockedMinutes(ctx).toString()) }
                ConfigNumberField(
                    label = "Max unlocked minutes",
                    value = minutes,
                    onChange = {
                        minutes = it
                        it.toIntOrNull()?.let(ProtectPrefs::setMaxUnlockedMinutes.curry(ctx))
                    },
                )
            }
            "A10" -> {
                var pkg by remember { mutableStateOf(ProtectPrefs.fakeMessengerPackage(ctx).orEmpty()) }
                ConfigTextField(
                    label = "Trap app package name",
                    value = pkg,
                    onChange = {
                        pkg = it
                        ProtectPrefs.setFakeMessengerPackage(ctx, it.ifEmpty { null })
                    },
                )
                Spacer(Modifier.height(12.dp))
                UsageAccessToggleRow()
                Spacer(Modifier.height(6.dp))
                Text(
                    "Usage Access is a Special-Access permission — Android requires the user to flip it in system Settings (no API can auto-grant, even for Device Owner). The toggle deep-links there and reflects the current state when you return.",
                    color = NoryptColors.MutedDeep,
                    fontSize = 11.sp,
                )
            }
            "B1" -> {
                var attempts by remember { mutableStateOf(ProtectPrefs.maxFailedAttempts(ctx).toString()) }
                ConfigNumberField(
                    label = "Max failed unlock attempts (default 10)",
                    value = attempts,
                    onChange = {
                        attempts = it
                        it.toIntOrNull()?.let(ProtectPrefs::setMaxFailedAttempts.curry(ctx))
                    },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Wipe fires after this many failed system unlock attempts. Set to 3 for paranoia, 10 for normal use.",
                    color = NoryptColors.MutedDeep,
                    fontSize = 11.sp,
                )
            }
            "A11" -> {
                var duress by remember { mutableStateOf(ProtectPrefs.duressThreshold(ctx).toString()) }
                ConfigNumberField(
                    label = "Duress fast-wipe threshold (0 = off)",
                    value = duress,
                    onChange = {
                        duress = it
                        it.toIntOrNull()?.let(ProtectPrefs::setDuressThreshold.curry(ctx))
                    },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Stricter than B1: wipe at this exact failed-attempt count. Use a low number (e.g. 3) so coercion is detected before the standard threshold. Must be ≤ B1.",
                    color = NoryptColors.MutedDeep,
                    fontSize = 11.sp,
                )
            }
            "C4" -> {
                var pct by remember { mutableStateOf(ProtectPrefs.deadmanBatteryPct(ctx).toString()) }
                var grace by remember { mutableStateOf(ProtectPrefs.deadmanGraceSeconds(ctx).toString()) }
                var disarm by remember { mutableStateOf(ProtectPrefs.deadmanDisarmMinutesAfterUnlock(ctx).toString()) }
                var requireBt by remember { mutableStateOf(ProtectPrefs.deadmanRequireBt(ctx)) }
                var requireGsm by remember { mutableStateOf(ProtectPrefs.deadmanRequireGsm(ctx)) }
                var requireWifi by remember { mutableStateOf(ProtectPrefs.deadmanRequireWifi(ctx)) }

                ConfigNumberField(
                    label = "Battery threshold % (default 5)",
                    value = pct,
                    onChange = {
                        pct = it
                        it.toIntOrNull()?.let(ProtectPrefs::setDeadmanBatteryPct.curry(ctx))
                    },
                )
                Spacer(Modifier.height(8.dp))
                ConfigNumberField(
                    label = "Countdown seconds before wipe (default 60)",
                    value = grace,
                    onChange = {
                        grace = it
                        it.toIntOrNull()?.let(ProtectPrefs::setDeadmanGraceSeconds.curry(ctx))
                    },
                )
                Spacer(Modifier.height(8.dp))
                ConfigNumberField(
                    label = "Disarm minutes after unlock (default 0)",
                    value = disarm,
                    onChange = {
                        disarm = it
                        it.toIntOrNull()?.let(ProtectPrefs::setDeadmanDisarmMinutesAfterUnlock.curry(ctx))
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Trigger fires only if ALL enabled connectivity checks below are simultaneously DOWN.",
                    color = NoryptColors.Muted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                ToggleConfigRow("Require Bluetooth check", requireBt) {
                    requireBt = it
                    ProtectPrefs.setDeadmanRequireBt(ctx, it)
                }
                ToggleConfigRow("Require cellular check", requireGsm) {
                    requireGsm = it
                    ProtectPrefs.setDeadmanRequireGsm(ctx, it)
                }
                ToggleConfigRow("Require Wi-Fi check", requireWifi) {
                    requireWifi = it
                    ProtectPrefs.setDeadmanRequireWifi(ctx, it)
                }
            }
            "A7" -> {
                InfoBlock(
                    title = "What this is",
                    body = "A signed entry point so a trusted companion app on this same phone can fire the wipe " +
                        "remotely (e.g. a smart-watch tile, a Tasker shortcut, or another Norypt app).",
                )
                Spacer(Modifier.height(8.dp))
                InfoBlock(
                    title = "How to fire it",
                    body = "From any app holding the signature permission " +
                        "com.norypt.protect.permission.TRIGGER, broadcast the action " +
                        "com.norypt.protect.action.TRIGGER. With dry-run ON, you can test " +
                        "from a computer over ADB without wiping:",
                )
                Spacer(Modifier.height(6.dp))
                CodeBlock(
                    "adb shell am broadcast -a com.norypt.protect.action.TRIGGER " +
                        "-n ${ctx.packageName}/com.norypt.protect.triggers.ExternalTriggerReceiver"
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Because the receiver is gated by a signature-level permission, only apps signed with the " +
                        "Norypt Protect release key can fire it. ADB is the only exception; it bypasses the " +
                        "permission for the lifetime of the cable.",
                    color = NoryptColors.MutedDeep,
                    fontSize = 11.sp,
                )
            }
            "A5" -> {
                InfoBlock(
                    title = "What this is",
                    body = "Implements the cross-app emergency-panic broadcast standard. Other emergency apps " +
                        "(panic-button apps, smart watches, NFC tags, hardware kill-switch dongles) that " +
                        "implement the same standard can fire Norypt Protect's wipe without any further config.",
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Action: info.guardianproject.panic.action.TRIGGER. The receiver checks this trigger's " +
                        "switch — if OFF, the broadcast is ignored.",
                    color = NoryptColors.MutedDeep,
                    fontSize = 11.sp,
                )
            }
            "B5" -> {
                InfoBlock(
                    title = "What this is",
                    body = "Watches every installed app. Posts a notification if any app silently gains the " +
                        "INTERNET permission after an update — a common stealth-tracking pattern. No wipe.",
                )
            }
            "B6" -> {
                InfoBlock(
                    title = "What this is",
                    body = "Registers a Notification Listener so Norypt Protect can react to lock/package events. " +
                        "Granting Notification Access in system Settings is required.",
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NoryptColors.Accent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NoryptColors.Border),
                ) { Text("Open Notification Access settings") }
            }
            "A12" -> {
                InfoBlock(
                    title = "What this is",
                    body = "When Norypt Protect is installed inside an Android Work Profile, this trigger limits " +
                        "wipe to the work profile only — personal data is preserved. Has no effect outside a " +
                        "work profile.",
                )
            }
            "C3" -> {
                InfoBlock(
                    title = "What this is",
                    body = "Press the physical power button 5 times within 3 seconds (any mix of screen-on and " +
                        "screen-off events). This bypasses the lockscreen — the wipe fires whether the phone " +
                        "is unlocked, locked, or asleep.",
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Important: Android's built-in Emergency SOS uses the same gesture by default and steals " +
                        "the events before they reach Norypt Protect. Use the Protect tab → \"Auto-disable " +
                        "Emergency SOS\" toggle to free the gesture for our wipe.",
                    color = NoryptColors.MutedDeep,
                    fontSize = 11.sp,
                )
            }
            else -> {
                Text("No additional settings.", color = NoryptColors.Muted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NoryptColors.Accent,
                contentColor = androidx.compose.ui.graphics.Color.White,
            ),
            shape = RoundedCornerShape(8.dp),
        ) { Text("Done") }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ConfigTextField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NoryptColors.Accent,
            unfocusedBorderColor = NoryptColors.Border,
            focusedTextColor = NoryptColors.Text,
            unfocusedTextColor = NoryptColors.Text,
            focusedLabelColor = NoryptColors.Accent,
            unfocusedLabelColor = NoryptColors.Muted,
            cursorColor = NoryptColors.Accent,
        ),
    )
}

@Composable
private fun ConfigNumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.all(Char::isDigit)) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NoryptColors.Accent,
            unfocusedBorderColor = NoryptColors.Border,
            focusedTextColor = NoryptColors.Text,
            unfocusedTextColor = NoryptColors.Text,
            focusedLabelColor = NoryptColors.Accent,
            unfocusedLabelColor = NoryptColors.Muted,
            cursorColor = NoryptColors.Accent,
        ),
    )
}

// Tiny curry helper so trigger configs can write back to ProtectPrefs concisely.
private fun ((android.content.Context, Int) -> Unit).curry(ctx: android.content.Context): (Int) -> Unit =
    { v -> this(ctx, v) }

@Composable
private fun InfoBlock(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(NoryptColors.Surface1)
            .padding(12.dp),
    ) {
        Text(title.uppercase(), color = NoryptColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = NoryptColors.Text, fontSize = 12.sp)
    }
}

@Composable
private fun CodeBlock(text: String) {
    val cm = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    Column(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .background(NoryptColors.Bg)
            .padding(10.dp),
    ) {
        Text(
            text,
            color = NoryptColors.Text,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("norypt", text))
            },
            modifier = Modifier.fillMaxWidth().height(36.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NoryptColors.Accent),
            border = androidx.compose.foundation.BorderStroke(1.dp, NoryptColors.Border),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
        ) { Text("Copy", fontSize = 12.sp) }
    }
}

/**
 * Toggle that grants/revokes a runtime permission.
 *
 * Behavior depends on tier:
 * - Device Owner → flipping ON calls dpm.setPermissionGrantState(GRANTED), no dialog.
 *   Flipping OFF calls dpm.setPermissionGrantState(DENIED).
 * - Device Admin / None → flipping ON launches the standard system permission dialog.
 *   Flipping OFF opens app-details settings (Android does not allow apps to self-revoke
 *   without DO).
 */
@Composable
private fun PermissionToggleRow(label: String, permission: String) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
    }
    val tier = remember { Provisioning.current(ctx) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = NoryptColors.Text, fontSize = 13.sp)
            Text(
                if (granted) "Granted" else "Not granted",
                color = if (granted) NoryptColors.Green else NoryptColors.MutedDeep,
                fontSize = 11.sp,
            )
        }
        Switch(
            checked = granted,
            onCheckedChange = { wantOn ->
                if (wantOn && !granted) {
                    if (tier == Tier.DeviceOwner) {
                        if (setPermissionViaDpm(ctx, permission, grant = true)) granted = true
                        else launcher.launch(permission)
                    } else {
                        launcher.launch(permission)
                    }
                } else if (!wantOn && granted) {
                    if (tier == Tier.DeviceOwner) {
                        if (setPermissionViaDpm(ctx, permission, grant = false)) granted = false
                    } else {
                        // Without DO we cannot self-revoke; deep-link to app settings.
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${ctx.packageName}"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                    }
                }
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = NoryptColors.Bg,
                checkedTrackColor = NoryptColors.Accent,
                uncheckedThumbColor = NoryptColors.Muted,
                uncheckedTrackColor = NoryptColors.Surface1,
                uncheckedBorderColor = NoryptColors.Border,
            ),
        )
    }
}

private fun setPermissionViaDpm(ctx: Context, permission: String, grant: Boolean): Boolean = runCatching {
    val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admin = ComponentName(ctx, ProtectAdminReceiver::class.java)
    val state = if (grant) DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                else DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
    dpm.setPermissionGrantState(admin, ctx.packageName, permission, state)
}.getOrDefault(false)

/**
 * Special-Access toggle for PACKAGE_USAGE_STATS (used by the fake-messenger trap).
 * Cannot be granted programmatically by any app — even Device Owner — so the
 * toggle deep-links to system Settings and re-reads the AppOps state on resume.
 */
@Composable
private fun UsageAccessToggleRow() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasUsageAccess(ctx)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = hasUsageAccess(ctx)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Usage Access (PACKAGE_USAGE_STATS)", color = NoryptColors.Text, fontSize = 13.sp)
            Text(
                if (granted) "Granted" else "Not granted",
                color = if (granted) NoryptColors.Green else NoryptColors.MutedDeep,
                fontSize = 11.sp,
            )
        }
        Switch(
            checked = granted,
            onCheckedChange = {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = NoryptColors.Bg,
                checkedTrackColor = NoryptColors.Accent,
                uncheckedThumbColor = NoryptColors.Muted,
                uncheckedTrackColor = NoryptColors.Surface1,
                uncheckedBorderColor = NoryptColors.Border,
            ),
        )
    }
}

private fun hasUsageAccess(ctx: Context): Boolean {
    val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = ops.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        ctx.packageName,
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

@Composable
private fun ToggleConfigRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = NoryptColors.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NoryptColors.Bg,
                checkedTrackColor = NoryptColors.Accent,
                uncheckedThumbColor = NoryptColors.Muted,
                uncheckedTrackColor = NoryptColors.Surface1,
                uncheckedBorderColor = NoryptColors.Border,
            ),
        )
    }
}
