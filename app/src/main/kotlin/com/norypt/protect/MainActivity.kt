package com.norypt.protect

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.security.AppPin
import com.norypt.protect.ui.components.MainScaffold
import com.norypt.protect.ui.components.NavTab
import com.norypt.protect.ui.components.PinEntryDialog
import com.norypt.protect.ui.screens.AboutScreen
import com.norypt.protect.ui.screens.HomeScreen
import com.norypt.protect.ui.screens.PinSetupScreen
import com.norypt.protect.ui.screens.TriggersScreen
import com.norypt.protect.ui.screens.WipeOptionsScreen
import com.norypt.protect.ui.theme.NoryptColors
import com.norypt.protect.ui.theme.NoryptProtectTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AColor.TRANSPARENT
        window.navigationBarColor = AColor.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val shortcutAction = intent.getStringExtra("action")

        setContent {
            NoryptProtectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().background(NoryptColors.Bg),
                    color = NoryptColors.Bg,
                ) {
                    if (!AppPin.isSet(this)) {
                        PinSetupScreen(onPinSet = { pin ->
                            AppPin.set(this, pin)
                            recreate()
                        })
                    } else when (shortcutAction) {
                        "lock" -> {
                            val tier = Provisioning.current(this)
                            if (tier >= Tier.DeviceAdmin) {
                                val dpm = getSystemService(DevicePolicyManager::class.java)
                                dpm.lockNow()
                            }
                            finish()
                        }
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
                        else -> AppShell(onRequestEnableAdmin = { launchDeviceAdminSettings() })
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
            NavTab.ABOUT -> AboutScreen(padding = padding)
        }
    }
}
