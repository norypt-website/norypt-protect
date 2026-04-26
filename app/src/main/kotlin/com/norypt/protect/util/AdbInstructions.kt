package com.norypt.protect.util

import com.norypt.protect.BuildConfig

/**
 * Central source for the ADB commands shown in the upgrade-to-Device-Owner card.
 *
 * Uses [BuildConfig.APPLICATION_ID] so debug (`com.norypt.protect.debug`) and
 * release (`com.norypt.protect`) builds always show the matching package name.
 *
 * Preconditions for `dpm set-device-owner` to succeed:
 *  - No existing Device Owner on the device (`dpm list-owners` empty).
 *  - No accounts configured on user 0 (Google, Samsung, email, work, etc.).
 *  - No managed / work profile.
 *  - Only user 0 (no secondary users / guest sessions).
 */
object AdbInstructions {
    val PKG: String = BuildConfig.APPLICATION_ID
    val ADMIN_RECEIVER: String = "$PKG/com.norypt.protect.admin.ProtectAdminReceiver"

    /** Run first to confirm no other Device Owner is set — output must be empty. */
    val checkOwners: String = "adb shell dpm list-owners"

    val setDeviceOwner: String = "adb shell dpm set-device-owner $ADMIN_RECEIVER"

    val grantWriteSecureSettings: String =
        "adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS"
}
