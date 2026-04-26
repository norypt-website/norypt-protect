package com.norypt.protect.triggers

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs

object UnlockedTimerMonitor {
    fun tick(context: Context) {
        if (!ProtectPrefs.isTriggerEnabled(context, "A8")) return

        // No real USER_PRESENT has been recorded yet — nothing to measure
        // against. Do NOT bootstrap to "now": that would cause the timer to
        // count from FGS start and panic after `maxUnlockedMinutes` even
        // though the user never actually unlocked.
        val lastUnlock = ProtectPrefs.lastUnlockMs(context)
        if (lastUnlock <= 0L) return

        // If the device is currently locked, the user is not "leaving it
        // unlocked too long". A8's whole premise is "phone is unlocked and
        // forgotten"; if it's locked, skip.
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km?.isDeviceLocked == true) return

        val maxMs = ProtectPrefs.maxUnlockedMinutes(context) * 60_000L
        val now = System.currentTimeMillis()
        if (now - lastUnlock > maxMs) {
            PanicHandler.panic(context, "unlocked.timer")
        }
    }
}

class UserPresentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
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
