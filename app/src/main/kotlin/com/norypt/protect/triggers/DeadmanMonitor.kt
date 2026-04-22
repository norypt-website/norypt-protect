package com.norypt.protect.triggers

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.norypt.protect.admin.Tier
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.service.WipeCountdownActivity

/**
 * C4 — Low-battery dead-man switch.
 *
 * Polled on each FGS tick. When battery level drops to or below the configured
 * threshold AND all required connectivity (BT/GSM/Wi-Fi) is absent, launches
 * [WipeCountdownActivity] over the lockscreen. The countdown activity handles
 * the actual panic call after the grace period.
 */
object DeadmanMonitor {

    /** Set to true while WipeCountdownActivity is visible to prevent duplicate launches. */
    var countdownActive: Boolean = false

    fun tick(ctx: Context) {
        if (!ProtectPrefs.isTriggerEnabled(ctx, "C4")) return

        // Disarm window after last unlock
        val disarmMinutes = ProtectPrefs.deadmanDisarmMinutesAfterUnlock(ctx)
        if (disarmMinutes > 0) {
            val lastUnlock = ProtectPrefs.lastUnlockMs(ctx)
            if (System.currentTimeMillis() - lastUnlock < disarmMinutes * 60_000L) return
        }

        val level = batteryLevel(ctx)
        val threshold = ProtectPrefs.deadmanBatteryPct(ctx)
        if (level > threshold) return

        // Check required connectivity — if ANY required link is present, abort
        val requireBt = ProtectPrefs.deadmanRequireBt(ctx)
        val requireGsm = ProtectPrefs.deadmanRequireGsm(ctx)
        val requireWifi = ProtectPrefs.deadmanRequireWifi(ctx)

        if (requireBt && isBluetoothConnected(ctx)) return
        if (requireGsm && isCellularConnected(ctx)) return
        if (requireWifi && isWifiConnected(ctx)) return

        if (countdownActive) return

        countdownActive = true
        val intent = Intent(ctx, WipeCountdownActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        ctx.startActivity(intent)
    }

    // --- Private helpers ---

    private fun batteryLevel(ctx: Context): Int {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun isBluetoothConnected(ctx: Context): Boolean {
        val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bm.adapter?.bondedDevices?.isNotEmpty() == true
    }

    private fun isCellularConnected(ctx: Context): Boolean =
        hasTransport(ctx, NetworkCapabilities.TRANSPORT_CELLULAR)

    private fun isWifiConnected(ctx: Context): Boolean =
        hasTransport(ctx, NetworkCapabilities.TRANSPORT_WIFI)

    private fun hasTransport(ctx: Context, transport: Int): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(transport)
    }
}

object DeadmanTrigger : Trigger {
    override val id = "C4"
    override val label = "Low-battery dead-man switch"
    override val description =
        "Starts a wipe countdown when battery is low and all monitored connections are lost."
    override val requiredTier = Tier.DeviceAdmin

    override fun arm(context: Context) =
        ProtectPrefs.setTriggerEnabled(context, id, true)

    override fun disarm(context: Context) =
        ProtectPrefs.setTriggerEnabled(context, id, false)
}
