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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.norypt.protect.dpm.InstallLockdown
import com.norypt.protect.dpm.LauncherAlias
import com.norypt.protect.dpm.LockdownMode
import com.norypt.protect.dpm.PowerMenuGuard
import com.norypt.protect.dpm.SafeBootLockdown
import com.norypt.protect.dpm.UsbLockdown
import com.norypt.protect.motion.MotionDetector
import com.norypt.protect.motion.MotionLockMonitor
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.ui.components.NoryptCard
import com.norypt.protect.ui.components.PinGuardedToggleCard
import com.norypt.protect.ui.components.ScreenHeader
import com.norypt.protect.ui.components.SecondaryButton
import com.norypt.protect.ui.components.SectionLabel
import com.norypt.protect.ui.components.TagPill
import com.norypt.protect.ui.components.ToggleCard
import com.norypt.protect.ui.theme.NoryptColors
import com.norypt.protect.util.AdbInstructions
import com.norypt.protect.util.DebugTelemetry

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
    var sosRaw by remember { mutableStateOf(EmergencySos.currentValue(ctx)) }
    var sosOn by remember { mutableStateOf(EmergencySos.currentValue(ctx) == 0) }
    var antiTamperOn by remember { mutableStateOf(AntiTamper.isApplied(ctx)) }
    var launcherHidden by remember { mutableStateOf(LauncherAlias.isHidden(ctx)) }
    var powerMenuBlockOn by remember { mutableStateOf(PowerMenuGuard.isEnabled(ctx)) }
    var installBlockOn by remember { mutableStateOf(InstallLockdown.isOn(ctx)) }
    var lockdownOn by remember { mutableStateOf(LockdownMode.isEnabled(ctx)) }
    var motionOn by remember { mutableStateOf(MotionLockMonitor.isEnabled(ctx)) }
    var motionSensitivity by remember { mutableIntStateOf(MotionLockMonitor.sensitivity(ctx)) }
    val motionAvailable = remember { MotionLockMonitor.hasSensor(ctx) }

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
                sosRaw = rawSos
                sosOn = rawSos == 0
                antiTamperOn = AntiTamper.isApplied(ctx)
                launcherHidden = LauncherAlias.isHidden(ctx)
                powerMenuBlockOn = PowerMenuGuard.isEnabled(ctx)
                installBlockOn = InstallLockdown.isOn(ctx)
                lockdownOn = LockdownMode.isEnabled(ctx)
                motionOn = MotionLockMonitor.isEnabled(ctx)
                DebugTelemetry.put(ctx, "sos_raw_value", rawSos)
                DebugTelemetry.bump(ctx, "sos_read_count")
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
        ScreenHeader(
            title = "Protect",
            subtitle = "Device hardening, lockdown and app settings.",
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
        // -1 means the platform would not tell us the value, usually because
        // WRITE_SECURE_SETTINGS was never granted over ADB. Reporting that as "off" would
        // read as "SOS is still enabled" when the truth is that we do not know — and in a
        // security app an unverified control must not look like a verified one.
        val sosUnknown = sosRaw == -1
        ToggleCard(
            title = "Disable Emergency SOS",
            subtitle = if (sosUnknown)
                "STATE UNKNOWN — this device does not expose the Emergency SOS setting to the " +
                    "app. Grant WRITE_SECURE_SETTINGS over ADB, or check Settings › Safety & " +
                    "emergency yourself. Do not assume SOS is disabled."
            else
                "Prevent accidental SOS calls from the lockscreen. Works via WRITE_SECURE_SETTINGS (ADB) or Device Owner.",
            checked = sosOn,
            enabled = true,
            onToggle = { on ->
                val before = EmergencySos.currentValue(ctx)
                val result = if (on) EmergencySos.disableIfPossible(ctx)
                             else EmergencySos.enableIfPossible(ctx)
                val after = EmergencySos.currentValue(ctx)
                sosRaw = after
                DebugTelemetry.bump(ctx, "sos_click_count")
                val n = DebugTelemetry.count(ctx, "sos_click_count")
                DebugTelemetry.put(ctx, "sos_click_${n}_wanted_disable", on.toString())
                DebugTelemetry.put(ctx, "sos_click_${n}_before", before)
                DebugTelemetry.put(ctx, "sos_click_${n}_after", after)
                DebugTelemetry.put(ctx, "sos_click_${n}_path", result.name)
                sosOn = after == 0
            },
        )

        // ── Card 6: Anti-tamper ────────────────────────────────────────────
        PinGuardedToggleCard(
            title = "Anti-tamper",
            subtitle = if (isOwner)
                "Blocks factory reset and prevents uninstall. Requires App PIN to enable or disable."
            else
                "Requires Device Owner.",
            checked = antiTamperOn,
            enabled = isOwner,
            requiresDeviceOwner = true,
            warningTitle = "Enable Anti-tamper?",
            warningText = "Once enabled, factory-reset is blocked, this app cannot be uninstalled, " +
                "and the only way to remove it is via ADB or another Device Owner app. " +
                "The toggle requires your App PIN both to enable and to disable. Continue?",
            apply = { on ->
                val ok = if (on) AntiTamper.apply(ctx) else AntiTamper.release(ctx)
                if (ok) ProtectPrefs.setAntiTamperEnabled(ctx, on)
                ok
            },
            onChanged = { antiTamperOn = AntiTamper.isApplied(ctx) },
        )

        // ── Card 6b: Block app installation ────────────────────────────────
        PinGuardedToggleCard(
            title = "Block app installation",
            subtitle = if (isOwner)
                "No app can be installed on the owner profile: not from a store, not by sideloading, " +
                    "not over ADB. Shuts the door on malware, extraction tools and unattended exploits " +
                    "that need a payload installed. Also blocks updates, including updates to Norypt " +
                    "Protect — turn off before updating. App PIN required either way."
            else
                "Requires Device Owner.",
            checked = installBlockOn,
            enabled = isOwner,
            requiresDeviceOwner = true,
            warningTitle = "Block all app installation?",
            warningText = "Every install on the owner profile will be refused, including app updates " +
                "and updates to Norypt Protect itself. You will need to turn this off, with your App " +
                "PIN, before installing or updating anything. Continue?",
            apply = { on -> if (on) InstallLockdown.enable(ctx) else InstallLockdown.disable(ctx) },
            onChanged = { installBlockOn = InstallLockdown.isOn(ctx) },
        )

        // ── Card 6c: Lockdown mode ─────────────────────────────────────────
        PinGuardedToggleCard(
            title = "Lockdown mode (blank device)",
            subtitle = if (isOwner)
                "The phone shows a black screen and nothing else: no launcher, no notifications, no " +
                    "quick settings, no power menu, no user switching. Survives reboots. Hold a finger " +
                    "anywhere on the blank screen for 3 seconds and enter your App PIN to reach " +
                    "Settings, switch user, or exit."
            else
                "Requires Device Owner.",
            checked = lockdownOn,
            enabled = isOwner,
            requiresDeviceOwner = true,
            warningTitle = "Enter lockdown mode?",
            warningText = "The device will show a blank screen until you hold a finger on it for " +
                "3 seconds and enter your App PIN. Lockdown survives reboots. If you forget the PIN, " +
                "the only way out is a factory reset from recovery. Emergency calls from the lock " +
                "screen should remain available, but verify that on this device before relying on " +
                "it. Continue?",
            apply = { on -> if (on) LockdownMode.enable(ctx) else LockdownMode.disable(ctx) },
            onChanged = { lockdownOn = LockdownMode.isEnabled(ctx) },
        )

        // ── Card 6d: Anti-snatch ───────────────────────────────────────────
        ToggleCard(
            title = "Anti-snatch: lock on sudden movement",
            subtitle = when {
                !motionAvailable -> "This device has no accelerometer."
                tier == Tier.None -> "Requires device admin."
                else -> "Locks the screen the instant the phone is yanked or dropped. Listens only while " +
                    "the phone is unlocked and the screen is on, so it costs nothing in a pocket."
            },
            checked = motionOn,
            enabled = motionAvailable && tier != Tier.None,
            onToggle = { on ->
                if (on) MotionLockMonitor.enable(ctx) else MotionLockMonitor.disable(ctx)
                motionOn = MotionLockMonitor.isEnabled(ctx)
            },
            extra = if (motionOn) {
                {
                    SensitivityRow(motionSensitivity) { level ->
                        MotionLockMonitor.setSensitivity(ctx, level)
                        motionSensitivity = level
                    }
                }
            } else null,
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
        SectionLabel("More")
        SecondaryButton(
            label = "Add Panic tile to Quick Settings",
            onClick = {
                DebugTelemetry.bump(ctx, "qs_tile_button_clicks")
                val sbm = ctx.getSystemService(android.app.StatusBarManager::class.java)
                if (sbm == null) {
                    DebugTelemetry.put(ctx, "qs_tile_last_error", "StatusBarManager null")
                    return@SecondaryButton
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
                        DebugTelemetry.put(ctx, "qs_tile_result_code", resultCode)
                    }
                }.onFailure { e ->
                    DebugTelemetry.put(ctx, "qs_tile_last_error", "${e::class.simpleName}: ${e.message}")
                }
            },
        )

        // ── Trust report button ─────────────────────────────────────────────
        SecondaryButton(
            label = "Trust report — verify permissions & signer",
            color = NoryptColors.Green,
            onClick = { showTrust = true },
        )

        // ── About button ───────────────────────────────────────────────────
        SecondaryButton(
            label = "About this app",
            color = NoryptColors.Muted,
            onClick = { showAbout = true },
        )
        Spacer(Modifier.height(8.dp))
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
    NoryptCard(accent = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Current tier", color = NoryptColors.TextStrong, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TagPill(label.uppercase(), color)
        }
        Spacer(Modifier.height(6.dp))
        Text(description, color = NoryptColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun UpgradeCard(ctx: Context) {
    NoryptCard(tint = NoryptColors.Accent) {
        Text(
            "UPGRADE TO DEVICE OWNER",
            color = NoryptColors.Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(8.dp))
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
        Spacer(Modifier.height(8.dp))
        CopyableCommand(label = "1. Check no Device Owner is set (output must be empty)", command = AdbInstructions.checkOwners, ctx = ctx)
        Spacer(Modifier.height(8.dp))
        CopyableCommand(label = "2. Set Device Owner", command = AdbInstructions.setDeviceOwner, ctx = ctx)
        Spacer(Modifier.height(8.dp))
        CopyableCommand(label = "3. Grant write secure settings", command = AdbInstructions.grantWriteSecureSettings, ctx = ctx)
        Spacer(Modifier.height(8.dp))
        Text(
            "If step 2 fails: \"already set\" → an old MDM/Knox owner is still active; remove it via ADB or factory reset. \"already accounts\" → remove every account in Settings → Passwords & accounts. \"Unknown admin\" → the package above doesn't match the installed APK; reinstall.",
            color = NoryptColors.Muted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(4.dp))
        SecondaryButton(
            label = "Open Privacy settings (to remove accounts)",
            color = NoryptColors.Muted,
            onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_PRIVACY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
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
private fun SensitivityRow(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf(
        MotionDetector.SENSITIVITY_LOW to "Low",
        MotionDetector.SENSITIVITY_MEDIUM to "Medium",
        MotionDetector.SENSITIVITY_HIGH to "High",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Sensitivity", color = NoryptColors.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        options.forEach { (level, label) ->
            FilterChip(
                selected = selected == level,
                onClick = { onSelect(level) },
                label = { Text(label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NoryptColors.AccentDim,
                    selectedLabelColor = NoryptColors.Accent,
                    labelColor = NoryptColors.Muted,
                ),
            )
        }
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
