package com.norypt.protect.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
            }
            Spacer(Modifier.height(2.dp))
            Text(trigger.description, color = NoryptColors.Muted, fontSize = 12.sp)
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
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
                Spacer(Modifier.height(8.dp))
                Text(
                    "Grant Norypt Protect the SMS permission in system Settings before incoming SMS messages can fire this trigger.",
                    color = NoryptColors.MutedDeep,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NoryptColors.Accent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NoryptColors.Border),
                ) { Text("Open app permission settings") }
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
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NoryptColors.Accent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NoryptColors.Border),
                ) { Text("Grant usage access") }
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
