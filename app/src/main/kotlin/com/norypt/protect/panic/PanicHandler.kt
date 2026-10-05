package com.norypt.protect.panic

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.norypt.protect.R
import com.norypt.protect.prefs.KvStore
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.prefs.ProtectPrefsKeys
import com.norypt.protect.timeline.TamperKind
import com.norypt.protect.timeline.TamperLog
import com.norypt.protect.util.DebugTelemetry
import com.norypt.protect.wipe.WipeEngine
import com.norypt.protect.wipe.WipeError
import com.norypt.protect.wipe.WipeOptions
import com.norypt.protect.service.NotificationIds

object PanicHandler {

    /**
     * Single entry point for every panic trigger. Reads dry-run + wipe-options
     * flags from [ProtectPrefs] and routes to [WipeEngine] (or [wipeFn] in tests).
     *
     * @param wipeFn Injectable wipe function for unit testing. Defaults to [WipeEngine.wipe].
     * @param dryRunOverride the dry-run setting to use instead of the current one: a retry passes
     *   the one its wipe was triggered with.
     */
    fun panic(
        context: Context,
        reason: String,
        logEvent: Boolean = true,
        dryRunOverride: Boolean? = null,
        wipeFn: (Context, String, WipeOptions, Boolean) -> WipeError? =
            { c, r, o, d -> WipeEngine.wipe(c, r, o, d) },
    ): WipeError? {
        DebugTelemetry.bumpAll(context, "panic_total", "panic_$reason")

        // Read before the attempt: a cancel by the owner while it runs must not be undone by it.
        val startGen = cancelGeneration(ProtectPrefs.store(context))
        val opts = WipeOptions(
            wipeExternalStorage = ProtectPrefs.wipeExternalStorage(context),
            wipeEuicc = ProtectPrefs.wipeEuicc(context),
        )
        val dryRun = dryRunOverride ?: ProtectPrefs.dryRun(context)
        // Only ever readable after a dry run or a denied wipe: a real wipe takes the log with it.
        // TamperLog never throws, so the opt-in log cannot stand between a trigger and its wipe.
        // Retries are not logged again: one queued wipe is one entry, not one per 30 s tick.
        if (logEvent) {
            TamperLog.record(
                context,
                TamperKind.WIPE_TRIGGERED,
                "Trigger \"$reason\"" + if (dryRun) " (dry-run: nothing erased)." else ".",
            )
        }
        // Anything thrown is a wipe that did not happen and must stay queued, never a crash
        // that silently drops it.
        val error = try {
            wipeFn(context, reason, opts, dryRun)
        } catch (t: Throwable) {
            WipeError.Unknown("${t.javaClass.simpleName}: ${t.message}")
        }

        // A real wipe never returns — the process dies mid-call. Reaching here with a
        // non-null error means the device still holds all its data while the user believes
        // it was destroyed. That is the worst outcome this app has, so it is made loud and
        // retried rather than recorded and forgotten.
        val store = ProtectPrefs.store(context)
        val cleared = recordOutcome(store, outcomeOf(reason, error), dryRun, System.currentTimeMillis(), startGen)
        val stillQueued = ProtectPrefsKeys.pendingWipeReason(store) != null
        when (alertAfter(error, cleared, stillQueued)) {
            WipeAlert.NONE -> Unit
            WipeAlert.CANCEL -> cancelWipeFailed(context)
            // Names the wipe still queued, which may be an earlier one than this attempt (real wins).
            WipeAlert.RETRYING -> if (error != null) {
                notifyWipeFailed(context, ProtectPrefsKeys.pendingWipeReason(store) ?: reason, error)
            }
            WipeAlert.REFUSED -> {
                cancelWipeFailed(context)
                notifyWipeRefused(context, reason)
            }
        }
        return error
    }

    /** What happens to the WIPE FAILED alert after an attempt. */
    internal enum class WipeAlert { NONE, CANCEL, RETRYING, REFUSED }

    /**
     * A queue that was actually cleared takes the ongoing "Retrying automatically" alert down; an
     * attempt that cleared nothing leaves whatever is shown, so a later unrelated success never
     * removes the "nothing will retry" alert. A wipe Android refuses outright gets that one-off
     * alert unless a real wipe is still queued (a dry-run next to it), whose alert stays. A failure
     * that left nothing queued (the owner cancelled while it ran) raises nothing.
     */
    internal fun alertAfter(error: WipeError?, cleared: Boolean, stillQueued: Boolean): WipeAlert = when {
        error == WipeError.NotPermitted -> if (stillQueued) WipeAlert.NONE else WipeAlert.REFUSED
        error != null -> if (stillQueued) WipeAlert.RETRYING else WipeAlert.NONE
        cleared -> WipeAlert.CANCEL
        else -> WipeAlert.NONE
    }

