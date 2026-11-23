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
 * A clean shutdown leaves a record one of two ways. Android tells running apps before the framework
 * shuts down ([Intent.ACTION_SHUTDOWN], delivered to receivers registered at runtime: the power-menu
 * restart and power off, updates, an empty battery). A reboot init performs itself (GrapheneOS's
 * auto-reboot, `adb reboot`) skips the framework and sends no broadcast, but init sends SIGTERM to every
 * process group first, which [ShutdownSignal] records (since 1.3.5; before that an auto-reboot read as
 * forced). A hardware reset does neither: holding the power button (alone or with a volume key) until
 * the phone restarts cuts Android off at once. So a boot is forced when Norypt watched the boot before
 * it and that boot left no clean-shutdown record ([CleanShutdownRule]).
 *
 * Since 1.3.6 the kernel boot_id ([KernelIds]) tells a restart of Android's userspace apart: system_server or zygote
 * died and init started them again. BOOT_COUNT goes up and no shutdown record is left, but the kernel keeps running and
 * the credential-encrypted storage stays unlocked: not the before-first-unlock state C9 exists for. Up to 1.3.5 it
 * counted as forced, and an unattended phone wiped 30 seconds later.
 */
object ForcedRestartRule {

    enum class Action { NONE, COUNTDOWN, WIPE }

    /**
     * @param watchedBoot the boot count Norypt last ran in, 0 when never recorded (first run with C9's code).
     * @param currentBoot this boot's count.
     * @param cleanBoot the boot count whose shutdown Norypt saw ([CleanShutdownRule.cleanBoot]), 0 when none.
     * @param pending a forced-restart countdown from an earlier boot ended with neither an unlock nor a wipe, or its
     *   record cannot be read.
     * @param kernel the kernel boot_ids ([KernelIds]); unknown ones leave the boot-count rules of 1.3.5.
     *
     * Order: the same kernel as the watched boot with a count that did not go up, or an unknown kernel with such a
     * count, is the same boot (the service restarted); a countdown pending from an earlier boot wipes (a userspace
     * restart raised the count, so a crash of system_server cannot escape it); never watched decides nothing; the same
     * kernel with a higher count is a userspace restart; on a new kernel a clean mark of the watched boot itself
     * vouches ([KernelIds.anyMarkVouchesFor]) for the boot right after it, or when the count did not go up (then nothing
     * else does: a countdown); otherwise the boot-count rule.
     */
    fun decide(
        watchedBoot: Int,
        currentBoot: Int,
        cleanBoot: Int,
        pending: Boolean,
        kernel: KernelIds = KernelIds.UNKNOWN,
    ): Action {
        val newKernel = kernel.newKernel()
        // A new kernel is never the same boot, whatever the count says.
        if (currentBoot <= watchedBoot && !newKernel) return Action.NONE // the service restarted, the phone did not
        // Restarting during the countdown, by any route, is what someone without the screen lock would do.
        if (pending) return Action.WIPE
        if (watchedBoot <= 0) return Action.NONE
        // Only Android's userspace restarted: no power-off, storage still unlocked.
        if (kernel.sameKernel() && currentBoot > watchedBoot) return Action.NONE
        if (newKernel) {
            // With a raised count only for the boot right after the watched one (a boot Norypt missed may have been
            // the forced one); with a count that did not go up, no boot can be told missed: the mark decides.
            val raised = currentBoot > watchedBoot
            val nextBoot = raised && currentBoot == watchedBoot + 1
            if ((!raised || nextBoot) && kernel.anyMarkVouchesFor(watchedBoot)) return Action.NONE
            // The count was read before Android raised it: the boot-count rule would ask about the boot before the
            // watched one, whose old mark is almost always there. Only a mark of the watched boot vouches then.
            if (!raised) return Action.COUNTDOWN
        }
        // A boot Norypt never reached (reset again before it started) also left no record: it counts.
        return if (cleanBoot != currentBoot - 1) Action.COUNTDOWN else Action.NONE
    }
}

