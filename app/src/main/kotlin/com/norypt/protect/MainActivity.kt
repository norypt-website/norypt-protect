package com.norypt.protect

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import android.view.WindowManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.norypt.protect.admin.Provisioning
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.admin.Tier
import com.norypt.protect.service.ProtectForegroundService
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.security.AppPin
import com.norypt.protect.security.SelfVerification
import com.norypt.protect.ui.components.MainScaffold
import com.norypt.protect.ui.components.NavTab
import com.norypt.protect.ui.components.PinEntryDialog
import com.norypt.protect.ui.screens.HomeScreen
import com.norypt.protect.ui.screens.PinSetupScreen
import com.norypt.protect.ui.screens.ProtectionLevelScreen
import com.norypt.protect.ui.screens.TriggersScreen
import com.norypt.protect.ui.screens.WipeOptionsScreen
import com.norypt.protect.ui.theme.NoryptColors
import com.norypt.protect.ui.theme.NoryptProtectTheme
import com.norypt.protect.util.DebugTelemetry

class MainActivity : ComponentActivity() {

    private val requestNotifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* user choice; we don't re-prompt */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Self-verification: if a release-cert pin is set and the running APK
        // doesn't match, refuse to launch. Catches repackaged binaries before
        // any UI or prefs are touched. Unpinned debug/dev builds pass through.
        if (!SelfVerification.isTrustedCert(this)) {
            DebugTelemetry.put(
                this,
                "self_verify_result",
                "FAIL cert=${SelfVerification.currentCertSha256(this)}",
            )
            finishAndRemoveTask()
            return
        }

        // Keeps PIN entry and the trigger configuration out of recents thumbnails and
        // screenshots — both disclose the device's protection posture.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AColor.TRANSPARENT
        window.navigationBarColor = AColor.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Re-start the foreground service whenever the user opens the app.
        // Android kills the FGS on reinstall / force-stop / app-data clear and
        // neither ProtectAdminReceiver.onEnabled nor BootCompletedReceiver fire
        // in those cases, so the screen-on/off + USB_STATE receivers would stay
        // unregistered until the next reboot without this. The call is a no-op
        // when the service is already running.
        if (Provisioning.current(this) != Tier.None) {
            ProtectForegroundService.start(this)
        }

        val shortcutAction = intent.getStringExtra("action")

        // Handled before setContent: lockNow() and finish() are side effects, and Compose
        // may run a composition body more than once, which would fire them repeatedly.
        if (shortcutAction == "lock") {
            if (Provisioning.current(this) >= Tier.DeviceAdmin) {
                getSystemService(DevicePolicyManager::class.java).lockNow()
            }
            finish()
            return
        }

        setContent {
            NoryptProtectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().background(NoryptColors.Bg),
                    color = NoryptColors.Bg,
                ) {
                    var launchUnlocked by remember { mutableStateOf(false) }
                    // Keystore + Tink setup, so read once rather than on every recomposition.
                    val pinIsSet = remember { AppPin.isSet(this) }
                    if (!pinIsSet) {
                        PinSetupScreen(onPinSet = { pin ->
                            AppPin.set(this, pin)
                            recreate()
                        })
                    } else when (shortcutAction) {
                        // The wipe shortcut bypasses the launch gate intentionally: it has its
                        // own PIN confirmation, so requiring the PIN twice would be redundant.
                        "wipe" -> {
                            var showDialog by remember { mutableStateOf(true) }
                            if (showDialog) {
                                PinEntryDialog(
                                    title = "Confirm Wipe",
                                    onConfirm = { pin ->
                                        showDialog = false
                                        if (AppPin.verify(this, pin)) {
                                            PanicHandler.panic(this, "shortcut.wipe")
                                        }
                                        finish()
                                    },
                                    onDismiss = {
                                        showDialog = false
                                        finish()
                                    }
                                )
                            }
                        }
                        else -> {
                            if (!launchUnlocked) {
                                com.norypt.protect.ui.screens.LaunchGateScreen(
                                    onUnlocked = { launchUnlocked = true },
                                )
                            } else {
                                AppShell(onRequestEnableAdmin = { launchDeviceAdminSettings() })
                            }
                        }
                    }
                }
            }
        }
    }

    private fun launchDeviceAdminSettings() {
        val admin = ComponentName(this, ProtectAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Norypt Protect needs device admin to lock and wipe the device in a security emergency. No data leaves the device.",
            )
        startActivity(intent)
    }
}

@androidx.compose.runtime.Composable
private fun AppShell(onRequestEnableAdmin: () -> Unit) {
    var selected by remember { mutableStateOf(NavTab.HOME) }
    MainScaffold(selected = selected, onSelect = { selected = it }) { padding ->
        when (selected) {
            NavTab.HOME -> HomeScreen(padding = padding, onRequestEnableAdmin = onRequestEnableAdmin)
            NavTab.TRIGGERS -> TriggersScreen(padding = padding)
            NavTab.WIPE -> WipeOptionsScreen(padding = padding)
            NavTab.PROTECT -> ProtectionLevelScreen(padding = padding)
        }
    }
}