    /**
     * Stores what [outcome] leaves queued. Returns true when a queued wipe was cleared (the attempt
     * succeeded, or Android refused a wipe it can never allow), so the caller removes a WIPE FAILED
     * alert left by an earlier attempt. With nothing queued before, it returns false. A dry-run
     * that succeeds does not settle a real wipe still queued: that one is still owed.
     *
     * Real wins: once a real wipe is queued, a later failed attempt changes nothing, so a dry-run
     * can never downgrade it and its reason and retry window stay those of the first trigger. A
     * failed real attempt replaces a queued dry-run and starts its own window.
     */
    internal fun recordOutcome(
        store: KvStore,
        outcome: PanicOutcome,
        dryRun: Boolean,
        nowMs: Long,
        startGen: Long? = null,
    ): Boolean {
        val queuedReason = ProtectPrefsKeys.pendingWipeReason(store)
        val realWipeQueued = queuedReason != null && !ProtectPrefsKeys.pendingWipeDryRun(store)
        if (outcome.pendingReason == null) {
            if (dryRun && realWipeQueued) return false
            clearPending(store)
            return queuedReason != null
        }
        // The owner cancelled the queue while this attempt ran ([startGen] is from before it).
        if (startGen != null && startGen != cancelGeneration(store)) return false
        // Something is queued already and this attempt is no upgrade of a dry-run to a real wipe.
        if (queuedReason != null && (realWipeQueued || dryRun)) return false
        ProtectPrefsKeys.setPendingWipeReason(store, outcome.pendingReason)
        // Snapshot of the dry-run setting: switching dry-run while the wipe is queued must
        // neither turn it into a real wipe nor quietly settle it with a test broadcast.
        ProtectPrefsKeys.setPendingWipeDryRun(store, dryRun)
        // Stamped when first queued, so the retry window measures from the original trigger
        // rather than sliding forward with every failed attempt.
        ProtectPrefsKeys.setPendingWipeAtMs(store, nowMs)
        return false
    }

    internal fun cancelGeneration(store: KvStore): Long = ProtectPrefsKeys.pendingWipeCancelGen(store)

    /**
     * The owner's cancel: drops the queue and bumps the cancel generation so an attempt in flight
     * cannot re-queue it. Returns whether a wipe was queued.
     */
    internal fun cancelPending(store: KvStore): Boolean {
        val had = ProtectPrefsKeys.pendingWipeReason(store) != null
        ProtectPrefsKeys.setPendingWipeCancelGen(store, cancelGeneration(store) + 1)
        clearPending(store)
        return had
    }

    /** Gives the queued wipe up after its retry window; returns its reason, or null if none was queued. */
    internal fun abandonPending(store: KvStore): String? {
        val reason = ProtectPrefsKeys.pendingWipeReason(store) ?: return null
        clearPending(store)
        return reason
    }

    /** Drops the queued wipe, if any. */
    internal fun clearPending(store: KvStore) {
        ProtectPrefsKeys.setPendingWipeReason(store, null)
        ProtectPrefsKeys.setPendingWipeAtMs(store, 0L)
        ProtectPrefsKeys.setPendingWipeDryRun(store, false)
    }

    /** What must follow a wipe attempt. Split out so the invariant is unit-testable. */
    internal data class PanicOutcome(val pendingReason: String?, val alertUser: Boolean)

    /**
     * Only a `null` result means the wipe is not outstanding. Every [WipeError] — including
     * [WipeError.ReturnedWithoutWiping], where the platform call returned instead of tearing
     * the process down — leaves the device holding its data and must stay pending.
     */
    internal fun outcomeOf(reason: String, error: WipeError?): PanicOutcome = when (error) {
        null -> PanicOutcome(null, alertUser = false)
        // Retrying cannot change the admin level, so it is reported once instead.
        WipeError.NotPermitted -> PanicOutcome(null, alertUser = true)
        else -> PanicOutcome(reason, alertUser = true)
    }

    /**
     * Re-attempts a wipe that previously failed. Driven from the foreground-service tick,
     * so a denial that was transient (the admin being re-granted, a policy lifted) still
     * results in the wipe the user asked for.
     */
    fun retryPendingWipe(context: Context) {
        when (val action = retryAction(ProtectPrefs.store(context), System.currentTimeMillis())) {
            Retry.None -> Unit
            Retry.Abandon -> {
                // Give up rather than fire later. The alert that promised a retry is replaced by
                // one that says it is over, and the Timeline keeps a note of it.
                DebugTelemetry.bump(context, "wipe_retry_abandoned")
                val reason = abandonPending(ProtectPrefs.store(context)) ?: return
                notifyWipeAbandoned(context, reason)
                TamperLog.record(
                    context,
                    TamperKind.WIPE_QUEUE_ABANDONED,
                    "Trigger \"$reason\": the wipe could not be completed within an hour; nothing will retry.",
                )
            }
            is Retry.Run -> {
                DebugTelemetry.bump(context, "wipe_retry_attempts")
                panic(context, action.reason, logEvent = false, dryRunOverride = action.dryRun)
            }
        }
    }