/**
 * Kernel boot_ids for [ForcedRestartRule.decide] (fix 1.3.6). Null: unreadable, not recorded (stored by 1.3.5 or
 * earlier) or not paired with its count. [watched]: stored with the watched boot. [current]: the running kernel.
 * [cleanShutdown]: stored with the `ACTION_SHUTDOWN` mark, [cleanShutdownBoot] its count. [signalMark]: the one in
 * init's orderly-reboot mark, [signalMarkBoot] its count.
 */
data class KernelIds(
    val watched: String? = null,
    val current: String? = null,
    val cleanShutdown: String? = null,
    val cleanShutdownBoot: Int? = null,
    val signalMark: String? = null,
    val signalMarkBoot: Int? = null,
) {
    /** Both readable and different: a kernel boot happened since the watched one. */
    fun newKernel(): Boolean =
        ShutdownSignalRules.validBootId(watched) && ShutdownSignalRules.validBootId(current) && watched != current

    /** Both readable and equal: the kernel of the watched boot still runs. */
    fun sameKernel(): Boolean = ShutdownSignalRules.validBootId(watched) && watched == current

    /**
     * A mark ([markId], [markBoot]) vouches for the end of the watched boot only when it was written in exactly that
     * boot: the watched kernel's boot_id and the watched boot count. A mark whose shutdown never finished, followed by
     * a userspace restart in the same kernel, names an earlier count and vouches for nothing.
     */
    fun vouchesFor(watchedBoot: Int, markId: String?, markBoot: Int?): Boolean =
        newKernel() && watchedBoot > 0 && ShutdownSignalRules.validBootId(markId) && markId == watched &&
            markBoot != null && markBoot == watchedBoot

    fun anyMarkVouchesFor(watchedBoot: Int): Boolean =
        vouchesFor(watchedBoot, cleanShutdown, cleanShutdownBoot) || vouchesFor(watchedBoot, signalMark, signalMarkBoot)

    companion object {
        val UNKNOWN = KernelIds()
    }
}

/** The boot a service start records as watched, and the kernel boot_id it ran in (null: none stored). Pure. */
data class WatchedBoot(val boot: Int, val bootId: String?) {
    companion object {
        /**
         * A new kernel whose count did not go up means the count was read before Android raised it: stored without a
         * boot_id, so a later start in the same kernel that reads the real count is never taken for a userspace
         * restart; the boot-count rules decide then.
         */
        fun record(boot: Int, bootId: String?, previousBoot: Int, kernel: KernelIds): WatchedBoot {
            val stale = kernel.newKernel() && previousBoot > 0 && boot <= previousBoot
            return WatchedBoot(boot, if (stale) null else bootId)
        }
    }
}

/** When an `ACTION_SHUTDOWN` mark of the running kernel is void (fix 1.3.6). Pure. */
object CleanMarkRule {

    /**
     * An Android shutdown ends well within a minute (ShutdownThread's and init's time-outs): a process that outlived its
     * own kernel's mark by this much saw a shutdown that never finished.
     */
    const val MAX_AGE_MS = 10L * 60_000L

    /**
     * [markId]: the boot_id paired with the mark; [markedAtMs]: its elapsedRealtime (null unknown). Void when it names
     * [runningBootId] and either [minAgeMs] is 0 (a service start: Android came back in the same kernel), or it is at
     * least [minAgeMs] old, or its time cannot be this kernel's (missing, negative, or later than now). A mark that
     * stays would vouch for a held-power reset later in this kernel.
     */
    fun void(markId: String?, markedAtMs: Long?, runningBootId: String?, nowElapsedMs: Long, minAgeMs: Long): Boolean {
        if (!ShutdownSignalRules.validBootId(runningBootId) || markId != runningBootId) return false
        if (minAgeMs <= 0L) return true
        if (markedAtMs == null || markedAtMs < 0L || markedAtMs > nowElapsedMs) return true
        return nowElapsedMs - markedAtMs >= minAgeMs
    }
}

/**
 * The debug line of each service start: which rule decided. Labels only, never a boot count or a boot_id, so a field
 * report can tell the paths apart and say nothing about the phone. Pure.
 */
