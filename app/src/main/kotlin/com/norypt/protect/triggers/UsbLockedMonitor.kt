package com.norypt.protect.triggers

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs

/**
 * Fires only when USB DATA is negotiated while the screen is locked.
 *
 * Plain charging (a dumb wall charger or a power-only cable) does NOT trigger:
 * those produce ACTION_POWER_CONNECTED but never raise the USB function or
 * data_unlocked flags inside ACTION_USB_STATE.
 *
 * Forensic dumps require MTP/PTP/ADB/AOA — every one of those flips at least
 * one function flag in the ACTION_USB_STATE extras. We treat any function
 * being true OR the explicit data_unlocked flag as proof of a data link and
 * panic if the keyguard is locked at that moment.
 *
 * The receiver is registered at runtime from [start]/[stop] (called by
 * ProtectForegroundService) because ACTION_USB_STATE cannot be declared in
 * the manifest on modern Android.
 */
object UsbLockedMonitor {

    private const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
    private const val EXTRA_CONNECTED = "connected"
    private const val EXTRA_DATA_UNLOCKED = "data_unlocked"

    // Function flags that ACTION_USB_STATE includes when each USB function is up.
    private val DATA_FUNCTION_FLAGS = listOf(
        "mtp", "ptp", "adb", "midi", "accessory", "ncm", "rndis", "uvc", "audio_source",
    )

    private var receiver: BroadcastReceiver? = null

    fun start(context: Context) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                // Debug telemetry stays local — increments a prefs counter so we can
                // verify the receiver is actually firing without adding logcat output.
                debugIncrement(ctx, "a9_usb_state_total")
                if (!ProtectPrefs.isTriggerEnabled(ctx, "A9")) return
                debugIncrement(ctx, "a9_usb_state_when_enabled")
                val connected = intent.getBooleanExtra(EXTRA_CONNECTED, false)
                if (!connected) return
                debugIncrement(ctx, "a9_usb_state_connected")
                val km = ctx.getSystemService(KeyguardManager::class.java)
                val locked = km.isKeyguardLocked
                if (locked) debugIncrement(ctx, "a9_usb_state_locked")
                val dataActive = isUsbDataActive(intent)
                if (dataActive) debugIncrement(ctx, "a9_usb_state_data_active")
                if (!dataActive) return
                if (!locked) return
                debugIncrement(ctx, "a9_panic_fired")
                PanicHandler.panic(ctx, "usb.data.while.locked")
            }
        }
        receiver = r
        context.applicationContext.registerReceiver(r, IntentFilter(ACTION_USB_STATE))
    }

    private fun debugIncrement(ctx: Context, key: String) {
        val sp = ctx.getSharedPreferences("norypt_a9_debug", Context.MODE_PRIVATE)
        sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply()
    }

    fun stop(context: Context) {
        receiver?.let { runCatching { context.applicationContext.unregisterReceiver(it) } }
        receiver = null
    }

    private fun isUsbDataActive(intent: Intent): Boolean {
        if (intent.getBooleanExtra(EXTRA_DATA_UNLOCKED, false)) return true
        return DATA_FUNCTION_FLAGS.any { intent.getBooleanExtra(it, false) }
    }
}

object UsbLockedTrigger : Trigger {
    override val id = "A9"
    override val label = "USB data while locked"
    override val description = "Wipe if a USB DATA cable (MTP/PTP/ADB/etc.) is negotiated while the screen is locked. Plain charging cables and dumb wall chargers do NOT trigger this."
    override val requiredTier = Tier.DeviceAdmin
    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A9", true)
    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A9", false)
}