    /** What the retry tick does with the queue. */
    internal sealed interface Retry {
        data object None : Retry
        data object Abandon : Retry
        data class Run(val reason: String, val dryRun: Boolean) : Retry
    }

    internal fun retryAction(store: KvStore, nowMs: Long): Retry {
        val reason = ProtectPrefsKeys.pendingWipeReason(store) ?: return Retry.None
        if (!shouldRetry(ProtectPrefsKeys.pendingWipeAtMs(store), nowMs)) return Retry.Abandon
        return Retry.Run(reason, ProtectPrefsKeys.pendingWipeDryRun(store))
    }

    /**
     * How long a denied wipe stays queued for retry.
     *
     * Unbounded retry is a false positive waiting to happen: a wipe denied today because
     * the app was not yet Device Owner would fire the moment it became one, days later,
     * for a trigger the user has long forgotten. Bounding it keeps the retry useful for
     * the transient denial it is meant to cover — a policy briefly in the way, a
     * re-granted admin — without leaving an armed wipe lying around indefinitely.
     */
    const val RETRY_WINDOW_MS = 60 * 60_000L

    internal fun shouldRetry(queuedAtMs: Long, nowMs: Long): Boolean {
        if (queuedAtMs <= 0L) return true // never stamped; treat as fresh
        val age = nowMs - queuedAtMs
        // A backwards clock step must not resurrect an expired queue entry either.
        if (age < 0L) return false
        return age <= RETRY_WINDOW_MS
    }

    /**
     * The owner cancels a queued wipe (Wipe tab, behind the App PIN): the queue, its snapshot and
     * the WIPE FAILED alert go, and the Timeline keeps a note of it.
     */
    fun cancelQueuedWipe(context: Context): Boolean {
        if (!cancelPending(ProtectPrefs.store(context))) return false
        cancelWipeFailed(context)
        TamperLog.record(context, TamperKind.WIPE_QUEUE_CANCELLED, "Queued wipe cancelled with the App PIN.")
        return true
    }

    private fun cancelWipeFailed(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(NotificationIds.WIPE_FAILED) }
    }

    private fun notifyWipeFailed(context: Context, reason: String, error: WipeError) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val notif = Notification.Builder(context, "alerts")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Norypt Protect: WIPE FAILED")
                // Never on the lock screen: it names the trigger and says the data is intact.
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setContentText("Trigger \"$reason\" fired but the device was NOT wiped.")
                .setStyle(
                    Notification.BigTextStyle().bigText(
                        "Trigger \"$reason\" fired but the device was NOT wiped: ${error.message}. " +
                            "Your data is still on this device. Retrying automatically.",
                    ),
                )
                .setOngoing(true)
                .build()
            // Fixed id so retries replace the alert instead of stacking one per attempt.
            nm.notify(NotificationIds.WIPE_FAILED, notif)
        }
    }

    /** Dismissible, replaces the retrying alert: the hour is over and nothing will try again. */
    private fun notifyWipeAbandoned(context: Context, reason: String) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val notif = Notification.Builder(context, "alerts")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Norypt Protect: WIPE FAILED")
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setContentText("The wipe could not be completed; nothing will retry.")
                .setStyle(
                    Notification.BigTextStyle().bigText(
                        "Trigger \"$reason\" fired but the wipe could not be completed within an hour. " +
                            "Your data is still on this device and nothing will retry the wipe.",
                    ),
                )
                .build()
            nm.notify(NotificationIds.WIPE_FAILED, notif)
        }
    }

    /** One-off and dismissible: Android refused the wipe and nothing will try it again. */
    private fun notifyWipeRefused(context: Context, reason: String) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val notif = Notification.Builder(context, "alerts")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Norypt Protect: WIPE FAILED")
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setContentText("Trigger \"$reason\" fired but Android refused the wipe.")
                .setStyle(
                    Notification.BigTextStyle().bigText(
                        "Trigger \"$reason\" fired but Android refused the wipe: ${WipeError.NotPermitted.message}. " +
                            "Your data is still on this device and nothing will retry the wipe.",
                    ),
                )
                .build()
            nm.notify(NotificationIds.WIPE_FAILED, notif)
        }
    }


    /**
     * Internal pure-logic overload: takes all values already resolved from prefs.
     * Used by unit tests to verify routing behaviour without a real [Context].
     */
    internal fun panicWith(
        reason: String,
        dryRun: Boolean,
        options: WipeOptions,
        wipeFn: (String, WipeOptions, Boolean) -> Unit,
    ) {
        wipeFn(reason, options, dryRun)
    }
}
