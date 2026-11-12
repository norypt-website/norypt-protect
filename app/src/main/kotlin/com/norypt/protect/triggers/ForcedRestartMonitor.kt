package com.norypt.protect.triggers

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.service.CountdownAlert
import com.norypt.protect.service.CountdownMode
import com.norypt.protect.timeline.TamperBootAudit
import com.norypt.protect.timeline.TamperKind
import com.norypt.protect.timeline.TamperLog
import com.norypt.protect.util.DebugTelemetry

/**
 * C9's decision, kept free of Android so it can be unit tested.
 *
 * Android tells running apps before it shuts down ([Intent.ACTION_SHUTDOWN], delivered to receivers
 * registered at runtime: the power-menu restart and power off, GrapheneOS's auto-reboot, updates, an
 * empty battery). A hardware reset does not: holding the power button (alone or with a volume key)
 * until the phone restarts cuts Android off at once. So a boot is forced when Norypt watched the boot
 * before it and that boot left no clean-shutdown record.
 */
object ForcedRestartRule {

    enum class Action { NONE, COUNTDOWN, WIPE }

    /**
     * @param watchedBoot the boot count Norypt last ran in, 0 when never recorded (first run with C9's code).
     * @param currentBoot this boot's count.
     * @param cleanBoot the boot count whose shutdown Norypt saw, 0 when none.
     * @param pending a forced-restart countdown from an earlier boot ended with neither an unlock nor a wipe.
     */
    fun decide(watchedBoot: Int, currentBoot: Int, cleanBoot: Int, pending: Boolean): Action = when {
        currentBoot <= watchedBoot -> Action.NONE // the same boot: the service restarted, the phone did not
        // Restarting during the countdown, by any route, is what someone without the screen lock would do.
        pending -> Action.WIPE
        watchedBoot <= 0 -> Action.NONE
        // A boot Norypt never reached (reset again before it started) also left no record: it counts.
        cleanBoot != currentBoot - 1 -> Action.COUNTDOWN
        else -> Action.NONE
    }
}

/**
 * The few values C9 needs across a restart, in device-protected storage and written with commit():
 * the shared settings store writes asynchronously, which a shutdown, or a second forced reset a
 * moment after the first, can cut short. Nothing here is secret.
 */
internal object RestartRecord {
    private const val FILE = "norypt_restart_record"
    private const val KEY_WATCHED_BOOT = "watched_boot"
    private const val KEY_CLEAN_BOOT = "clean_shutdown_boot"
    private const val KEY_PENDING_BOOT = "pending_boot"

    private fun prefs(ctx: Context) =
        ctx.createDeviceProtectedStorageContext().getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun watchedBoot(ctx: Context): Int = prefs(ctx).getInt(KEY_WATCHED_BOOT, 0)
    fun setWatchedBoot(ctx: Context, boot: Int) = prefs(ctx).edit().putInt(KEY_WATCHED_BOOT, boot).commit()
    fun cleanBoot(ctx: Context): Int = prefs(ctx).getInt(KEY_CLEAN_BOOT, 0)
    fun setCleanBoot(ctx: Context, boot: Int) = prefs(ctx).edit().putInt(KEY_CLEAN_BOOT, boot).commit()

    /** The boot a countdown was started in, 0 when none is pending. */
    fun pendingBoot(ctx: Context): Int = prefs(ctx).getInt(KEY_PENDING_BOOT, 0)
    fun setPendingBoot(ctx: Context, boot: Int) = prefs(ctx).edit().putInt(KEY_PENDING_BOOT, boot).commit()
}

/**
 * C9 — Wipe after a forced restart. A boot that followed a hardware reset rather than a shutdown (see
 * [ForcedRestartRule]) starts a 30-second countdown on the lock screen at once; unlocking the phone
 * cancels it, as the owner of a phone that crashed or froze would. Any restart while that countdown is
 * still pending wipes at the next start, with no new countdown, so restarting again buys no time.
 *
 * Runs before the first unlock: the service that calls it starts at LOCKED_BOOT_COMPLETED, and it
 * touches only device-protected storage. The start-up grace of the other countdowns does not apply.
 */
object ForcedRestartMonitor {

    const val GRACE_SECONDS = 30

    private const val COUNTDOWN_GUARD_MS = 5 * 60_000L

    /** Past the deadline by this much with no outcome, the tick wipes without the screen. */
    private const val OVERDUE_AFTER_MS = 10_000L

    private val mode = CountdownMode.FORCED_RESTART

    private var countdownStartedAtMs: Long = 0L

    var countdownActive: Boolean
        get() = countdownStartedAtMs != 0L &&
            SystemClock.elapsedRealtime() - countdownStartedAtMs < COUNTDOWN_GUARD_MS
        set(value) {
            countdownStartedAtMs = if (value) SystemClock.elapsedRealtime() else 0L
        }

    private var shutdownReceiver: BroadcastReceiver? = null

