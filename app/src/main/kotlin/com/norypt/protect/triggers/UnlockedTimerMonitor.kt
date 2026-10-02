package com.norypt.protect.triggers

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import com.norypt.protect.admin.Tier
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.service.CountdownAlert
import com.norypt.protect.service.CountdownMode
import com.norypt.protect.timeline.TamperBootAudit
import com.norypt.protect.timeline.TamperKind
import com.norypt.protect.timeline.TamperLog

/** How long the device has been unlocked, measured so a clock change cannot fake it. */
object UnlockedTimer {

    /**
     * Milliseconds since the last unlock seen in this boot, or null if none was. Measured on
     * elapsedRealtime: the wall clock can be stepped forward, which used to make a phone in
     * use look unlocked for hours and wipe it.
     */
    fun unlockedForMs(lastElapsedMs: Long, lastBoot: Int, nowElapsedMs: Long, bootNow: Int): Long? {
        if (lastElapsedMs <= 0L || lastBoot != bootNow) return null
        return (nowElapsedMs - lastElapsedMs).coerceAtLeast(0L)
    }

    /** Records "unlocked now" on both clocks. */
    fun stamp(context: Context) {
        ProtectPrefs.setLastUnlockMs(context, System.currentTimeMillis())
        ProtectPrefs.setLastUnlockElapsed(context, SystemClock.elapsedRealtime(), TamperBootAudit.bootCount(context) ?: 0)
    }
}

/**
 * A8 — the device stayed unlocked longer than the configured maximum.
 *
 * It starts the same cancellable countdown as C4 and C6 instead of wiping on the spot: the
 * phone is unlocked here, so the person holding it may well be the owner, and a credential
 * cancel, or simply locking the phone, ends it.
 */
object UnlockedTimerMonitor {

    private const val COUNTDOWN_GUARD_MS = 30 * 60_000L
    private var countdownStartedAtMs: Long = 0L

    var countdownActive: Boolean
        get() = countdownStartedAtMs != 0L &&
            SystemClock.elapsedRealtime() - countdownStartedAtMs < COUNTDOWN_GUARD_MS
        set(value) {
            countdownStartedAtMs = if (value) SystemClock.elapsedRealtime() else 0L
        }

    fun tick(context: Context) {
        if (!ProtectPrefs.isTriggerEnabled(context, "A8")) return

        // A8's premise is a phone left unlocked; once it is locked there is nothing to time.
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km?.isDeviceLocked == true) {
            CountdownAlert.forgetDeadline(context, CountdownMode.UNLOCKED_TOO_LONG)
            return
        }

        // No unlock seen in this boot: nothing to measure against. Bootstrapping to "now"
        // would start the clock at service start rather than at a real unlock.
        val unlockedFor = UnlockedTimer.unlockedForMs(
            ProtectPrefs.lastUnlockElapsedMs(context),
            ProtectPrefs.lastUnlockBoot(context),
            SystemClock.elapsedRealtime(),
            TamperBootAudit.bootCount(context) ?: 0,
        ) ?: return

        if (unlockedFor <= ProtectPrefs.maxUnlockedMinutes(context) * 60_000L) {
            CountdownAlert.forgetDeadline(context, CountdownMode.UNLOCKED_TOO_LONG)
            return
        }
        if (countdownActive || DeadmanMonitor.countdownActive || UnlockDeadlineMonitor.countdownActive) return
        countdownActive = true
        CountdownAlert.post(context, CountdownMode.UNLOCKED_TOO_LONG)
    }
}

class UserPresentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        UnlockedTimer.stamp(context)
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
    override val description = "Starts a wipe countdown if the device stays unlocked longer than the " +
        "configured maximum. Your screen-lock credential cancels it, and locking the phone ends it. " +
        "Requires Device Owner — the wipe call is denied for non-DO admins on Android 13+."
    override val requiredTier = Tier.DeviceOwner
    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A8", true)
    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A8", false)
}
