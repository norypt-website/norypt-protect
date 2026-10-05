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
        val cleared = recordOutcome(store, outcomeOf(reason, error), dryRun, System.currentTimeMillis())
        when (alertAfter(error, cleared)) {
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
     * Any cleared queue takes the ongoing "Retrying automatically" alert down. A wipe Android
     * refuses outright clears the queue and gets its own one-off alert that promises no retry.
     * A refusal that cleared nothing (a dry-run next to a queued real wipe) leaves the alert alone.
     */
    internal fun alertAfter(error: WipeError?, cleared: Boolean): WipeAlert = when {
        error == WipeError.NotPermitted -> if (cleared) WipeAlert.REFUSED else WipeAlert.NONE
        error != null -> WipeAlert.RETRYING
        cleared -> WipeAlert.CANCEL
        else -> WipeAlert.NONE
    }

    /**
     * Stores what [outcome] leaves queued. Returns true when the queue was cleared (the attempt
     * succeeded, or Android refused a wipe it can never allow), so the caller removes a WIPE FAILED
     * alert left by an earlier attempt. A dry-run that succeeds does not settle a real wipe still
     * queued: that one is still owed.
     *
     * Real wins: once a real wipe is queued, a later failed attempt changes nothing, so a dry-run
     * can never downgrade it and its reason and retry window stay those of the first trigger. A
     * failed real attempt replaces a queued dry-run and starts its own window.
     */
    internal fun recordOutcome(store: KvStore, outcome: PanicOutcome, dryRun: Boolean, nowMs: Long): Boolean {
        val queuedReason = ProtectPrefsKeys.pendingWipeReason(store)
        val realWipeQueued = queuedReason != null && !ProtectPrefsKeys.pendingWipeDryRun(store)
        if (outcome.pendingReason == null) {
            if (dryRun && realWipeQueued) return false
            clearPending(store)
            return true
        }
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
                // Give up rather than fire later, and take down the alert that promised a retry.
                DebugTelemetry.bump(context, "wipe_retry_abandoned")
                clearPending(ProtectPrefs.store(context))
                cancelWipeFailed(context)
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
