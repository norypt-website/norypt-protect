package com.norypt.protect.panic

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.norypt.protect.R
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.util.DebugTelemetry
import com.norypt.protect.wipe.WipeEngine
import com.norypt.protect.wipe.WipeError
import com.norypt.protect.wipe.WipeOptions

object PanicHandler {

    /**
     * Single entry point for every panic trigger. Reads dry-run + wipe-options
     * flags from [ProtectPrefs] and routes to [WipeEngine] (or [wipeFn] in tests).
     *
     * @param wipeFn Injectable wipe function for unit testing. Defaults to [WipeEngine.wipe].
     */
    fun panic(
        context: Context,
        reason: String,
        wipeFn: (Context, String, WipeOptions, Boolean) -> WipeError? =
            { c, r, o, d -> WipeEngine.wipe(c, r, o, d) },
    ): WipeError? {
        DebugTelemetry.bumpAll(context, "panic_total", "panic_$reason")

        val opts = WipeOptions(
            wipeExternalStorage = ProtectPrefs.wipeExternalStorage(context),
            wipeEuicc = ProtectPrefs.wipeEuicc(context),
        )
        val dryRun = ProtectPrefs.dryRun(context)
        val error = wipeFn(context, reason, opts, dryRun)

        // A real wipe never returns — the process dies mid-call. Reaching here with a
        // non-null error means the device still holds all its data while the user believes
        // it was destroyed. That is the worst outcome this app has, so it is made loud and
        // retried rather than recorded and forgotten.
        val outcome = outcomeOf(reason, error)
        ProtectPrefs.setPendingWipeReason(context, outcome.pendingReason)
        if (outcome.pendingReason != null) {
            // Stamped only when first queued, so the retry window measures from the
            // original trigger rather than sliding forward with every failed attempt.
            if (ProtectPrefs.pendingWipeAtMs(context) == 0L) {
                ProtectPrefs.setPendingWipeAtMs(context, System.currentTimeMillis())
            }
        } else {
            ProtectPrefs.setPendingWipeAtMs(context, 0L)
        }
        if (outcome.alertUser && error != null) {
            notifyWipeFailed(context, reason, error)
        }
        return error
    }

    /** What must follow a wipe attempt. Split out so the invariant is unit-testable. */
    internal data class PanicOutcome(val pendingReason: String?, val alertUser: Boolean)

    /**
     * Only a `null` result means the wipe is not outstanding. Every [WipeError] — including
     * [WipeError.ReturnedWithoutWiping], where the platform call returned instead of tearing
     * the process down — leaves the device holding its data and must stay pending.
     */
    internal fun outcomeOf(reason: String, error: WipeError?): PanicOutcome =
        if (error == null) PanicOutcome(null, alertUser = false)
        else PanicOutcome(reason, alertUser = true)

    /**
     * Re-attempts a wipe that previously failed. Driven from the foreground-service tick,
     * so a denial that was transient (the admin being re-granted, a policy lifted) still
     * results in the wipe the user asked for.
     */
    fun retryPendingWipe(context: Context) {
        val reason = ProtectPrefs.pendingWipeReason(context) ?: return
        val queuedAt = ProtectPrefs.pendingWipeAtMs(context)
        if (!shouldRetry(queuedAt, System.currentTimeMillis())) {
            // Give up rather than fire later. The failure notification stays up, so the
            // user still knows the wipe did not happen.
            DebugTelemetry.bump(context, "wipe_retry_abandoned")
            ProtectPrefs.setPendingWipeReason(context, null)
            ProtectPrefs.setPendingWipeAtMs(context, 0L)
            return
        }
        DebugTelemetry.bump(context, "wipe_retry_attempts")
        panic(context, reason)
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

    private fun notifyWipeFailed(context: Context, reason: String, error: WipeError) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val notif = Notification.Builder(context, "alerts")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Norypt Protect: WIPE FAILED")
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
            nm.notify(NOTIF_ID_WIPE_FAILED, notif)
        }
    }

    private const val NOTIF_ID_WIPE_FAILED = 5002

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