    /** Records a clean shutdown of this boot. Registered at runtime: Android delivers ACTION_SHUTDOWN to no manifest receiver. */
    fun startWatch(ctx: Context) {
        if (shutdownReceiver != null) return
        val app = ctx.applicationContext
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action != Intent.ACTION_SHUTDOWN) return
                TamperBootAudit.bootCount(app)?.let { RestartRecord.setCleanBoot(app, it) }
            }
        }
        app.registerReceiver(r, IntentFilter(Intent.ACTION_SHUTDOWN))
        shutdownReceiver = r
    }

    fun stopWatch(ctx: Context) {
        shutdownReceiver?.let { runCatching { ctx.applicationContext.unregisterReceiver(it) } }
        shutdownReceiver = null
    }

    fun pending(ctx: Context): Boolean = RestartRecord.pendingBoot(ctx) != 0

    /** At every service start; acts once per boot. Must run before anything that could take long. */
    fun onServiceStart(ctx: Context) {
        val current = TamperBootAudit.bootCount(ctx) ?: return
        val watched = RestartRecord.watchedBoot(ctx)
        if (current == watched) {
            tick(ctx)
            return
        }
        // Recorded whether C9 is armed or not, so arming it later has a baseline.
        RestartRecord.setWatchedBoot(ctx, current)
        val enabled = ProtectPrefs.isTriggerEnabled(ctx, ForcedRestartTrigger.id)
        if (!enabled) {
            settle(ctx)
            return
        }
        val action = ForcedRestartRule.decide(watched, current, RestartRecord.cleanBoot(ctx), pending(ctx))
        DebugTelemetry.log("c9 boot=$current watched=$watched clean=${RestartRecord.cleanBoot(ctx)} action=$action")
        when (action) {
            ForcedRestartRule.Action.NONE -> Unit
            ForcedRestartRule.Action.WIPE -> {
                TamperLog.record(ctx, TamperKind.FORCED_RESTART, REPEAT_DETAIL)
                PanicHandler.panic(ctx, REPEAT_REASON)
                // Reached only in dry-run or after a failed wipe: this restart was forced too, so it counts again.
                settle(ctx)
                if (RestartRecord.cleanBoot(ctx) != current - 1) startCountdown(ctx, current)
            }
            ForcedRestartRule.Action.COUNTDOWN -> {
                TamperLog.record(ctx, TamperKind.FORCED_RESTART, COUNTDOWN_DETAIL)
                startCountdown(ctx, current)
            }
        }
    }

    private fun startCountdown(ctx: Context, boot: Int) {
        // Pending from this moment, even if the screen never opens.
        RestartRecord.setPendingBoot(ctx, boot)
        ProtectPrefs.setCountdownDeadline(ctx, mode.extraValue, SystemClock.elapsedRealtime() + GRACE_SECONDS * 1_000L, boot)
        DeadmanScheduler.schedule(ctx)
        launch(ctx)
    }

    /**
     * Service tick and dead-man alarm: an unlock settles a pending countdown; a screen that closed comes
     * back; past its deadline with no outcome (the screen never opened, or stalled) the phone is wiped.
     */
    fun tick(ctx: Context) {
        if (!pending(ctx)) return
        if (!ProtectPrefs.isTriggerEnabled(ctx, ForcedRestartTrigger.id) || !lockedNow(ctx)) {
            settle(ctx)
            return
        }
        val deadline = ProtectPrefs.countdownDeadline(ctx, mode.extraValue)
        val boot = TamperBootAudit.bootCount(ctx) ?: 0
        if (overdue(deadline, ProtectPrefs.countdownBoot(ctx, mode.extraValue), SystemClock.elapsedRealtime(), boot)) {
            DebugTelemetry.bump(ctx, "c9_overdue_wipe")
            PanicHandler.panic(ctx, mode.reason)
            settle(ctx)
        } else {
            launch(ctx)
        }
    }

    /** The phone locked right now; false once it is open, or when it has no screen lock at all. */
    private fun lockedNow(ctx: Context): Boolean =
        ctx.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true

    internal fun overdue(deadline: Long, deadlineBoot: Int, nowElapsed: Long, bootCount: Int): Boolean =
        deadline > 0L && deadlineBoot == bootCount && nowElapsed > deadline + OVERDUE_AFTER_MS

    /** The countdown ended (unlocked, expired in dry-run, or C9 disarmed): nothing is owed any more. */
    fun settle(ctx: Context) {
        if (RestartRecord.pendingBoot(ctx) != 0) RestartRecord.setPendingBoot(ctx, 0)
        CountdownAlert.forgetDeadline(ctx, mode)
        countdownActive = false
        runCatching { DeadmanScheduler.schedule(ctx) }
    }

    private fun launch(ctx: Context) {
        if (countdownActive) return
        countdownActive = true
        CountdownAlert.post(ctx, mode)
    }

    const val REPEAT_REASON = "forced.restart.again"

    private const val COUNTDOWN_DETAIL =
        "The phone restarted without shutting down: a button held until it restarted, a crash or a power " +
            "loss. A ${GRACE_SECONDS}-second wipe countdown started at the lock screen."
    private const val REPEAT_DETAIL =
        "The phone restarted again while the forced-restart countdown was pending, without being unlocked."
}

object ForcedRestartTrigger : Trigger {
    override val id = "C9"
    override val label = "Forced restart"
    override val description =
        "After a restart that skipped Android's shutdown — the power button held, alone or with a volume " +
            "key, until the phone restarts — a ${ForcedRestartMonitor.GRACE_SECONDS}-second wipe countdown " +
            "starts at the lock screen. Unlocking the phone cancels it. Restarting again while it is pending " +
            "wipes at the next start. A crash or a power loss looks the same, so the countdown starts then too. " +
            "Requires Device Owner."
    override val requiredTier = Tier.DeviceOwner

    override fun arm(context: Context) {
        ProtectPrefs.setTriggerEnabled(context, id, true)
        // This boot is watched from now; its own clean shutdown is what the next start looks for.
        TamperBootAudit.bootCount(context)?.let { RestartRecord.setWatchedBoot(context, it) }
        ForcedRestartMonitor.startWatch(context)
    }

    override fun disarm(context: Context) {
        ProtectPrefs.setTriggerEnabled(context, id, false)
        ForcedRestartMonitor.settle(context)
    }
}
