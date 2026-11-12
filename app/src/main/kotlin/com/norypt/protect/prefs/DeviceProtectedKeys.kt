package com.norypt.protect.prefs

/**
 * The settings and state kept in device-protected storage, which Android unlocks at boot, so the
 * triggers that can work before the first unlock after a restart can read them then. Everything
 * else stays in the encrypted file, readable only after the first unlock.
 *
 * Device-protected storage is encrypted with the phone's hardware-bound key, not with the screen
 * lock, so nothing secret belongs here: the App PIN, its lockout, the SMS code and the PanicKit
 * pairing stay encrypted ([NEVER_ROUTED] is checked by a test).
 */
internal object DeviceProtectedKeys {

    const val FILE = "norypt_device_protected"

    /** Triggers that run before the first unlock; their switches are routed. */
    val TRIGGER_IDS: Set<String> = setOf("A9", "A11", "B1", "B4", "C3", "C4", "C6")

    private val EXACT: Set<String> = setOf(
        // B1 / A11 wrong-PIN wipe
        ProtectPrefsKeys.KEY_MAX_FAILED_ATTEMPTS,
        ProtectPrefsKeys.KEY_DURESS_THRESHOLD,
        ProtectPrefsKeys.KEY_FAILED_ATTEMPTS,
        ProtectPrefsKeys.KEY_FAILED_ATTEMPT_LAST_MS,
        // The wipe itself
        ProtectPrefsKeys.KEY_DRY_RUN,
        ProtectPrefsKeys.KEY_WIPE_EXTERNAL_STORAGE,
        ProtectPrefsKeys.KEY_WIPE_EUICC,
        // C4 and C6 measure from the last unlock
        ProtectPrefsKeys.KEY_LAST_UNLOCK_MS,
        // Entries recorded before the first unlock are queued only while the Timeline is on
        ProtectPrefsKeys.KEY_TIMELINE_ENABLED,
    )

    private val PREFIXES: List<String> = listOf(
        "pending_wipe_",        // the queued-wipe record and its cancel generation
        "deadman_",             // C4 settings and snooze
        "unlock_deadline_",     // C6 settings and stamps
        "countdown_deadline_",  // every open countdown
        "countdown_boot_",
        "protection_start_",    // when monitoring first started in this boot (Timeline boot entry)
    )

    /** Keys that must never leave the encrypted file. The PIN keys are private to AppPin. */
    val NEVER_ROUTED: Set<String> = setOf(
        "pin_salt", "pin_hash",
        ProtectPrefsKeys.KEY_GATE_ATTEMPTS,
        ProtectPrefsKeys.KEY_GATE_LOCKED_UNTIL_MS,
        ProtectPrefsKeys.KEY_GATE_LOCKED_UNTIL_ELAPSED_MS,
        ProtectPrefsKeys.KEY_SMS_SECRET_CODE,
        ProtectPrefsKeys.KEY_PANIC_TRIGGER_PACKAGE,
        ProtectPrefsKeys.KEY_PANIC_TRIGGER_CERT,
        ProtectPrefsKeys.KEY_FAKE_MESSENGER_PACKAGE,
    )

    fun routed(key: String): Boolean {
        if (key in EXACT) return true
        if (key.startsWith(ProtectPrefsKeys.KEY_TRIGGER_ENABLED_PREFIX)) {
            return key.removePrefix(ProtectPrefsKeys.KEY_TRIGGER_ENABLED_PREFIX) in TRIGGER_IDS
        }
        return PREFIXES.any { key.startsWith(it) }
    }
}
