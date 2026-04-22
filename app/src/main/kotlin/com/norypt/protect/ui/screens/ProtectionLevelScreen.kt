package com.norypt.protect.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.norypt.protect.admin.Provisioning
import com.norypt.protect.admin.Tier
import com.norypt.protect.dpm.AntiTamper
import com.norypt.protect.dpm.EmergencySos
import com.norypt.protect.dpm.LauncherAlias
import com.norypt.protect.dpm.SafeBootLockdown
import com.norypt.protect.dpm.UsbLockdown
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.security.AppPin
import com.norypt.protect.ui.components.PinEntryDialog
import com.norypt.protect.ui.theme.NoryptColors
import com.norypt.protect.util.AdbInstructions

@Composable
fun ProtectionLevelScreen(padding: PaddingValues) {
    val ctx = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }

    if (showAbout) {
        AboutSubScreen(onBack = { showAbout = false }, padding = padding)
        return
    }

    var tier by remember { mutableStateOf(Provisioning.current(ctx)) }

    // Mutable UI state — read from device on first composition
    var usbOn by remember { mutableStateOf(UsbLockdown.isOn(ctx)) }
    var safeBootOn by remember { mutableStateOf(SafeBootLockdown.isOn(ctx)) }
    var sosOn by remember { mutableStateOf(EmergencySos.currentValue(ctx) == 0) }
    var antiTamperOn by remember { mutableStateOf(AntiTamper.isApplied(ctx)) }
    var launcherHidden by remember { mutableStateOf(LauncherAlias.isHidden(ctx)) }

    // Anti-tamper dialog state
    var showAntiTamperWarning by remember { mutableStateOf(false) }
    var pendingAntiTamperEnable by remember { mutableStateOf(false) }
    var showAntiTamperPin by remember { mutableStateOf(false) }
    var antiTamperPinForDisable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { tier = Provisioning.current(ctx) }

    // Re-read every device-visible state when the user returns to the screen.
    // Deep-links to system Settings (SOS, Usage Access, app details, etc.) mean
    // the user can flip values outside our UI; without this observer the
    // Switches stay on the stale value until next cold launch.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                tier = Provisioning.current(ctx)
                usbOn = UsbLockdown.isOn(ctx)
                safeBootOn = SafeBootLockdown.isOn(ctx)
                val rawSos = EmergencySos.currentValue(ctx)
                sosOn = rawSos == 0
                antiTamperOn = AntiTamper.isApplied(ctx)
                launcherHidden = LauncherAlias.isHidden(ctx)
                // Debug telemetry so we can inspect over adb what the app actually reads.
                ctx.getSharedPreferences("norypt_admin_debug", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("sos_raw_value", rawSos)
                    .putInt("sos_read_count", (ctx.getSharedPreferences("norypt_admin_debug", Context.MODE_PRIVATE).getInt("sos_read_count", 0) + 1))
                    .apply()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isOwner = tier == Tier.DeviceOwner

    Column(
        Modifier
            .fillMaxSize()
            .background(NoryptColors.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "PROTECTION",
            color = NoryptColors.MutedDeep,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )

        // ── Card 1: Current Tier ──────────────────────────────────────────
        TierCard(tier)

        // ── Card 2: Upgrade to Device Owner (hidden when already DO) ──────
        if (!isOwner) {
            UpgradeCard(ctx)
        }

        // ── Card 3: USB Lockdown ──────────────────────────────────────────
        ToggleCard(
            title = "USB data lockdown",
            subtitle = if (isOwner)
                "Block USB file transfer (DISALLOW_USB_FILE_TRANSFER)."
            else
                "Requires Device Owner.",
            checked = usbOn,
            enabled = isOwner,
            onToggle = { on ->
                if (on) UsbLockdown.enable(ctx) else UsbLockdown.disable(ctx)
                usbOn = UsbLockdown.isOn(ctx)
            },
        )

        // ── Card 4: Safe-boot block ────────────────────────────────────────
        ToggleCard(
            title = "Block safe-boot",
            subtitle = if (isOwner)
                "Prevent booting into safe mode (DISALLOW_SAFE_BOOT)."
            else
                "Requires Device Owner.",
            checked = safeBootOn,
            enabled = isOwner,
            onToggle = { on ->
                if (on) SafeBootLockdown.enable(ctx) else SafeBootLockdown.disable(ctx)
                safeBootOn = SafeBootLockdown.isOn(ctx)
            },
        )

        // ── Card 5: Auto-disable Emergency SOS ────────────────────────────
        ToggleCard(
            title = "Disable Emergency SOS",
            subtitle = "Prevent accidental SOS calls from the lockscreen. Works via WRITE_SECURE_SETTINGS (ADB) or Device Owner.",
            checked = sosOn,
            enabled = true,
            onToggle = { on ->
                if (on) EmergencySos.disableIfPossible(ctx)
                else EmergencySos.enableIfPossible(ctx)
                sosOn = EmergencySos.currentValue(ctx) == 0
            },
        )

        // ── Card 6: Anti-tamper ────────────────────────────────────────────
        ToggleCard(
            title = "Anti-tamper",
            subtitle = if (isOwner)
                "Blocks factory reset and prevents uninstall. Requires App PIN to enable or disable."
            else
                "Requires Device Owner.",
            checked = antiTamperOn,
            enabled = isOwner,
            onToggle = { on ->
                if (on) {
                    // Enabling: show warning first, then PIN
                    pendingAntiTamperEnable = true
                    showAntiTamperWarning = true
                } else {
                    // Disabling: require PIN directly
                    antiTamperPinForDisable = true
                    showAntiTamperPin = true
                }
            },
        )

        // ── Card 7: Hide launcher icon ─────────────────────────────────────
        ToggleCard(
            title = "Hide launcher icon",
            subtitle = if (isOwner)
                "Remove app from the drawer. Icon may take 1–2 minutes to disappear on some launchers."
            else
                "Requires Device Owner.",
            checked = launcherHidden,
            enabled = isOwner,
            onToggle = { hide ->
                if (hide) {
                    LauncherAlias.hide(ctx)
                    ProtectPrefs.setLauncherHidden(ctx, true)
                } else {
                    LauncherAlias.show(ctx)
                    ProtectPrefs.setLauncherHidden(ctx, false)
                }
                launcherHidden = LauncherAlias.isHidden(ctx)
            },
        )

        // ── About button ───────────────────────────────────────────────────
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = { showAbout = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NoryptColors.Muted),
            border = androidx.compose.foundation.BorderStroke(1.dp, NoryptColors.Border),
        ) {
            Text("About this app")
        }
    }

    // Anti-tamper warning dialog
    if (showAntiTamperWarning) {
        AlertDialog(
            onDismissRequest = {
                showAntiTamperWarning = false
                pendingAntiTamperEnable = false
            },
            icon = {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = NoryptColors.Amber)
            },
            title = { Text("Enable Anti-tamper?", color = NoryptColors.Text) },
            text = {
                Text(
                    "Once enabled, factory-reset is blocked, this app cannot be uninstalled, " +
                    "and the only way to remove it is via ADB or another Device Owner app. " +
                    "The toggle requires your App PIN both to enable and to disable. Continue?",
                    color = NoryptColors.Muted,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAntiTamperWarning = false
                    showAntiTamperPin = true
                }) { Text("Continue", color = NoryptColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAntiTamperWarning = false
                    pendingAntiTamperEnable = false
                }) { Text("Cancel", color = NoryptColors.Muted) }
            },
            containerColor = NoryptColors.Surface1,
        )
    }

    // Anti-tamper PIN dialog (enable or disable)
    if (showAntiTamperPin) {
        PinEntryDialog(
            title = if (pendingAntiTamperEnable) "Enter App PIN to enable" else "Enter App PIN to disable",
            onConfirm = { pin ->
                if (AppPin.verify(ctx, pin)) {
                    showAntiTamperPin = false
                    if (pendingAntiTamperEnable) {
                        val ok = AntiTamper.apply(ctx)
                        if (ok) ProtectPrefs.setAntiTamperEnabled(ctx, true)
                    } else {
                        val ok = AntiTamper.release(ctx)
                        if (ok) ProtectPrefs.setAntiTamperEnabled(ctx, false)
                    }
                    antiTamperOn = AntiTamper.isApplied(ctx)
                    pendingAntiTamperEnable = false
                    antiTamperPinForDisable = false
                }
            },
            onDismiss = {
                showAntiTamperPin = false
                pendingAntiTamperEnable = false
                antiTamperPinForDisable = false
            },
        )
    }
}

// ── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun TierCard(tier: Tier) {
    val (label, description, color) = when (tier) {
        Tier.None -> Triple(
            "Not enrolled",
            "No device admin active. Wipe and lock features are unavailable.",
            NoryptColors.Red,
        )
        Tier.DeviceAdmin -> Triple(
            "Device Admin",
            "Lock and wipe are active. Upgrade to Device Owner via ADB to unlock USB lockdown, safe-boot block, anti-tamper, and launcher hiding.",
            NoryptColors.Amber,
        )
        Tier.DeviceOwner -> Triple(
            "Device Owner",
            "All protections available. Maximum privilege tier.",
            NoryptColors.Green,
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NoryptColors.Surface2)
            .border(1.dp, NoryptColors.Border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(label.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(description, color = NoryptColors.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun UpgradeCard(ctx: Context) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NoryptColors.Surface2)
            .border(1.dp, NoryptColors.Border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "UPGRADE TO DEVICE OWNER",
            color = NoryptColors.MutedDeep,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Run these ADB commands on a PC. The device must have no Google or manufacturer accounts.",
            color = NoryptColors.Muted,
            fontSize = 12.sp,
        )
        CopyableCommand(label = "1. Set Device Owner", command = AdbInstructions.setDeviceOwner, ctx = ctx)
        CopyableCommand(label = "2. Grant write secure settings", command = AdbInstructions.grantWriteSecureSettings, ctx = ctx)
        OutlinedButton(
            onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_PRIVACY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NoryptColors.Muted),
            border = androidx.compose.foundation.BorderStroke(1.dp, NoryptColors.Border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open Privacy settings (to remove accounts)", fontSize = 12.sp)
        }
    }
}

@Composable
private fun CopyableCommand(label: String, command: String, ctx: Context) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NoryptColors.Bg)
            .border(1.dp, NoryptColors.Border, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = NoryptColors.Muted, fontSize = 11.sp)
        Text(
            command,
            color = NoryptColors.Text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        TextButton(
            onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("adb", command))
            },
            contentPadding = PaddingValues(0.dp),
        ) {
            Text("Copy", color = NoryptColors.Accent, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NoryptColors.Surface2)
            .border(1.dp, NoryptColors.Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                color = if (enabled) NoryptColors.Text else NoryptColors.Muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                subtitle,
                color = NoryptColors.Muted,
                fontSize = 12.sp,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NoryptColors.Accent,
                uncheckedThumbColor = NoryptColors.Muted,
                uncheckedTrackColor = NoryptColors.Surface1,
                disabledCheckedTrackColor = NoryptColors.Muted,
                disabledUncheckedTrackColor = NoryptColors.Surface1,
            ),
        )
    }
}

// ── About sub-screen ─────────────────────────────────────────────────────────

@Composable
private fun AboutSubScreen(onBack: () -> Unit, padding: PaddingValues) {
    Column(
        Modifier
            .fillMaxSize()
            .background(NoryptColors.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(padding),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = NoryptColors.Text)
            }
            Text(
                "About",
                color = NoryptColors.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        AboutScreen(padding = PaddingValues(0.dp))
    }
}
