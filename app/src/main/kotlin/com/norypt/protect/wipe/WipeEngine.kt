package com.norypt.protect.wipe

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent

object WipeEngine {

    const val ACTION_DRY_RUN = "com.norypt.protect.action.WIPED_DRYRUN"
    const val EXTRA_REASON = "reason"
    const val EXTRA_FLAGS = "flags"

    /**
     * Wipe the device using [options]. If [dryRun] is true, broadcasts
     * [ACTION_DRY_RUN] locally instead of invoking wipeData — used by the
     * instrumented smoke test in Task 15 and by A13 dry-run mode in Plan 2.
     *
     * Returns `null` on real wipe success (process dies), or a [WipeError]
     * on failure. In dry-run mode always returns null after broadcasting.
     */
    fun wipe(context: Context, reason: String, options: WipeOptions = WipeOptions(), dryRun: Boolean = false): WipeError? {
        val flags = WipeFlags.build(options)
        if (dryRun) {
            val intent = Intent(ACTION_DRY_RUN)
                .setPackage(context.packageName)
                .putExtra(EXTRA_REASON, reason)
                .putExtra(EXTRA_FLAGS, flags)
            context.sendBroadcast(intent)
            return null
        }
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return try {
            dpm.wipeData(flags)
            null // unreachable on a real wipe
        } catch (e: SecurityException) {
            WipeError.SecurityDenied(e.message ?: "SecurityException")
        } catch (e: IllegalStateException) {
            WipeError.IllegalState(e.message ?: "IllegalStateException")
        }
    }
}

sealed class WipeError(val message: String) {
    class SecurityDenied(message: String) : WipeError(message)
    class IllegalState(message: String) : WipeError(message)
}
