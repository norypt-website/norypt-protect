package com.norypt.protect.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs

object UnlockedTimerMonitor {
    fun tick(context: Context) {
        if (!ProtectPrefs.isTriggerEnabled(context, "A8")) return
        val lastUnlock = ProtectPrefs.lastUnlockMs(context)
        val now = System.currentTimeMillis()
        if (lastUnlock == 0L) {
            ProtectPrefs.setLastUnlockMs(context, now)
            return
        }
        val maxMs = ProtectPrefs.maxUnlockedMinutes(context) * 60_000L
        if (now - lastUnlock > maxMs) {
            PanicHandler.panic(context, "unlocked.timer")
        }
    }
}

class UserPresentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ProtectPrefs.setLastUnlockMs(context, System.currentTimeMillis())
    }
}

object UnlockedTimerTrigger : Trigger {
    override val id = "A8"
    override val label = "Max unlocked duration"
    override val description = "Wipe if the device stays unlocked longer than the configured maximum. " +
        "Requires Device Owner — the wipe call is denied for non-DO admins on Android 13+."
    override val requiredTier = Tier.DeviceOwner
    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A8", true)
    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A8", false)
}
