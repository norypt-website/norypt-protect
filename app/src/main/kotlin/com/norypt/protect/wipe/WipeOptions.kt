package com.norypt.protect.wipe

data class WipeOptions(
    /** Internal storage wipe is always performed and cannot be disabled. */
    val wipeExternalStorage: Boolean = true,
    val wipeEuicc: Boolean = true,
)
