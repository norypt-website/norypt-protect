package com.norypt.protect.triggers

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.timeline.TamperKind
import com.norypt.protect.timeline.TamperLog

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
        // A successful unlock proves the owner is present, so the failed-attempt run
        // that feeds the duress wipe ends here. onPasswordSucceeded only covers
        // credential unlocks; biometric unlocks would otherwise leave the count standing.
        ProtectPrefs.resetFailedAttempts(context)
        TamperLog.record(context, TamperKind.UNLOCK)
    }
}

/**
 * Registers [UserPresentReceiver] at runtime.
 *
 * ACTION_USER_PRESENT is not on the implicit-broadcast exception list, so a
 * manifest-declared receiver is never invoked on API 26+ (see the same constraint on
 * SCREEN_ON/SCREEN_OFF in [PowerGestureMonitor]). While it was declared in the manifest,
 * `last_unlock_ms` was never written: A8 returned early on every tick and the dead-man
 * "disarm after unlock" guard measured against 0 and never suppressed anything.
 */
object UserPresentMonitor {

    private var receiver: BroadcastReceiver? = null

    fun start(ctx: Context) {
        if (receiver != null) return
        val r = UserPresentReceiver()
        ctx.applicationContext.registerReceiver(r, IntentFilter(Intent.ACTION_USER_PRESENT))
        receiver = r
    }

    fun stop(ctx: Context) {
        receiver?.let { runCatching { ctx.applicationContext.unregisterReceiver(it) } }
        receiver = null
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