object StartLabel {
    fun of(watchedBoot: Int, currentBoot: Int, kernel: KernelIds, armed: Boolean, action: ForcedRestartRule.Action): String {
        val newKernel = kernel.newKernel()
        val sameBoot = currentBoot <= watchedBoot && !newKernel
        val why = when {
            action == ForcedRestartRule.Action.WIPE -> "countdown interrupted by a restart"
            sameBoot -> "same boot"
            watchedBoot <= 0 -> "first start"
            kernel.sameKernel() -> "userspace restart (same kernel)"
            !armed -> "restart, trigger not armed"
            action == ForcedRestartRule.Action.COUNTDOWN -> "restart without a clean mark"
            else -> "clean restart"
        }
        val k = when {
            newKernel -> ", new kernel"
            // Already in the reason.
            kernel.sameKernel() && !sameBoot && watchedBoot > 0 && action != ForcedRestartRule.Action.WIPE -> ""
            kernel.sameKernel() -> ", same kernel"
            else -> ", kernel unknown"
        }
        val d = when (action) {
            ForcedRestartRule.Action.NONE -> "none"
            ForcedRestartRule.Action.COUNTDOWN -> "countdown"
            ForcedRestartRule.Action.WIPE -> "wipe"
        }
        return "start: $why$k, decision $d"
    }
}

/**
 * The few values C9 needs across a restart, in device-protected storage and written with commit():
 * the shared settings store writes asynchronously, which a shutdown, or a second forced reset a
 * moment after the first, can cut short. Nothing here is secret: boot counts, elapsedRealtime values and the kernel's
 * random boot_id, each id stored as "<count> <boot_id>" ([PairedBootId]) and trusted only beside its count.
 */
internal object RestartRecord {
    private const val FILE = "norypt_restart_record"
    private const val KEY_WATCHED_BOOT = "watched_boot"
    private const val KEY_WATCHED_BOOT_ID = "watched_boot_id"
    private const val KEY_CLEAN_BOOT = "clean_shutdown_boot"
    private const val KEY_CLEAN_BOOT_ID = "clean_shutdown_boot_id"
    private const val KEY_CLEAN_AT = "clean_shutdown_elapsed"
    private const val KEY_PENDING_BOOT = "pending_boot"

    enum class Pending { NONE, PRESENT, CORRUPT }

    private fun prefs(ctx: Context) =
        ctx.createDeviceProtectedStorageContext().getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** A mistyped value reads as -1: never the same boot, so a pending countdown still wipes; nothing else is decided. */
    fun watchedBoot(ctx: Context): Int = when (val v = prefs(ctx).all[KEY_WATCHED_BOOT]) {
        null -> 0
        is Int -> v
        else -> -1
    }

    /** The boot_id of the watched boot, null when absent, not paired with the watched count, or unreadable. */
    fun watchedBootId(ctx: Context): String? = runCatching {
        val all = prefs(ctx).all
        PairedBootId.decode(all[KEY_WATCHED_BOOT_ID], all[KEY_WATCHED_BOOT] as? Int)
    }.getOrNull()

    /** The watched boot and its boot_id, one commit; an id that cannot be paired is removed, never left behind. */
    fun setWatchedBoot(ctx: Context, w: WatchedBoot): Boolean {
        val e = prefs(ctx).edit().putInt(KEY_WATCHED_BOOT, w.boot)
        val v = PairedBootId.encode(w.boot, w.bootId)
        if (v != null) e.putString(KEY_WATCHED_BOOT_ID, v) else e.remove(KEY_WATCHED_BOOT_ID)
        return e.commit()
    }

    /** The boot of the last `ACTION_SHUTDOWN`, 0 when none or mistyped (then it vouches for nothing). */
    fun cleanBoot(ctx: Context): Int = prefs(ctx).all[KEY_CLEAN_BOOT] as? Int ?: 0

    fun cleanBootId(ctx: Context): String? = runCatching {
        val all = prefs(ctx).all
        PairedBootId.decode(all[KEY_CLEAN_BOOT_ID], all[KEY_CLEAN_BOOT] as? Int)
    }.getOrNull()

