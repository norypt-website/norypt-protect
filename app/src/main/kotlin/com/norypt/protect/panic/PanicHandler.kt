package com.norypt.protect.panic

import android.content.Context
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.wipe.WipeEngine
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
        wipeFn: (Context, String, WipeOptions, Boolean) -> Unit =
            { c, r, o, d -> WipeEngine.wipe(c, r, o, d) },
    ) {
        val opts = WipeOptions(
            wipeExternalStorage = ProtectPrefs.wipeExternalStorage(context),
            wipeEuicc = ProtectPrefs.wipeEuicc(context),
        )
        val dryRun = ProtectPrefs.dryRun(context)
        wipeFn(context, reason, opts, dryRun)
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
