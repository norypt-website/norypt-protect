package com.norypt.protect

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.security.AppPin
import com.norypt.protect.ui.screens.HomeScreen
import com.norypt.protect.ui.screens.PinSetupScreen
import com.norypt.protect.ui.theme.NoryptColors
import com.norypt.protect.ui.theme.NoryptProtectTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    } else {
                        HomeScreen(onRequestEnableAdmin = { launchDeviceAdminSettings() })
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