    /** From the `ACTION_SHUTDOWN` receiver: the boot, its boot_id and when, one synchronous commit. */
    fun setCleanBoot(ctx: Context, boot: Int, bootId: String?, atElapsedMs: Long): Boolean {
        val e = prefs(ctx).edit().putInt(KEY_CLEAN_BOOT, boot).putLong(KEY_CLEAN_AT, atElapsedMs)
        val v = PairedBootId.encode(boot, bootId)
        if (v != null) e.putString(KEY_CLEAN_BOOT_ID, v) else e.remove(KEY_CLEAN_BOOT_ID)
        return e.commit()
    }

    /** Voids an `ACTION_SHUTDOWN` mark of the running kernel ([CleanMarkRule]). True when one was cleared. */
    fun voidCleanOfKernel(ctx: Context, runningBootId: String?, nowElapsedMs: Long, minAgeMs: Long): Boolean {
        val p = prefs(ctx)
        val all = p.all
        val id = PairedBootId.decode(all[KEY_CLEAN_BOOT_ID], all[KEY_CLEAN_BOOT] as? Int)
        if (!CleanMarkRule.void(id, all[KEY_CLEAN_AT] as? Long, runningBootId, nowElapsedMs, minAgeMs)) return false
        return p.edit().remove(KEY_CLEAN_BOOT).remove(KEY_CLEAN_BOOT_ID).remove(KEY_CLEAN_AT).commit()
    }

    /** The boot a countdown was started in, 0 when none is pending (and when the value is mistyped). */
    fun pendingBoot(ctx: Context): Int = prefs(ctx).all[KEY_PENDING_BOOT] as? Int ?: 0

    /** Whether a countdown is pending; a value that is not a boot count is [Pending.CORRUPT] and counts as pending. */
    fun pendingState(ctx: Context): Pending = when (val v = prefs(ctx).all[KEY_PENDING_BOOT]) {
        null, 0 -> Pending.NONE
        is Int -> Pending.PRESENT
        else -> Pending.CORRUPT
    }

    fun setPendingBoot(ctx: Context, boot: Int) = prefs(ctx).edit().putInt(KEY_PENDING_BOOT, boot).commit()

