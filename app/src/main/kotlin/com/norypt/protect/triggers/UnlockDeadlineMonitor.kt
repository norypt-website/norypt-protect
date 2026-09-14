package com.norypt.protect.triggers

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import com.norypt.protect.admin.Tier
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.service.CountdownAlert
import com.norypt.protect.service.CountdownMode
import com.norypt.protect.util.DebugTelemetry

/**
 * C6 — Wipe if the device has not been unlocked for N hours.
 *
 * The dead-man switch the F-Droid description promised but the app never had: A8 fires when
 * the phone *stays unlocked* too long, C4 on low battery. This one covers the phone that was
 * seized, lost, or left behind — locked, and never opened again by its owner.
 *
 * Driven by the same Doze-piercing alarm chain as C4 ([DeadmanScheduler]); the service's
 * handler tick does not run while the SoC sleeps, which is exactly when this must fire.
 *
 * The baseline is the latest of three stamps: the last USER_PRESENT, the moment C6 was armed
 * (the owner is demonstrably present then), and the last time this check itself found the
 * device open. The third is kept apart from `last_unlock_ms` so C6 can never push A8's
 * timer forward.
 */
object UnlockDeadlineMonitor {

    private const val HOUR_MS = 3_600_000L

    /** Same expiring guard as C4: a full-screen intent that never opened must not disarm the trigger for good. */
    private const val COUNTDOWN_GUARD_MS = 30 * 60_000L

    private var countdownStartedAtMs: Long = 0L

    var countdownActive: Boolean
        get() = countdownStartedAtMs != 0L &&
            SystemClock.elapsedRealtime() - countdownStartedAtMs < COUNTDOWN_GUARD_MS
        set(value) {
            countdownStartedAtMs = if (value) SystemClock.elapsedRealtime() else 0L
        }

    internal fun baselineMs(lastUnlockMs: Long, armedAtMs: Long, seenUnlockedMs: Long): Long =
        maxOf(lastUnlockMs, armedAtMs, seenUnlockedMs)

    /**
     * True once [maxHours] have passed since [baselineMs]. A baseline of zero (nothing ever
     * recorded) or a clock that now reads earlier than the baseline never counts as expired:
     * the wrong answer here is an irreversible wipe.
     */
    internal fun deadlineReached(baselineMs: Long, nowMs: Long, maxHours: Int): Boolean {
        if (maxHours <= 0 || baselineMs <= 0L) return false
        if (nowMs < baselineMs) return false
        return nowMs - baselineMs >= maxHours * HOUR_MS
    }

    fun tick(ctx: Context) {
        if (!ProtectPrefs.isTriggerEnabled(ctx, UnlockDeadlineTrigger.id)) return
        DebugTelemetry.bump(ctx, "c6_ticks_when_enabled")
        val now = System.currentTimeMillis()

        val km = ctx.getSystemService(KeyguardManager::class.java)
        if (km != null && !km.isDeviceLocked) {
            // The device is open right now, so someone holding the credential is present.
            ProtectPrefs.setUnlockDeadlineSeenUnlockedMs(ctx, now)
            return
        }

        val baseline = baselineMs(
            ProtectPrefs.lastUnlockMs(ctx),
            ProtectPrefs.unlockDeadlineArmedAtMs(ctx),
            ProtectPrefs.unlockDeadlineSeenUnlockedMs(ctx),
        )
        if (!deadlineReached(baseline, now, ProtectPrefs.unlockDeadlineHours(ctx))) return

        if (countdownActive || DeadmanMonitor.countdownActive) {
            DebugTelemetry.bump(ctx, "c6_skip_countdown_active")
            return
        }
        DebugTelemetry.bump(ctx, "c6_countdown_launched")
        countdownActive = true
        CountdownAlert.post(ctx, CountdownMode.UNLOCK_DEADLINE)
    }
}

object UnlockDeadlineTrigger : Trigger {
    override val id = "C6"
    override val label = "Not unlocked for too long"
    override val description =
        "Starts a wipe countdown if the device has not been unlocked for the configured number of " +
        "hours — for a phone that was seized, lost or left behind. Counts from the last unlock, or " +
        "from the moment this trigger was armed. A screen-lock credential cancels the countdown. " +
        "Requires Device Owner — the wipe call is denied for non-DO admins on Android 13+."
    override val requiredTier = Tier.DeviceOwner

    override fun arm(context: Context) {
        ProtectPrefs.setTriggerEnabled(context, id, true)
        // Arming happens from the unlocked UI, so the owner is present at this instant.
        ProtectPrefs.setUnlockDeadlineArmedAtMs(context, System.currentTimeMillis())
        DeadmanScheduler.schedule(context)
    }

    override fun disarm(context: Context) {
        ProtectPrefs.setTriggerEnabled(context, id, false)
        UnlockDeadlineMonitor.countdownActive = false
        DeadmanScheduler.schedule(context)
    }
}
