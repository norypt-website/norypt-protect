package com.norypt.protect.util

import android.os.Build

object GrapheneDetect {

    fun isGrapheneOS(): Boolean {
        val signals = listOf(
            Build.FINGERPRINT,
            Build.HOST,
            Build.ID,
            Build.DISPLAY,
            Build.PRODUCT,
            Build.VERSION.INCREMENTAL,
        )
        return signals.any { it.contains("graphene", ignoreCase = true) }
    }

    fun adbCommand(packageName: String): String =
        "adb shell dpm set-active-admin --user 0 $packageName/com.norypt.protect.admin.ProtectAdminReceiver"
}
