package com.norypt.protect.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.util.GestureCounter

/**
 * C3 — 5× power-button press within 3 seconds triggers wipe.
 *
 * SCREEN_ON/SCREEN_OFF cannot be declared in the manifest on API 26+.
 * The receiver is runtime-registered from [ProtectForegroundService.onCreate]
 * and unregistered in [ProtectForegroundService.onDestroy].
 */
object PowerGestureMonitor {

    private var receiver: BroadcastReceiver? = null
    private val counter = GestureCounter(threshold = 5, windowMs = 3_000)

    fun start(ctx: Context) {
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (!ProtectPrefs.isTriggerEnabled(c, "C3")) return
                if (counter.onEvent(System.currentTimeMillis())) {
                    counter.reset()
                    PanicHandler.panic(c, "power.gesture")
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ctx.registerReceiver(receiver, filter)
    }

    fun stop(ctx: Context) {
        receiver?.let { runCatching { ctx.unregisterReceiver(it) } }
        receiver = null
    }
}

object PowerGestureTrigger : Trigger {
    override val id = "C3"
    override val label = "5× power-button gesture"
    override val description =
        "Press the power button 5 times within 3 seconds to trigger an immediate wipe. " +
        "Requires Device Owner — the wipe call is denied for non-DO admins on Android 13+."
    override val requiredTier = Tier.DeviceOwner

    override fun arm(context: Context) =
        ProtectPrefs.setTriggerEnabled(context, id, true)

    override fun disarm(context: Context) =
        ProtectPrefs.setTriggerEnabled(context, id, false)
}
