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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.platform.PlatformInfo
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.triggers.DeadmanScheduler
import com.norypt.protect.triggers.SmsSecretReceiver
import com.norypt.protect.triggers.Trigger
import com.norypt.protect.triggers.TriggerRegistry
import com.norypt.protect.ui.components.NoryptCard
import com.norypt.protect.ui.components.NoteCard
import com.norypt.protect.ui.components.ScreenHeader
import com.norypt.protect.ui.components.TagPill
import com.norypt.protect.ui.components.noryptFieldColors
import com.norypt.protect.ui.components.noryptSwitchColors
import com.norypt.protect.ui.theme.NoryptColors
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.onFocusChanged
import com.norypt.protect.prefs.SettingBounds
import com.norypt.protect.util.GrapheneDetect
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation

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
    // Why an armed trigger cannot fire, re-read whenever its inputs may have changed.
    val problems = remember { mutableStateMapOf<String, String>() }
    fun refreshProblems() {
        TriggerRegistry.all.forEach { trigger ->
            val problem = trigger.problem(ctx)
            if (problem == null) problems.remove(trigger.id) else problems[trigger.id] = problem
        }
    }
    LaunchedEffect(Unit) { refreshProblems() }
    var currentTier by remember { mutableStateOf(Provisioning.current(ctx)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentTier = Provisioning.current(ctx)
                // Also re-read trigger enabled states in case they were changed
                // elsewhere (e.g. by a DO restriction policy).
                TriggerRegistry.all.forEach {
                    enabledMap[it.id] = ProtectPrefs.isTriggerEnabled(ctx, it.id)
                }
                refreshProblems()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
        ScreenHeader(
            title = "Triggers",
            subtitle = "Each trigger arms on its own. Tap a card for its settings.",
        )
        TriggerRegistry.all.forEach { trigger ->
            TriggerRow(
                trigger = trigger,
                enabled = enabledMap[trigger.id] == true,
                tierMet = trigger.requiredTier <= currentTier,
                problem = problems[trigger.id],
                onToggle = { newValue ->
                    if (newValue) trigger.arm(ctx) else trigger.disarm(ctx)
                    // Read back rather than trusting the switch.
                    enabledMap[trigger.id] = ProtectPrefs.isTriggerEnabled(ctx, trigger.id)
                    refreshProblems()
                },
                onConfigure = { configuring = trigger },
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    val cur = configuring
    if (cur != null) {
        ModalBottomSheet(
            onDismissRequest = {
                configuring = null
                refreshProblems()
            },
            containerColor = NoryptColors.Surface1,
        ) {
            ConfigSheet(trigger = cur, onDone = {
                configuring = null
                refreshProblems()
            })
        }
    }
}

@Composable
private fun TriggerRow(
    trigger: Trigger,
    enabled: Boolean,
    tierMet: Boolean,
    problem: String?,
    onToggle: (Boolean) -> Unit,
    onConfigure: () -> Unit,
) {
    NoryptCard(
        accent = if (enabled && tierMet) NoryptColors.Accent else null,
        onClick = onConfigure,
        contentPadding = PaddingValues(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        trigger.label,
                        color = NoryptColors.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        trigger.id,
                        color = NoryptColors.MutedDeep,
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        maxLines = 1,
                    )
                    if (trigger.requiredTier == Tier.DeviceOwner) {
                        TagPill("DEVICE OWNER", if (tierMet) NoryptColors.MutedDeep else NoryptColors.Amber)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    trigger.description,
                    color = NoryptColors.Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
                val grapheneNote = trigger.grapheneOsNote
                if (grapheneNote != null && PlatformInfo.isGrapheneOS(LocalContext.current)) {
                    Spacer(Modifier.height(8.dp))
                    NoteCard(text = "GrapheneOS: $grapheneNote", color = NoryptColors.Amber)
                }
                if (enabled && tierMet && problem != null) {
                    Spacer(Modifier.height(8.dp))
                    NoteCard(text = "Armed, but it cannot fire: $problem Tap to fix.", color = NoryptColors.Red)
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = enabled,
                enabled = tierMet,
                onCheckedChange = onToggle,
                colors = noryptSwitchColors(),
            )
        }
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
        val grapheneNote = trigger.grapheneOsNote
        if (grapheneNote != null && PlatformInfo.isGrapheneOS(ctx)) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(NoryptColors.Amber.copy(alpha = 0.10f))
                    .border(1.dp, NoryptColors.Amber.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    "GrapheneOS: $grapheneNote",
                    color = NoryptColors.Amber,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        when (trigger.id) {
            "A6" -> SmsTriggerConfig()
            "A8" -> {
                ConfigNumberField(
                    label = "Max unlocked minutes",
                    current = ProtectPrefs.maxUnlockedMinutes(ctx),
                    range = SettingBounds.UNLOCKED_MINUTES,
                    bound = SettingBounds::unlockedMinutes,
                    onCommit = { ProtectPrefs.setMaxUnlockedMinutes(ctx, it) },
                )
                Spacer(Modifier.height(8.dp))
                ConfigNumberField(
                    label = "Countdown seconds before wipe (default 60)",
                    current = ProtectPrefs.unlockedTimerGraceSeconds(ctx),
                    range = SettingBounds.GRACE_SECONDS,
                    bound = SettingBounds::graceSeconds,
                    onCommit = { ProtectPrefs.setUnlockedTimerGraceSeconds(ctx, it) },
                )
            }
            "A10" -> {
                var pkg by remember { mutableStateOf(ProtectPrefs.fakeMessengerPackage(ctx).orEmpty()) }
                ConfigTextField(
                    label = "Trap app package name",
                    value = pkg,
                    onChange = {
                        pkg = it
                        // Trimmed: a keyboard's trailing space would otherwise never match a package.
                        ProtectPrefs.setFakeMessengerPackage(ctx, it.trim().ifEmpty { null })
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
                ConfigNumberField(
                    label = "Max failed unlock attempts (default 10)",
                    current = ProtectPrefs.maxFailedAttempts(ctx),
                    range = SettingBounds.FAILED_ATTEMPTS,
                    bound = SettingBounds::failedAttempts,
                    onCommit = { ProtectPrefs.setMaxFailedAttempts(ctx, it) },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Wipe fires after this many failed system unlock attempts. Set to 3 for paranoia, 10 for normal use.",
                    color = NoryptColors.MutedDeep,
                    fontSize = 11.sp,
                )
            }
            "A11" -> {
                ConfigNumberField(
                    label = "Duress fast-wipe threshold (0 = off)",
                    current = ProtectPrefs.duressThreshold(ctx),
                    range = 0..SettingBounds.DURESS_ATTEMPTS.last,
                    bound = SettingBounds::duressThreshold,
                    onCommit = { ProtectPrefs.setDuressThreshold(ctx, it) },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Stricter than B1: wipe at this exact failed-attempt count. Use a low number (e.g. 3) so coercion is detected before the standard threshold. Must be ≤ B1.",
                    color = NoryptColors.MutedDeep,
                    fontSize = 11.sp,
                )
            }
            "C4" -> {
                DeadmanReliabilityPanel(ctx)
                Spacer(Modifier.height(12.dp))
                var requireBt by remember { mutableStateOf(ProtectPrefs.deadmanRequireBt(ctx)) }
                var requireGsm by remember { mutableStateOf(ProtectPrefs.deadmanRequireGsm(ctx)) }
                var requireWifi by remember { mutableStateOf(ProtectPrefs.deadmanRequireWifi(ctx)) }

                ConfigNumberField(
                    label = "Battery threshold % (default 5)",
                    current = ProtectPrefs.deadmanBatteryPct(ctx),
                    range = SettingBounds.BATTERY_PCT,
                    bound = SettingBounds::batteryPct,
                    onCommit = { ProtectPrefs.setDeadmanBatteryPct(ctx, it) },
                )
                Spacer(Modifier.height(8.dp))
                ConfigNumberField(
                    label = "Countdown seconds before wipe (default 60)",
                    current = ProtectPrefs.deadmanGraceSeconds(ctx),
                    range = SettingBounds.GRACE_SECONDS,
                    bound = SettingBounds::graceSeconds,
                    onCommit = { ProtectPrefs.setDeadmanGraceSeconds(ctx, it) },
                )
                Spacer(Modifier.height(8.dp))
                ConfigNumberField(
                    label = "Disarm minutes after unlock (default 0)",
                    current = ProtectPrefs.deadmanDisarmMinutesAfterUnlock(ctx),
                    range = SettingBounds.DISARM_MINUTES,
                    bound = SettingBounds::disarmMinutes,
                    onCommit = { ProtectPrefs.setDeadmanDisarmMinutesAfterUnlock(ctx, it) },
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
            "C6" -> {
                DeadmanReliabilityPanel(ctx)
                Spacer(Modifier.height(12.dp))
                ConfigNumberField(
                    label = "Hours without an unlock before the countdown (default 12)",
                    current = ProtectPrefs.unlockDeadlineHours(ctx),
                    range = SettingBounds.DEADLINE_HOURS,
                    bound = SettingBounds::deadlineHours,
                    onCommit = { ProtectPrefs.setUnlockDeadlineHours(ctx, it) },
                )
                if (GrapheneDetect.isGrapheneOS()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "GrapheneOS restarts a locked phone after its auto-reboot time (18 h by default). " +
                            "After a restart nothing runs until you unlock, so keep this shorter than your " +
                            "auto-reboot time or the countdown never comes.",
                        color = NoryptColors.Amber,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                ConfigNumberField(
                    label = "Countdown seconds before wipe (default 60)",
                    current = ProtectPrefs.unlockDeadlineGraceSeconds(ctx),
                    range = SettingBounds.GRACE_SECONDS,
                    bound = SettingBounds::graceSeconds,
                    onCommit = { ProtectPrefs.setUnlockDeadlineGraceSeconds(ctx, it) },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Counts from the last unlock, or from the moment this trigger was armed. Checked on " +
                        "the same alarm as the low-battery dead-man switch, so while the phone sleeps the " +
                        "check runs roughly every ten minutes. When the deadline passes a full-screen " +
                        "countdown starts; your screen-lock credential cancels it and restarts the clock.",
                    color = NoryptColors.MutedDeep,
                    fontSize = 11.sp,
                )
            }
            "A7" -> {
                InfoBlock(
                    title = "What this is",
                    body = "An entry point for a companion app on this phone that is signed with the same key " +
                        "as Norypt Protect, such as another Norypt app. Automation apps like Tasker cannot use " +
                        "it; for third-party panic apps use the PanicKit trigger (A5).",
                )
                Spacer(Modifier.height(8.dp))
                InfoBlock(
                    title = "How to fire it",
                    body = "From any app holding the signature permission " +
                        "com.norypt.protect.permission.TRIGGER, broadcast the action " +
                        "com.norypt.protect.action.TRIGGER. The ADB command below only works " +
                        "where the shell is allowed past the permission; on Android 14 and later " +
                        "it is refused (verified on Android 17), which is the protection working:",
                )
                Spacer(Modifier.height(6.dp))
                CodeBlock(
                    "adb shell am broadcast -a com.norypt.protect.action.TRIGGER " +
                        "-n ${ctx.packageName}/com.norypt.protect.triggers.ExternalTriggerReceiver"
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Because the receiver is gated by a signature-level permission, only apps signed with the " +
                        "Norypt Protect release key can fire it. To test it, use a companion app signed with " +
                        "the same key, with dry-run on.",
                    color = NoryptColors.MutedDeep,
                    fontSize = 11.sp,
                )
            }
            "A5" -> {
                var paired by remember { mutableStateOf(ProtectPrefs.panicTriggerPackage(ctx)) }
                val pairedLabel = remember(paired) {
                    paired?.let { pkg ->
                        runCatching {
                            ctx.packageManager.getApplicationLabel(
                                ctx.packageManager.getApplicationInfo(pkg, 0),
                            ).toString()
                        }.getOrDefault(pkg)
                    }
                }

                InfoBlock(
                    title = "What this is",
                    body = "Implements the PanicKit standard, so one panic app you choose (Ripple, Panic " +
                        "Button, a watch app, an NFC tag handler) can fire Norypt Protect's wipe.",
                )
                Spacer(Modifier.height(8.dp))
                InfoBlock(
                    title = "How to pair",
                    body = "Turn this switch on, then open the panic app and connect it to Norypt Protect. " +
                        "This app shows the panic app's package name and signing-key fingerprint; check " +
                        "them, confirm, then enter your App PIN. Only that one app can trigger a wipe, and " +
                        "only while this switch is on — nothing else can, even if it sends the same intent.",
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "PAIRED TRIGGER APP",
                    color = NoryptColors.MutedDeep,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                if (paired == null) {
                    Text(
                        "None. Nothing external can trigger a wipe.",
                        color = NoryptColors.Muted,
                        fontSize = 12.sp,
                    )
                } else {
                    Text(pairedLabel.orEmpty(), color = NoryptColors.Text, fontSize = 13.sp)
                    Text(paired.orEmpty(), color = NoryptColors.MutedDeep, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    // Unpairing only ever reduces what can wipe the device, so it needs no
                    // PIN — unlike pairing, which grants that power.
                    OutlinedButton(
                        onClick = {
                            ProtectPrefs.setPanicTriggerPackage(ctx, null)
                            paired = null
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NoryptColors.Red),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NoryptColors.Red.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Unpair")
                    }
                }
            }
            "B5" -> {
                InfoBlock(
                    title = "What this is",
                    body = "Watches every installed app. Posts a notification if any app silently gains the " +
                        "INTERNET permission after an update — a common stealth-tracking pattern. No wipe.",
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
        shape = RoundedCornerShape(10.dp),
        colors = noryptFieldColors(),
    )
}

/** A6's settings: the secret code and the SMS permission. */
@Composable
private fun SmsTriggerConfig() {
    val ctx = LocalContext.current
    var code by remember { mutableStateOf(ProtectPrefs.smsSecretCode(ctx).orEmpty()) }
    var showCode by remember { mutableStateOf(false) }
    // Masked, and typed with a password keyboard so the IME does not learn it.
    OutlinedTextField(
        value = code,
        onValueChange = {
            code = it
            ProtectPrefs.setSmsSecretCode(ctx, it.ifEmpty { null })
        },
        label = { Text("Secret SMS code") },
        singleLine = true,
        visualTransformation = if (showCode) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            TextButton(onClick = { showCode = !showCode }) {
                Text(if (showCode) "Hide" else "Show", color = NoryptColors.Accent, fontSize = 12.sp)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = noryptFieldColors(),
    )
    Spacer(Modifier.height(6.dp))
    OutlinedButton(
        onClick = {
            code = SmsSecretReceiver.generateCode()
            showCode = true
            ProtectPrefs.setSmsSecretCode(ctx, code)
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = NoryptColors.Accent),
        border = androidx.compose.foundation.BorderStroke(1.dp, NoryptColors.Border),
    ) { Text("Generate a strong code") }
    Spacer(Modifier.height(6.dp))
    Text(
        when {
            code.isNotEmpty() && !SmsSecretReceiver.isUsableCode(code) ->
                "Not armed: needs at least ${SmsSecretReceiver.MIN_CODE_LENGTH} characters."
            code.isNotEmpty() && code.length < SmsSecretReceiver.RECOMMENDED_CODE_LENGTH ->
                "Works, but a short code is easier to guess. Use " +
                    "${SmsSecretReceiver.RECOMMENDED_CODE_LENGTH}+ characters or generate one."
            else ->
                "The whole message must be exactly this code; a message that merely contains " +
                    "it will not trigger a wipe. Keep a copy somewhere safe off this phone."
        },
        color = if (code.isNotEmpty() && code.length < SmsSecretReceiver.RECOMMENDED_CODE_LENGTH)
            NoryptColors.Amber else NoryptColors.MutedDeep,
        fontSize = 11.sp,
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

/**
 * A number setting that is saved only when editing ends (Done, or leaving the field), and
 * always within [range]. Saving on every keystroke stored each intermediate value: changing
 * 360 to 120 briefly stored 1, and a trigger tick landing then could wipe the phone.
 */
@Composable
private fun ConfigNumberField(
    label: String,
    current: Int,
    range: IntRange,
    bound: (Int) -> Int,
    onCommit: (Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var saved by remember { mutableIntStateOf(current) }
    var text by remember { mutableStateOf(current.toString()) }
    fun commit() {
        val value = SettingBounds.parse(text, saved, bound)
        text = value.toString()
        if (value != saved) {
            saved = value
            onCommit(value)
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { if (it.length <= MAX_NUMBER_DIGITS && it.all { c -> c in '0'..'9' }) text = it },
        label = { Text(label) },
        supportingText = { Text("${range.first} to ${range.last}", color = NoryptColors.MutedDeep, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            commit()
            focusManager.clearFocus()
        }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) commit() },
        shape = RoundedCornerShape(10.dp),
        colors = noryptFieldColors(),
    )
}

private const val MAX_NUMBER_DIGITS = 5

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
            colors = noryptSwitchColors(),
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
            colors = noryptSwitchColors(),
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
            colors = noryptSwitchColors(),
        )
    }
}


/**
 * Surfaces the two platform conditions that decide whether C4 can actually fire on a
 * sleeping phone. Both degrade silently, so without this the trigger reads as armed while
 * the OS is deferring or throttling it.
 */
@Composable
private fun DeadmanReliabilityPanel(ctx: Context) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var canExact by remember { mutableStateOf(DeadmanScheduler.canScheduleExact(ctx)) }
    var bucket by remember { mutableStateOf(standbyBucket(ctx)) }

    // Re-read on return: granting the permission happens in system Settings.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canExact = DeadmanScheduler.canScheduleExact(ctx)
                bucket = standbyBucket(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Text(
        "RELIABILITY",
        color = NoryptColors.MutedDeep,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(6.dp))

    if (canExact) {
        Text("Exact alarms allowed — checks run on schedule.", color = NoryptColors.Muted, fontSize = 12.sp)
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(NoryptColors.Amber.copy(alpha = 0.12f))
                .border(1.dp, NoryptColors.Amber.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .padding(10.dp),
        ) {
            Text(
                "Exact alarms are not allowed for this app, so checks run on the system's own " +
                    "schedule and may be delayed by many minutes while the phone sleeps. The " +
                    "dead-man switch still works, but not to the second.",
                color = NoryptColors.Amber,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                runCatching {
                    ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:" + ctx.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NoryptColors.Accent),
            border = androidx.compose.foundation.BorderStroke(1.dp, NoryptColors.Border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Allow exact alarms")
        }
    }

    if (bucket != null && bucket!! >= 30) {
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(NoryptColors.Red.copy(alpha = 0.12f))
                .border(1.dp, NoryptColors.Red.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .padding(10.dp),
        ) {
            Text(
                "Android has put this app in a restricted background bucket, which can delay " +
                    "dead-man checks by hours. Open the app occasionally, and exclude it from " +
                    "battery optimisation, to keep the switch responsive.",
                color = NoryptColors.Red,
                fontSize = 11.sp,
            )
        }
    }
}

/** App-standby bucket, or null when the platform will not say. */
private fun standbyBucket(ctx: Context): Int? = runCatching {
    ctx.getSystemService(android.app.usage.UsageStatsManager::class.java)?.appStandbyBucket
}.getOrNull()
