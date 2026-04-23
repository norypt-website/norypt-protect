package com.norypt.protect.wipe

import android.app.admin.DevicePolicyManager

object WipeFlags {
    fun build(options: WipeOptions): Int {
        // WIPE_SILENTLY suppresses the user-confirmation dialog that Android 14+
        // would otherwise show. Without it, GrapheneOS may drop the wipe because
        // the dialog never renders (no UI → no confirmation → silent no-op).
        var flags = DevicePolicyManager.WIPE_SILENTLY
        if (options.wipeExternalStorage) flags = flags or DevicePolicyManager.WIPE_EXTERNAL_STORAGE
        if (options.wipeEuicc) flags = flags or DevicePolicyManager.WIPE_EUICC
        return flags
    }
}
