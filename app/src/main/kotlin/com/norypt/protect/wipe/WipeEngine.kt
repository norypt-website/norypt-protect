package com.norypt.protect.wipe

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build

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
        val sp = context.getSharedPreferences("norypt_admin_debug", Context.MODE_PRIVATE)
        sp.edit().putString("wipe_last_call", "pre-call flags=$flags reason=$reason sdk=${Build.VERSION.SDK_INT}").commit()
        return try {
            // Android 14+ split the wipe APIs: wipeData() now removes only the calling
            // user (IllegalStateException on user 0, the system user), while the new
            // wipeDevice() is the canonical full-factory-reset call for Device Owners.
            if (Build.VERSION.SDK_INT >= 34) {
                dpm.wipeDevice(flags)
            } else {
                dpm.wipeData(flags)
            }
            sp.edit().putString("wipe_last_call", "returned-without-wiping flags=$flags reason=$reason").commit()
            null // unreachable on a real wipe
        } catch (e: SecurityException) {
            sp.edit().putString("wipe_last_call", "SecurityException: ${e.message}").commit()
            WipeError.SecurityDenied(e.message ?: "SecurityException")
        } catch (e: IllegalStateException) {
            sp.edit().putString("wipe_last_call", "IllegalStateException: ${e.message}").commit()
            WipeError.IllegalState(e.message ?: "IllegalStateException")
        } catch (t: Throwable) {
            sp.edit().putString("wipe_last_call", "Throwable: ${t.javaClass.simpleName}: ${t.message}").commit()
            null
        }
    }
}

sealed class WipeError(val message: String) {
    class SecurityDenied(message: String) : WipeError(message)
    class IllegalState(message: String) : WipeError(message)
}
