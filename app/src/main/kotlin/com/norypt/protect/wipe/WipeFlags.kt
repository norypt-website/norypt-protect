package com.norypt.protect.wipe

import android.app.admin.DevicePolicyManager

object WipeFlags {
    fun build(options: WipeOptions): Int {
        var flags = 0
        if (options.wipeExternalStorage) flags = flags or DevicePolicyManager.WIPE_EXTERNAL_STORAGE
        if (options.wipeEuicc) flags = flags or DevicePolicyManager.WIPE_EUICC
        return flags
    }
}
