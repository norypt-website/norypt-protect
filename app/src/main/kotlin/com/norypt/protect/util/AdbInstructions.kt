package com.norypt.protect.util

/**
 * Central source for the ADB commands shown in the upgrade-to-Device-Owner card.
 *
 * Note: Device must have zero Google / manufacturer accounts before set-device-owner succeeds.
 */
object AdbInstructions {
    const val PKG = "com.norypt.protect"
    const val ADMIN_RECEIVER = "$PKG/com.norypt.protect.admin.ProtectAdminReceiver"

    val setDeviceOwner: String =
        "adb shell dpm set-device-owner $ADMIN_RECEIVER"

    val grantWriteSecureSettings: String =
        "adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS"
}