    /** Settles a pending countdown, a corrupt value included. */
    fun clearPending(ctx: Context) = prefs(ctx).edit().remove(KEY_PENDING_BOOT).commit()
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
                // With the kernel boot_id and the time (fix 1.3.6): the next boot can tell this kernel's mark from any
                // other, and a mark this kernel outlives (a shutdown that never finished) is voided.
                TamperBootAudit.bootCount(app)?.let {
                    RestartRecord.setCleanBoot(app, it, KernelBootId.read(), SystemClock.elapsedRealtime())
                }
            }
        }
        app.registerReceiver(r, IntentFilter(Intent.ACTION_SHUTDOWN))
        shutdownReceiver = r
    }

    fun stopWatch(ctx: Context) {
        shutdownReceiver?.let { runCatching { ctx.applicationContext.unregisterReceiver(it) } }
        shutdownReceiver = null
    }

    /** A countdown is pending, or its record cannot be read (which counts as pending). */
    fun pending(ctx: Context): Boolean = RestartRecord.pendingState(ctx) != RestartRecord.Pending.NONE

    /** Once per process: the first service start voids an `ACTION_SHUTDOWN` mark of the running kernel. */
    @Volatile private var voidedAtStart = false

    /** The kernel boot_ids of this start (fix 1.3.6): the watched boot's, the running one, and both marks'. */
    private fun kernelIds(ctx: Context, bootId: String?): KernelIds {
        val signal = ShutdownSignal.mark(ctx)
        return KernelIds(
            watched = RestartRecord.watchedBootId(ctx),
            current = bootId,
            cleanShutdown = RestartRecord.cleanBootId(ctx),
            cleanShutdownBoot = RestartRecord.cleanBoot(ctx).takeIf { it > 0 },
            signalMark = signal?.bootId,
            signalMarkBoot = signal?.boot,
        )
    }

    /** At every service start; acts once per boot. Must run before anything that could take long. */
    fun onServiceStart(ctx: Context) {
        val current = TamperBootAudit.bootCount(ctx) ?: return
        val bootId = KernelBootId.read()
        if (!voidedAtStart) {
            // Before anything reads it: Android came back in the kernel that wrote it, so that shutdown never finished.
            voidedAtStart = true
            if (RestartRecord.voidCleanOfKernel(ctx, bootId, SystemClock.elapsedRealtime(), minAgeMs = 0L)) {
                DebugTelemetry.log("c9 clean-shutdown mark of this kernel voided at start: no reboot followed it")
            }
        }
        val watched = RestartRecord.watchedBoot(ctx)
        val kernel = kernelIds(ctx, bootId)
        val enabled = ProtectPrefs.isTriggerEnabled(ctx, ForcedRestartTrigger.id)
        // The same boot: the service restarted, the phone did not. A new kernel never is, whatever the count says.
        if (current == watched && !kernel.newKernel()) {
            DebugTelemetry.log(StartLabel.of(watched, current, kernel, enabled, ForcedRestartRule.Action.NONE))
            tick(ctx)
            return
        }
        // Recorded whether C9 is armed or not, so arming it later has a baseline.
        RestartRecord.setWatchedBoot(ctx, WatchedBoot.record(current, bootId, watched, kernel))
        if (!enabled) {
            DebugTelemetry.log(StartLabel.of(watched, current, kernel, armed = false, ForcedRestartRule.Action.NONE))
            settle(ctx)
            return
        }
        // ACTION_SHUTDOWN seen by the service, or init's orderly reboot (GrapheneOS auto-reboot, fix 1.3.5).
        val signal = ShutdownSignal.vouchedBoot(ctx)
        val clean = CleanShutdownRule.cleanBoot(RestartRecord.cleanBoot(ctx), signal, current)
        val action = ForcedRestartRule.decide(watched, current, clean, pending(ctx), kernel)
        // Labels only: no boot count, no boot_id.
        DebugTelemetry.log(StartLabel.of(watched, current, kernel, armed = true, action))
        when (action) {
            // A userspace restart is told on the Timeline's boot entry ([TamperBootAudit.check]).
            ForcedRestartRule.Action.NONE -> Unit
            ForcedRestartRule.Action.WIPE -> {
                TamperLog.record(ctx, TamperKind.FORCED_RESTART, REPEAT_DETAIL)
                PanicHandler.panic(ctx, REPEAT_REASON)
                // Reached only in dry-run or after a failed wipe: this restart may have been forced too, so it is
                // judged again as if nothing had been pending.
                settle(ctx)
                if (ForcedRestartRule.decide(watched, current, clean, pending = false, kernel) == ForcedRestartRule.Action.COUNTDOWN) {
                    startCountdown(ctx, current)
                }
            }
            ForcedRestartRule.Action.COUNTDOWN -> {
                TamperLog.record(ctx, TamperKind.FORCED_RESTART, COUNTDOWN_DETAIL)
                startCountdown(ctx, current)
            }
        }
    }

    /**
     * The app was updated inside the boot it last watched (fix 1.3.6): the kernel boot_id goes next to that boot now,
     * so the first userspace restart after an update from 1.3.5 (no id stored) is already told apart. Touches nothing
     * when the boot is not the watched one or a paired id is there. From MY_PACKAGE_REPLACED only: at a plain start the
     * count may not have been raised yet for a new kernel. Never throws.
     */
    fun onAppUpdated(ctx: Context) {
        try {
            val current = TamperBootAudit.bootCount(ctx) ?: return
            if (current <= 0 || RestartRecord.watchedBoot(ctx) != current || RestartRecord.watchedBootId(ctx) != null) return
            val bootId = KernelBootId.read() ?: return
            if (RestartRecord.setWatchedBoot(ctx, WatchedBoot(current, bootId))) {
                DebugTelemetry.log("c9 watched boot paired with its kernel at the update")
            }
        } catch (e: Throwable) {
            DebugTelemetry.log("c9 watched boot not paired at the update: ${e.javaClass.simpleName}")
        }
    }

    private fun startCountdown(ctx: Context, boot: Int) {
        // Pending from this moment, even if the screen never opens.
        RestartRecord.setPendingBoot(ctx, boot)
        // The deadline is on this kernel's clock: a userspace restart keeps it (it wipes anyway), a reboot does not.
        ProtectPrefs.setCountdownDeadline(
            ctx, mode.extraValue, SystemClock.elapsedRealtime() + GRACE_SECONDS * 1_000L, ClockBoot.of(ctx),
        )
        DeadmanScheduler.schedule(ctx)
        launch(ctx)
    }

    /**
     * Service tick and dead-man alarm: an unlock settles a pending countdown; a screen that closed comes
     * back; past its deadline with no outcome (the screen never opened, or stalled) the phone is wiped.
     */
    fun tick(ctx: Context) {
        // A watcher that is gone is started again; a process that could not arm yet tries again.
        ShutdownSignal.ensure(ctx)
        // This process outlived its own kernel's clean-shutdown mark: that shutdown never finished (fix 1.3.6).
        runCatching {
            if (RestartRecord.voidCleanOfKernel(ctx, KernelBootId.read(), SystemClock.elapsedRealtime(), CleanMarkRule.MAX_AGE_MS)) {
                DebugTelemetry.log("c9 clean-shutdown mark of this kernel voided: no reboot followed it")
            }
        }
        if (!pending(ctx)) return
        if (!ProtectPrefs.isTriggerEnabled(ctx, ForcedRestartTrigger.id) || !lockedNow(ctx)) {
            settle(ctx)
            return
        }
        val deadline = ProtectPrefs.countdownDeadline(ctx, mode.extraValue)
        val boot = ClockBoot.of(ctx)
        if (overdue(deadline, ProtectPrefs.countdownBoot(ctx, mode.extraValue), SystemClock.elapsedRealtime(), boot)) {
            DebugTelemetry.bump(ctx, "c9_overdue_wipe")
            PanicHandler.panic(ctx, mode.reason)
            settle(ctx)
        } else {
            launch(ctx)
        }
    }

    internal fun overdue(deadline: Long, deadlineBoot: Int, nowElapsed: Long, bootCount: Int): Boolean =
        deadline > 0L && deadlineBoot == bootCount && nowElapsed > deadline + OVERDUE_AFTER_MS

    /** The phone locked right now; false once it is open, or when it has no screen lock at all. */
    private fun lockedNow(ctx: Context): Boolean =
        ctx.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true

    /** The countdown ended (unlocked, expired in dry-run, or C9 disarmed): nothing is owed any more. */
    fun settle(ctx: Context) {
        if (pending(ctx)) RestartRecord.clearPending(ctx)
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
        "The phone restarted without shutting down: a button held until it restarted, a kernel crash or a power " +
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
            "wipes at the next start. A kernel crash or a power loss looks the same, so the countdown starts then too. " +
            "A restart the system makes itself, such as GrapheneOS's auto-reboot, is a normal restart on current Android, " +
            "and so is Android restarting itself after a system crash with the phone still on. " +
            "Requires Device Owner."
    override val requiredTier = Tier.DeviceOwner

    override fun arm(context: Context) {
        ProtectPrefs.setTriggerEnabled(context, id, true)
        // This boot is watched from now; its own clean shutdown is what the next start looks for.
        TamperBootAudit.bootCount(context)?.let { boot ->
            val bootId = KernelBootId.read()
            val previous = RestartRecord.watchedBoot(context)
            RestartRecord.setWatchedBoot(
                context,
                WatchedBoot.record(boot, bootId, previous, KernelIds(watched = RestartRecord.watchedBootId(context), current = bootId)),
            )
        }
        ForcedRestartMonitor.startWatch(context)
        ShutdownSignal.arm(context)
    }

    override fun disarm(context: Context) {
        ProtectPrefs.setTriggerEnabled(context, id, false)
        ForcedRestartMonitor.settle(context)
    }
}
