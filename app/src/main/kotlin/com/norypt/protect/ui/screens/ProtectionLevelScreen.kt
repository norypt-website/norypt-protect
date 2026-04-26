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
import androidx.compose.ui.text.style.TextOverflow
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
import com.norypt.protect.dpm.PowerMenuGuard
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
    var showTrust by remember { mutableStateOf(false) }

    if (showAbout) {
        AboutSubScreen(onBack = { showAbout = false }, padding = padding)
        return
    }

    if (showTrust) {
        TrustReportSubScreen(onBack = { showTrust = false }, padding = padding)
        return
    }

    var tier by remember { mutableStateOf(Provisioning.current(ctx)) }

    // Mutable UI state — read from device on first composition
    var usbOn by remember { mutableStateOf(UsbLockdown.isOn(ctx)) }
    var safeBootOn by remember { mutableStateOf(SafeBootLockdown.isOn(ctx)) }
    var sosOn by remember { mutableStateOf(EmergencySos.currentValue(ctx) == 0) }
    var antiTamperOn by remember { mutableStateOf(AntiTamper.isApplied(ctx)) }
    var launcherHidden by remember { mutableStateOf(LauncherAlias.isHidden(ctx)) }
    var powerMenuBlockOn by remember { mutableStateOf(PowerMenuGuard.isEnabled(ctx)) }

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
                powerMenuBlockOn = PowerMenuGuard.isEnabled(ctx)
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
            requiresDeviceOwner = true,
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
            requiresDeviceOwner = true,
        )

        // ── Card 4b: Block power menu when locked ──────────────────────────
        ToggleCard(
            title = "Block power menu when locked",
            subtitle = if (isOwner)
                "While the screen is locked, the stock Power Off menu is hidden. Only a 30-second hard firmware hold can shut the phone down."
            else
                "Requires Device Owner.",
            checked = powerMenuBlockOn,
            enabled = isOwner,
            onToggle = { on ->
                if (on) PowerMenuGuard.enable(ctx) else PowerMenuGuard.disable(ctx)
                powerMenuBlockOn = PowerMenuGuard.isEnabled(ctx)
            },
            requiresDeviceOwner = true,
        )

        // ── Card 5: Auto-disable Emergency SOS ────────────────────────────
        ToggleCard(
            title = "Disable Emergency SOS",
            subtitle = "Prevent accidental SOS calls from the lockscreen. Works via WRITE_SECURE_SETTINGS (ADB) or Device Owner.",
            checked = sosOn,
            enabled = true,
            onToggle = { on ->
                val before = EmergencySos.currentValue(ctx)
                val result = if (on) EmergencySos.disableIfPossible(ctx)
                             else EmergencySos.enableIfPossible(ctx)
                val after = EmergencySos.currentValue(ctx)
                val dbg = ctx.getSharedPreferences("norypt_admin_debug", Context.MODE_PRIVATE)
                val n = dbg.getInt("sos_click_count", 0) + 1
                dbg.edit()
                    .putInt("sos_click_count", n)
                    .putString("sos_click_${n}_wanted_disable", on.toString())
                    .putInt("sos_click_${n}_before", before)
                    .putInt("sos_click_${n}_after", after)
                    .putString("sos_click_${n}_path", result.name)
                    .apply()
                sosOn = after == 0
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
            requiresDeviceOwner = true,
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
            requiresDeviceOwner = true,
        )

        // ── Card 8: Biometric for app unlock ───────────────────────────────
        val biometricAvailable = remember {
            ctx.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
                ?.canAuthenticate(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
        }
        var biometricUnlockOn by remember { mutableStateOf(ProtectPrefs.launchBiometricEnabled(ctx)) }
        ToggleCard(
            title = "Use biometric to unlock app",
            subtitle = if (biometricAvailable)
                "Speeds up app open with fingerprint or face. App PIN remains the fallback and is always required when biometrics fail."
            else
                "No enrolled fingerprint or face on this device. Add one in Settings → Security to enable.",
            checked = biometricUnlockOn,
            enabled = biometricAvailable,
            onToggle = { on ->
                ProtectPrefs.setLaunchBiometricEnabled(ctx, on)
                biometricUnlockOn = on
            },
        )

        // ── Quick Settings tile helper ─────────────────────────────────────
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = {
                val dbg = ctx.getSharedPreferences("norypt_admin_debug", Context.MODE_PRIVATE)
                dbg.edit().putInt("qs_tile_button_clicks", dbg.getInt("qs_tile_button_clicks", 0) + 1).apply()
                val sbm = ctx.getSystemService(android.app.StatusBarManager::class.java)
                if (sbm == null) {
                    dbg.edit().putString("qs_tile_last_error", "StatusBarManager null").apply()
                    return@OutlinedButton
                }
                val component = android.content.ComponentName(
                    ctx,
                    com.norypt.protect.service.PanicTileService::class.java,
                )
                val icon = android.graphics.drawable.Icon.createWithResource(
                    ctx,
                    com.norypt.protect.R.mipmap.ic_launcher,
                )
                runCatching {
                    sbm.requestAddTileService(
                        component,
                        "Norypt Panic",
                        icon,
                        ctx.mainExecutor,
                    ) { resultCode ->
                        ctx.getSharedPreferences("norypt_admin_debug", Context.MODE_PRIVATE)
                            .edit().putInt("qs_tile_result_code", resultCode).apply()
                    }
                }.onFailure { e ->
                    dbg.edit().putString("qs_tile_last_error", "${e::class.simpleName}: ${e.message}").apply()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NoryptColors.Accent),
            border = androidx.compose.foundation.BorderStroke(1.dp, NoryptColors.Border),
        ) {
            Text("Add Panic tile to Quick Settings")
        }

        // ── Trust report button ─────────────────────────────────────────────
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = { showTrust = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NoryptColors.Green),
            border = androidx.compose.foundation.BorderStroke(1.dp, NoryptColors.Green.copy(alpha = 0.4f)),
        ) {
            Text("Trust report — verify permissions & signer")
        }

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
            "Run these ADB commands on a PC. Before step 2, the device must have:",
            color = NoryptColors.Muted,
            fontSize = 12.sp,
        )
        Text(
            "• No other Device Owner (Knox, Intune, etc.)\n" +
                "• No Google, Samsung, email, or work accounts\n" +
                "• No managed / work profile\n" +
                "• Only user 0 (no secondary users)",
            color = NoryptColors.Muted,
            fontSize = 12.sp,
        )
        CopyableCommand(label = "1. Check no Device Owner is set (output must be empty)", command = AdbInstructions.checkOwners, ctx = ctx)
        CopyableCommand(label = "2. Set Device Owner", command = AdbInstructions.setDeviceOwner, ctx = ctx)
        CopyableCommand(label = "3. Grant write secure settings", command = AdbInstructions.grantWriteSecureSettings, ctx = ctx)
        Text(
            "If step 2 fails: \"already set\" → an old MDM/Knox owner is still active; remove it via ADB or factory reset. \"already accounts\" → remove every account in Settings → Passwords & accounts. \"Unknown admin\" → the package above doesn't match the installed APK; reinstall.",
            color = NoryptColors.Muted,
            fontSize = 11.sp,
        )
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
    requiresDeviceOwner: Boolean = false,
) {
    val ctxIsOwner = enabled // for DO-required cards, enabled mirrors DO tier
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = if (enabled) NoryptColors.Text else NoryptColors.Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (requiresDeviceOwner) {
                    Spacer(Modifier.width(8.dp))
                    val badgeColor = if (ctxIsOwner) NoryptColors.MutedDeep else NoryptColors.Amber
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "DEVICE OWNER",
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
            }
            Text(
                subtitle,
                color = NoryptColors.Muted,
                fontSize = 12.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
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

// ── Trust report sub-screen ─────────────────────────────────────────────────

@Composable
private fun TrustReportSubScreen(onBack: () -> Unit, padding: PaddingValues) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val pkgInfo = runCatching {
        pm.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
    }.getOrNull()
    val requestedPerms = pkgInfo?.requestedPermissions?.toList().orEmpty()
    val certSha = com.norypt.protect.security.SelfVerification.currentCertSha256(ctx) ?: "unavailable"
    val pinned = com.norypt.protect.security.SelfVerification.pinnedCertSha256()

    val internetDeclared = requestedPerms.contains(android.Manifest.permission.INTERNET)
    val locationDeclared = requestedPerms.any {
        it == android.Manifest.permission.ACCESS_COARSE_LOCATION ||
            it == android.Manifest.permission.ACCESS_FINE_LOCATION ||
            it == android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
    }
    val contactsDeclared = requestedPerms.contains(android.Manifest.permission.READ_CONTACTS)
    val micDeclared = requestedPerms.contains(android.Manifest.permission.RECORD_AUDIO)
    val cameraDeclared = requestedPerms.contains(android.Manifest.permission.CAMERA)

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
                "Trust report",
                color = NoryptColors.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Norypt Protect operates entirely on-device. This report lists the " +
                "permissions it declares and its signing cert so you can verify it " +
                "does not talk to the internet or collect data.",
                color = NoryptColors.Muted,
                fontSize = 12.sp,
            )

            TrustCheckRow("No INTERNET permission", !internetDeclared)
            TrustCheckRow("No location permission", !locationDeclared)
            TrustCheckRow("No contacts permission", !contactsDeclared)
            TrustCheckRow("No microphone permission", !micDeclared)
            TrustCheckRow("No camera permission", !cameraDeclared)

            Spacer(Modifier.height(8.dp))
            Text("Declared permissions (${requestedPerms.size})", color = NoryptColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(NoryptColors.Surface2)
                    .border(1.dp, NoryptColors.Border, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (requestedPerms.isEmpty()) {
                        Text("(none)", color = NoryptColors.Muted, fontSize = 11.sp)
                    } else {
                        requestedPerms.forEach { perm ->
                            Text(
                                perm.removePrefix("android.permission."),
                                color = NoryptColors.Muted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("APK signing certificate (SHA-256)", color = NoryptColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Compare to the fingerprint published on norypt.com or the F-Droid " +
                "build page. Mismatch = repackaged APK.",
                color = NoryptColors.Muted,
                fontSize = 11.sp,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(NoryptColors.Surface2)
                    .border(1.dp, NoryptColors.Border, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                Text(
                    certSha,
                    color = NoryptColors.Text,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (pinned.isBlank()) {
                Text(
                    "⚠ No release fingerprint pinned in this build. The running APK " +
                    "is debug-signed; a production build will pin the Norypt signing " +
                    "cert and refuse to launch if the cert changes.",
                    color = NoryptColors.Amber,
                    fontSize = 11.sp,
                )
            } else {
                Text(
                    "Pinned: $pinned",
                    color = NoryptColors.Green,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(12.dp))
            val pkgName = ctx.packageName
            val versionName = pkgInfo?.versionName ?: "?"
            Text("Package", color = NoryptColors.Muted, fontSize = 11.sp)
            Text(pkgName, color = NoryptColors.Text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))
            Text("Version", color = NoryptColors.Muted, fontSize = 11.sp)
            Text(versionName, color = NoryptColors.Text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TrustCheckRow(label: String, ok: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NoryptColors.Surface2)
            .border(1.dp, NoryptColors.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (ok) "✓" else "✗",
            color = if (ok) NoryptColors.Green else NoryptColors.Red,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = if (ok) NoryptColors.Text else NoryptColors.Red,
            fontSize = 13.sp,
        )
    }
}
