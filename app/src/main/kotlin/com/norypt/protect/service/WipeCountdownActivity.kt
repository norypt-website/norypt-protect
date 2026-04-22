package com.norypt.protect.service

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.triggers.DeadmanMonitor
import com.norypt.protect.ui.theme.NoryptColors
import com.norypt.protect.ui.theme.NoryptProtectTheme
import kotlinx.coroutines.delay

/**
 * C4 — Full-screen countdown activity launched over the lockscreen by [DeadmanMonitor].
 *
 * - Shows a countdown timer (grace period from prefs).
 * - Cancel button launches keyguard credential intent; RESULT_OK aborts wipe.
 * - Re-checks conditions on every tick; if connectivity restored → silent abort.
 * - On countdown reaches 0 → [PanicHandler.panic].
 */
class WipeCountdownActivity : ComponentActivity() {

    private val cancelLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lockscreen
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val km = getSystemService(KeyguardManager::class.java)
        km.requestDismissKeyguard(this, null)

        val graceSeconds = ProtectPrefs.deadmanGraceSeconds(this)

        setContent {
            NoryptProtectTheme {
                CountdownScreen(
                    initialSeconds = graceSeconds,
                    onCancel = { launchKeyguardCancel() },
                    onConditionsCleared = { finish() },
                    onTimerExpired = {
                        PanicHandler.panic(this@WipeCountdownActivity, "deadman")
                        finish()
                    },
                    conditionChecker = { areConditionsCleared() },
                )
            }
        }
    }

    override fun onDestroy() {
        DeadmanMonitor.countdownActive = false
        super.onDestroy()
    }

    private fun launchKeyguardCancel() {
        val km = getSystemService(KeyguardManager::class.java)
        val intent = km.createConfirmDeviceCredentialIntent(
            "Authorise cancel",
            "Wipe countdown will abort on success. Wipe stays armed otherwise.",
        )
        if (intent != null) {
            cancelLauncher.launch(intent)
        } else {
            // No lock screen set — treat as authorised
            finish()
        }
    }

    /** Returns true if the dead-man conditions are no longer met (battery recovered or connectivity restored). */
    private fun areConditionsCleared(): Boolean {
        val level = batteryLevel(this)
        val threshold = ProtectPrefs.deadmanBatteryPct(this)
        if (level > threshold) return true

        val requireBt = ProtectPrefs.deadmanRequireBt(this)
        val requireGsm = ProtectPrefs.deadmanRequireGsm(this)
        val requireWifi = ProtectPrefs.deadmanRequireWifi(this)

        if (requireBt && isBluetoothConnected(this)) return true
        if (requireGsm && isCellularConnected(this)) return true
        if (requireWifi && isWifiConnected(this)) return true
        return false
    }

    private fun batteryLevel(ctx: Context): Int {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun isBluetoothConnected(ctx: Context): Boolean {
        val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        return bm.adapter?.bondedDevices?.isNotEmpty() == true
    }

    private fun isCellularConnected(ctx: Context): Boolean = hasTransport(ctx, NetworkCapabilities.TRANSPORT_CELLULAR)
    private fun isWifiConnected(ctx: Context): Boolean = hasTransport(ctx, NetworkCapabilities.TRANSPORT_WIFI)

    private fun hasTransport(ctx: Context, transport: Int): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(transport)
    }
}

@Composable
private fun CountdownScreen(
    initialSeconds: Int,
    onCancel: () -> Unit,
    onConditionsCleared: () -> Unit,
    onTimerExpired: () -> Unit,
    conditionChecker: () -> Boolean,
) {
    var secondsLeft by remember { mutableIntStateOf(initialSeconds) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000L)
            if (conditionChecker()) {
                onConditionsCleared()
                return@LaunchedEffect
            }
            secondsLeft--
        }
        onTimerExpired()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NoryptColors.Red),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = "NORYPT PROTECT",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )

            Text(
                text = "AUTO-WIPE IN",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
            )

            Text(
                text = secondsLeft.toString(),
                color = Color.White,
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold,
            )

            Button(
                onClick = onCancel,
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = NoryptColors.Red,
                ),
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
