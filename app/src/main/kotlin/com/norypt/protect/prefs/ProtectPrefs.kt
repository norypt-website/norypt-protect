package com.norypt.protect.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.norypt.protect.security.KeystoreHelper

/**
 * Internal key-value abstraction that routes ProtectPrefs through a testable interface.
 * Production code uses EncryptedSharedPreferences; tests inject an in-memory HashMap store.
 */
internal interface KvStore {
    fun getString(key: String, default: String?): String?
    fun getInt(key: String, default: Int): Int
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getLong(key: String, default: Long): Long
    fun putString(key: String, value: String?)
    fun putInt(key: String, value: Int)
    fun putBoolean(key: String, value: Boolean)
    fun putLong(key: String, value: Long)
}

/** Key constants and pure typed accessors — exercised directly by unit tests. */
internal object ProtectPrefsKeys {
    const val KEY_TRIGGER_ENABLED_PREFIX = "trigger_enabled_"
    const val KEY_MAX_FAILED_ATTEMPTS = "max_failed_attempts"
    const val KEY_MAX_UNLOCKED_MINUTES = "max_unlocked_minutes"
    const val KEY_SMS_SECRET_CODE = "sms_secret_code"
    const val KEY_FAKE_MESSENGER_PACKAGE = "fake_messenger_package"
    const val KEY_DRY_RUN = "dry_run"
    const val KEY_WIPE_EXTERNAL_STORAGE = "wipe_external_storage"
    const val KEY_WIPE_EUICC = "wipe_euicc"
    const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    const val KEY_FAILED_ATTEMPT_LAST_MS = "failed_attempt_last_ms"
    const val KEY_GATE_ATTEMPTS = "gate_attempts"
    const val KEY_GATE_LOCKED_UNTIL_MS = "gate_locked_until_ms"
    const val KEY_GATE_LOCKED_UNTIL_ELAPSED_MS = "gate_locked_until_elapsed_ms"
    const val KEY_PENDING_WIPE_REASON = "pending_wipe_reason"
    const val KEY_PENDING_WIPE_AT_MS = "pending_wipe_at_ms"
    const val KEY_PANIC_TRIGGER_PACKAGE = "panic_trigger_package"
    const val KEY_LAST_UNLOCK_MS = "last_unlock_ms"
    const val KEY_DURESS_THRESHOLD = "duress_threshold"
    const val KEY_ANTI_TAMPER_ENABLED = "anti_tamper_enabled"
    const val KEY_LAUNCHER_HIDDEN = "launcher_hidden"
    const val KEY_SOS_DISABLED_ON_PROMOTION = "sos_disabled_on_promotion"
    const val KEY_SOS_LAST_INTENT = "sos_last_intent_value"

    // C4 Dead-man switch keys
    const val KEY_DEADMAN_BATTERY_PCT = "deadman_battery_pct"
    const val KEY_DEADMAN_GRACE_SECONDS = "deadman_grace_seconds"
    const val KEY_DEADMAN_REQUIRE_BT = "deadman_require_bt"
    const val KEY_DEADMAN_REQUIRE_GSM = "deadman_require_gsm"
    const val KEY_DEADMAN_REQUIRE_WIFI = "deadman_require_wifi"
    const val KEY_DEADMAN_DISARM_MINUTES_AFTER_UNLOCK = "deadman_disarm_minutes_after_unlock"

    // B5 Package internet watcher
    const val KEY_KNOWN_INTERNET_PACKAGES = "known_internet_packages"

    // Launch gate
    const val KEY_LAUNCH_BIOMETRIC_ENABLED = "launch_biometric_enabled"

    // Power-menu guard (Device Owner)
    const val KEY_POWER_MENU_BLOCK_WHEN_LOCKED = "power_menu_block_when_locked"

    fun powerMenuBlockWhenLocked(store: KvStore): Boolean =
        store.getBoolean(KEY_POWER_MENU_BLOCK_WHEN_LOCKED, false)

    fun setPowerMenuBlockWhenLocked(store: KvStore, value: Boolean) =
        store.putBoolean(KEY_POWER_MENU_BLOCK_WHEN_LOCKED, value)

    fun isTriggerEnabled(store: KvStore, id: String, default: Boolean): Boolean =
        store.getBoolean(KEY_TRIGGER_ENABLED_PREFIX + id, default)

    fun setTriggerEnabled(store: KvStore, id: String, enabled: Boolean) =
        store.putBoolean(KEY_TRIGGER_ENABLED_PREFIX + id, enabled)

    fun maxFailedAttempts(store: KvStore): Int =
        store.getInt(KEY_MAX_FAILED_ATTEMPTS, 10)

    fun setMaxFailedAttempts(store: KvStore, value: Int) =
        store.putInt(KEY_MAX_FAILED_ATTEMPTS, value)

    fun maxUnlockedMinutes(store: KvStore): Int =
        store.getInt(KEY_MAX_UNLOCKED_MINUTES, 360)

    fun setMaxUnlockedMinutes(store: KvStore, value: Int) =
        store.putInt(KEY_MAX_UNLOCKED_MINUTES, value)

    fun smsSecretCode(store: KvStore): String? =
        store.getString(KEY_SMS_SECRET_CODE, null)

    fun setSmsSecretCode(store: KvStore, value: String?) =
        store.putString(KEY_SMS_SECRET_CODE, value)

    fun fakeMessengerPackage(store: KvStore): String? =
        store.getString(KEY_FAKE_MESSENGER_PACKAGE, null)

    fun setFakeMessengerPackage(store: KvStore, value: String?) =
        store.putString(KEY_FAKE_MESSENGER_PACKAGE, value)

    /**
     * Dry-run defaults to `true` on first install: any trigger that fires before
     * the user has explicitly disabled dry-run only broadcasts a test intent,
     * never a real factory reset. The Wipe-options screen flips this to `false`
     * once the user confirms they want real wipes.
     */
    fun dryRun(store: KvStore): Boolean =
        store.getBoolean(KEY_DRY_RUN, true)

    fun setDryRun(store: KvStore, value: Boolean) =
        store.putBoolean(KEY_DRY_RUN, value)

    fun wipeExternalStorage(store: KvStore): Boolean =
        store.getBoolean(KEY_WIPE_EXTERNAL_STORAGE, true)

    fun setWipeExternalStorage(store: KvStore, value: Boolean) =
        store.putBoolean(KEY_WIPE_EXTERNAL_STORAGE, value)

    fun wipeEuicc(store: KvStore): Boolean =
        store.getBoolean(KEY_WIPE_EUICC, true)

    fun setWipeEuicc(store: KvStore, value: Boolean) =
        store.putBoolean(KEY_WIPE_EUICC, value)

    fun failedAttempts(store: KvStore): Int =
        store.getInt(KEY_FAILED_ATTEMPTS, 0)

    /**
     * Failed unlocks further apart than this belong to different events and start a new
     * run. Without a window the counter is monotonic for the life of the install, so three
     * unrelated mistypes spread over months reach a duress threshold of 3 and wipe a device
     * that was never under coercion.
     */
    const val FAILED_ATTEMPT_WINDOW_MS = 15 * 60_000L

    /** Records a failed unlock at [nowMs] and returns the length of the current run. */
    fun recordFailedAttempt(store: KvStore, nowMs: Long): Int {
        val last = store.getLong(KEY_FAILED_ATTEMPT_LAST_MS, 0L)
        // nowMs < last means the clock stepped backwards; treat it as a fresh run rather
        // than extending one on timestamps we can no longer compare.
        val continuesRun = last > 0L && nowMs >= last && nowMs - last <= FAILED_ATTEMPT_WINDOW_MS
        val next = if (continuesRun) failedAttempts(store) + 1 else 1
        store.putInt(KEY_FAILED_ATTEMPTS, next)
        store.putLong(KEY_FAILED_ATTEMPT_LAST_MS, nowMs)
        return next
    }

    fun resetFailedAttempts(store: KvStore) {
        store.putInt(KEY_FAILED_ATTEMPTS, 0)
        store.putLong(KEY_FAILED_ATTEMPT_LAST_MS, 0L)
    }

    /** The single PanicKit trigger app the user paired; null when none. */
    fun panicTriggerPackage(store: KvStore): String? =
        store.getString(KEY_PANIC_TRIGGER_PACKAGE, null)

    fun setPanicTriggerPackage(store: KvStore, value: String?) =
        store.putString(KEY_PANIC_TRIGGER_PACKAGE, value)

    /** Reason of a wipe that was attempted and did not happen; null when none is pending. */
    fun pendingWipeReason(store: KvStore): String? =
        store.getString(KEY_PENDING_WIPE_REASON, null)

    fun setPendingWipeReason(store: KvStore, value: String?) =
        store.putString(KEY_PENDING_WIPE_REASON, value)

    fun pendingWipeAtMs(store: KvStore): Long =
        store.getLong(KEY_PENDING_WIPE_AT_MS, 0L)

    fun setPendingWipeAtMs(store: KvStore, value: Long) =
        store.putLong(KEY_PENDING_WIPE_AT_MS, value)

    fun gateAttempts(store: KvStore): Int =
        store.getInt(KEY_GATE_ATTEMPTS, 0)

    fun setGateAttempts(store: KvStore, value: Int) =
        store.putInt(KEY_GATE_ATTEMPTS, value)

    fun gateLockedUntilMs(store: KvStore): Long =
        store.getLong(KEY_GATE_LOCKED_UNTIL_MS, 0L)

    fun setGateLockedUntilMs(store: KvStore, value: Long) =
        store.putLong(KEY_GATE_LOCKED_UNTIL_MS, value)

    fun gateLockedUntilElapsedMs(store: KvStore): Long =
        store.getLong(KEY_GATE_LOCKED_UNTIL_ELAPSED_MS, 0L)

    fun setGateLockedUntilElapsedMs(store: KvStore, value: Long) =
        store.putLong(KEY_GATE_LOCKED_UNTIL_ELAPSED_MS, value)

    fun lastUnlockMs(store: KvStore): Long =
        store.getLong(KEY_LAST_UNLOCK_MS, 0L)

    fun setLastUnlockMs(store: KvStore, value: Long) =
        store.putLong(KEY_LAST_UNLOCK_MS, value)

    /** A11: duress panic threshold (0 = off). Wipe fires when failedAttempts reaches this value. */
    fun duressThreshold(store: KvStore): Int =
        store.getInt(KEY_DURESS_THRESHOLD, 0)

    fun setDuressThreshold(store: KvStore, value: Int) =
        store.putInt(KEY_DURESS_THRESHOLD, value)

    /** Whether the AntiTamper restrictions are currently applied. */
    fun antiTamperEnabled(store: KvStore): Boolean =
        store.getBoolean(KEY_ANTI_TAMPER_ENABLED, false)

    fun setAntiTamperEnabled(store: KvStore, value: Boolean) =
        store.putBoolean(KEY_ANTI_TAMPER_ENABLED, value)

    /** Whether the launcher icon has been hidden via LauncherAlias. */
    fun launcherHidden(store: KvStore): Boolean =
        store.getBoolean(KEY_LAUNCHER_HIDDEN, false)

    fun setLauncherHidden(store: KvStore, value: Boolean) =
        store.putBoolean(KEY_LAUNCHER_HIDDEN, value)

    /** True once EmergencySos was auto-disabled on DO promotion (prevents repeated attempts). */
    fun sosDisabledOnPromotion(store: KvStore): Boolean =
        store.getBoolean(KEY_SOS_DISABLED_ON_PROMOTION, false)

    fun setSosDisabledOnPromotion(store: KvStore, value: Boolean) =
        store.putBoolean(KEY_SOS_DISABLED_ON_PROMOTION, value)

    /** Local cache of the last SOS value we successfully wrote via the app. Used
     *  as UI state when Settings.Secure.getInt returns -1 (GrapheneOS scopes
     *  reads tighter than AOSP even with WRITE_SECURE_SETTINGS). -1 = unknown. */
    fun sosLastIntent(store: KvStore): Int =
        store.getInt(KEY_SOS_LAST_INTENT, -1)

    fun setSosLastIntent(store: KvStore, value: Int) =
        store.putInt(KEY_SOS_LAST_INTENT, value)

    // --- C4 Dead-man switch ---

    fun deadmanBatteryPct(store: KvStore): Int =
        store.getInt(KEY_DEADMAN_BATTERY_PCT, 5)

    fun setDeadmanBatteryPct(store: KvStore, value: Int) =
        store.putInt(KEY_DEADMAN_BATTERY_PCT, value)

    fun deadmanGraceSeconds(store: KvStore): Int =
        store.getInt(KEY_DEADMAN_GRACE_SECONDS, 60)

    fun setDeadmanGraceSeconds(store: KvStore, value: Int) =
        store.putInt(KEY_DEADMAN_GRACE_SECONDS, value)

    fun deadmanRequireBt(store: KvStore): Boolean =
        store.getBoolean(KEY_DEADMAN_REQUIRE_BT, true)

    fun setDeadmanRequireBt(store: KvStore, value: Boolean) =
        store.putBoolean(KEY_DEADMAN_REQUIRE_BT, value)

    fun deadmanRequireGsm(store: KvStore): Boolean =
        store.getBoolean(KEY_DEADMAN_REQUIRE_GSM, true)

    fun setDeadmanRequireGsm(store: KvStore, value: Boolean) =
        store.putBoolean(KEY_DEADMAN_REQUIRE_GSM, value)

    fun deadmanRequireWifi(store: KvStore): Boolean =
        store.getBoolean(KEY_DEADMAN_REQUIRE_WIFI, true)

    fun setDeadmanRequireWifi(store: KvStore, value: Boolean) =
        store.putBoolean(KEY_DEADMAN_REQUIRE_WIFI, value)

    fun deadmanDisarmMinutesAfterUnlock(store: KvStore): Int =
        store.getInt(KEY_DEADMAN_DISARM_MINUTES_AFTER_UNLOCK, 0)

    fun setDeadmanDisarmMinutesAfterUnlock(store: KvStore, value: Int) =
        store.putInt(KEY_DEADMAN_DISARM_MINUTES_AFTER_UNLOCK, value)

    // --- B5 Known internet packages ---

    fun knownInternetPackages(store: KvStore): Set<String> {
        val raw = store.getString(KEY_KNOWN_INTERNET_PACKAGES, null) ?: return emptySet()
        return raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    fun setKnownInternetPackages(store: KvStore, packages: Set<String>) =
        store.putString(KEY_KNOWN_INTERNET_PACKAGES, packages.joinToString(","))

    // --- Launch gate biometric preference ---
    fun launchBiometricEnabled(store: KvStore): Boolean =
        store.getBoolean(KEY_LAUNCH_BIOMETRIC_ENABLED, false)

    fun setLaunchBiometricEnabled(store: KvStore, value: Boolean) =
        store.putBoolean(KEY_LAUNCH_BIOMETRIC_ENABLED, value)

    // --- Tamper timeline ---
    //
    // Off by default: the app's public posture is "no logs", and the timeline is a log —
    // local, encrypted and owner-visible, but a log. It exists only once the owner turns
    // it on. The baseline keys below let the monitor report *changes* rather than states.
    const val KEY_TIMELINE_ENABLED = "timeline_enabled"
    const val KEY_TIMELINE_LAST_ALIVE_MS = "timeline_last_alive_ms"
    const val KEY_TIMELINE_LAST_BOOT_COUNT = "timeline_last_boot_count"
    const val KEY_TIMELINE_SIM_SNAPSHOT = "timeline_sim_snapshot"
    const val KEY_TIMELINE_ADB_ENABLED = "timeline_adb_enabled"
    const val KEY_TIMELINE_DEVICE_SECURE = "timeline_device_secure"
    const val KEY_TIMELINE_BIOMETRIC_STATUS = "timeline_biometric_status"
    const val KEY_TIMELINE_SENTINEL_ARMED = "timeline_sentinel_armed"
    const val KEY_TIMELINE_FAILED_ATTEMPTS_SEEN = "timeline_failed_attempts_seen"

    fun timelineEnabled(store: KvStore): Boolean = store.getBoolean(KEY_TIMELINE_ENABLED, false)
    fun setTimelineEnabled(store: KvStore, value: Boolean) = store.putBoolean(KEY_TIMELINE_ENABLED, value)

    /** Wall-clock time the monitoring service was last known to be running; 0 = never. */
    fun timelineLastAliveMs(store: KvStore): Long = store.getLong(KEY_TIMELINE_LAST_ALIVE_MS, 0L)
    fun setTimelineLastAliveMs(store: KvStore, value: Long) = store.putLong(KEY_TIMELINE_LAST_ALIVE_MS, value)

    /** Settings.Global.BOOT_COUNT as last observed; 0 = never observed. */
    fun timelineLastBootCount(store: KvStore): Int = store.getInt(KEY_TIMELINE_LAST_BOOT_COUNT, 0)
    fun setTimelineLastBootCount(store: KvStore, value: Int) = store.putInt(KEY_TIMELINE_LAST_BOOT_COUNT, value)

    fun timelineSimSnapshot(store: KvStore): String? = store.getString(KEY_TIMELINE_SIM_SNAPSHOT, null)
    fun setTimelineSimSnapshot(store: KvStore, value: String?) = store.putString(KEY_TIMELINE_SIM_SNAPSHOT, value)

    /** -1 = not yet observed, otherwise 0/1. Same convention for the two keys below. */
    fun timelineAdbEnabled(store: KvStore): Int = store.getInt(KEY_TIMELINE_ADB_ENABLED, -1)
    fun setTimelineAdbEnabled(store: KvStore, value: Int) = store.putInt(KEY_TIMELINE_ADB_ENABLED, value)

    fun timelineDeviceSecure(store: KvStore): Int = store.getInt(KEY_TIMELINE_DEVICE_SECURE, -1)
    fun setTimelineDeviceSecure(store: KvStore, value: Int) = store.putInt(KEY_TIMELINE_DEVICE_SECURE, value)

    /** Last BiometricManager.canAuthenticate result; Int.MIN_VALUE = not yet observed. */
    fun timelineBiometricStatus(store: KvStore): Int = store.getInt(KEY_TIMELINE_BIOMETRIC_STATUS, Int.MIN_VALUE)
    fun setTimelineBiometricStatus(store: KvStore, value: Int) = store.putInt(KEY_TIMELINE_BIOMETRIC_STATUS, value)

    fun timelineSentinelArmed(store: KvStore): Boolean = store.getBoolean(KEY_TIMELINE_SENTINEL_ARMED, false)
    fun setTimelineSentinelArmed(store: KvStore, value: Boolean) = store.putBoolean(KEY_TIMELINE_SENTINEL_ARMED, value)

    fun timelineFailedAttemptsSeen(store: KvStore): Int = store.getInt(KEY_TIMELINE_FAILED_ATTEMPTS_SEEN, 0)
    fun setTimelineFailedAttemptsSeen(store: KvStore, value: Int) = store.putInt(KEY_TIMELINE_FAILED_ATTEMPTS_SEEN, value)

    // --- C6 Unlock deadline ---
    const val KEY_UNLOCK_DEADLINE_HOURS = "unlock_deadline_hours"
    const val KEY_UNLOCK_DEADLINE_GRACE_SECONDS = "unlock_deadline_grace_seconds"
    const val KEY_UNLOCK_DEADLINE_ARMED_AT_MS = "unlock_deadline_armed_at_ms"
    const val KEY_UNLOCK_DEADLINE_SEEN_UNLOCKED_MS = "unlock_deadline_seen_unlocked_ms"

    fun unlockDeadlineHours(store: KvStore): Int = store.getInt(KEY_UNLOCK_DEADLINE_HOURS, 48)
    fun setUnlockDeadlineHours(store: KvStore, value: Int) = store.putInt(KEY_UNLOCK_DEADLINE_HOURS, value)

    fun unlockDeadlineGraceSeconds(store: KvStore): Int = store.getInt(KEY_UNLOCK_DEADLINE_GRACE_SECONDS, 60)
    fun setUnlockDeadlineGraceSeconds(store: KvStore, value: Int) =
        store.putInt(KEY_UNLOCK_DEADLINE_GRACE_SECONDS, value)

    /** When C6 was last armed from the UI; the owner is demonstrably present at that moment. */
    fun unlockDeadlineArmedAtMs(store: KvStore): Long = store.getLong(KEY_UNLOCK_DEADLINE_ARMED_AT_MS, 0L)
    fun setUnlockDeadlineArmedAtMs(store: KvStore, value: Long) = store.putLong(KEY_UNLOCK_DEADLINE_ARMED_AT_MS, value)

    /**
     * Last time the C6 check itself observed the device unlocked, or a countdown was cancelled
     * with the credential. Kept apart from [lastUnlockMs] so C6 can never push A8's timer.
     */
    fun unlockDeadlineSeenUnlockedMs(store: KvStore): Long = store.getLong(KEY_UNLOCK_DEADLINE_SEEN_UNLOCKED_MS, 0L)
    fun setUnlockDeadlineSeenUnlockedMs(store: KvStore, value: Long) =
        store.putLong(KEY_UNLOCK_DEADLINE_SEEN_UNLOCKED_MS, value)

    // --- Anti-snatch motion lock ---
    const val KEY_MOTION_LOCK_ENABLED = "motion_lock_enabled"
    const val KEY_MOTION_LOCK_SENSITIVITY = "motion_lock_sensitivity"

    fun motionLockEnabled(store: KvStore): Boolean = store.getBoolean(KEY_MOTION_LOCK_ENABLED, false)
    fun setMotionLockEnabled(store: KvStore, value: Boolean) = store.putBoolean(KEY_MOTION_LOCK_ENABLED, value)

    /** 0 = low, 1 = medium (default), 2 = high. */
    fun motionLockSensitivity(store: KvStore): Int = store.getInt(KEY_MOTION_LOCK_SENSITIVITY, 1)
    fun setMotionLockSensitivity(store: KvStore, value: Int) = store.putInt(KEY_MOTION_LOCK_SENSITIVITY, value)

    // --- Lockdown mode ---
    const val KEY_LOCKDOWN_ENABLED = "lockdown_enabled"

    fun lockdownEnabled(store: KvStore): Boolean = store.getBoolean(KEY_LOCKDOWN_ENABLED, false)
    fun setLockdownEnabled(store: KvStore, value: Boolean) = store.putBoolean(KEY_LOCKDOWN_ENABLED, value)
}

/**
 * Typed singleton accessors for Norypt Protect preferences.
 * Backed by EncryptedSharedPreferences (file: "norypt_protect_prefs").
 */
object ProtectPrefs {

    @Volatile private var cachedStore: KvStore? = null

    internal fun store(context: Context): KvStore {
        cachedStore?.let { return it }
        return synchronized(this) {
            cachedStore ?: buildStore(context.applicationContext).also { cachedStore = it }
        }
    }

    /** Main configuration file. */
    private const val MAIN_FILE = "norypt_protect_prefs"

    /**
     * Opens a separate encrypted file under the same master key. Not cached here; the caller
     * owns the instance. Used by the tamper timeline so its ring buffer never bloats the
     * configuration file that every trigger reads on each tick.
     */
    internal fun openStore(context: Context, fileName: String): KvStore =
        buildStore(context.applicationContext, fileName)

    private fun buildStore(appContext: Context, fileName: String = MAIN_FILE): KvStore {
        val masterKey: MasterKey = KeystoreHelper.masterKey(appContext)
        val prefs = EncryptedSharedPreferences.create(
            appContext,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        return object : KvStore {
            override fun getString(key: String, default: String?): String? = prefs.getString(key, default)
            override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
            override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
            override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
            override fun putString(key: String, value: String?) = prefs.edit().putString(key, value).apply()
            override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
            override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
            override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
        }
    }

    fun isTriggerEnabled(context: Context, id: String, default: Boolean = false): Boolean =
        ProtectPrefsKeys.isTriggerEnabled(store(context), id, default)

    fun setTriggerEnabled(context: Context, id: String, enabled: Boolean) =
        ProtectPrefsKeys.setTriggerEnabled(store(context), id, enabled)

    fun maxFailedAttempts(context: Context): Int =
        ProtectPrefsKeys.maxFailedAttempts(store(context))

    fun setMaxFailedAttempts(context: Context, value: Int) =
        ProtectPrefsKeys.setMaxFailedAttempts(store(context), value)

    fun maxUnlockedMinutes(context: Context): Int =
        ProtectPrefsKeys.maxUnlockedMinutes(store(context))

    fun setMaxUnlockedMinutes(context: Context, value: Int) =
        ProtectPrefsKeys.setMaxUnlockedMinutes(store(context), value)

    fun smsSecretCode(context: Context): String? =
        ProtectPrefsKeys.smsSecretCode(store(context))

    fun setSmsSecretCode(context: Context, value: String?) =
        ProtectPrefsKeys.setSmsSecretCode(store(context), value)

    fun fakeMessengerPackage(context: Context): String? =
        ProtectPrefsKeys.fakeMessengerPackage(store(context))

    fun setFakeMessengerPackage(context: Context, value: String?) =
        ProtectPrefsKeys.setFakeMessengerPackage(store(context), value)

    fun dryRun(context: Context): Boolean =
        ProtectPrefsKeys.dryRun(store(context))

    fun setDryRun(context: Context, value: Boolean) =
        ProtectPrefsKeys.setDryRun(store(context), value)

    fun wipeExternalStorage(context: Context): Boolean =
        ProtectPrefsKeys.wipeExternalStorage(store(context))

    fun setWipeExternalStorage(context: Context, value: Boolean) =
        ProtectPrefsKeys.setWipeExternalStorage(store(context), value)

    fun wipeEuicc(context: Context): Boolean =
        ProtectPrefsKeys.wipeEuicc(store(context))

    fun setWipeEuicc(context: Context, value: Boolean) =
        ProtectPrefsKeys.setWipeEuicc(store(context), value)

    fun failedAttempts(context: Context): Int =
        ProtectPrefsKeys.failedAttempts(store(context))

    /** Records a failed unlock now and returns the length of the current run. */
    fun recordFailedAttempt(context: Context): Int =
        ProtectPrefsKeys.recordFailedAttempt(store(context), System.currentTimeMillis())

    fun resetFailedAttempts(context: Context) =
        ProtectPrefsKeys.resetFailedAttempts(store(context))

    fun panicTriggerPackage(context: Context): String? =
        ProtectPrefsKeys.panicTriggerPackage(store(context))

    fun setPanicTriggerPackage(context: Context, value: String?) =
        ProtectPrefsKeys.setPanicTriggerPackage(store(context), value)

    fun pendingWipeReason(context: Context): String? =
        ProtectPrefsKeys.pendingWipeReason(store(context))

    fun setPendingWipeReason(context: Context, value: String?) =
        ProtectPrefsKeys.setPendingWipeReason(store(context), value)

    fun pendingWipeAtMs(context: Context): Long =
        ProtectPrefsKeys.pendingWipeAtMs(store(context))

    fun setPendingWipeAtMs(context: Context, value: Long) =
        ProtectPrefsKeys.setPendingWipeAtMs(store(context), value)

    fun lastUnlockMs(context: Context): Long =
        ProtectPrefsKeys.lastUnlockMs(store(context))

    fun setLastUnlockMs(context: Context, value: Long) =
        ProtectPrefsKeys.setLastUnlockMs(store(context), value)

    fun duressThreshold(context: Context): Int =
        ProtectPrefsKeys.duressThreshold(store(context))

    fun setDuressThreshold(context: Context, value: Int) =
        ProtectPrefsKeys.setDuressThreshold(store(context), value)

    fun antiTamperEnabled(context: Context): Boolean =
        ProtectPrefsKeys.antiTamperEnabled(store(context))

    fun setAntiTamperEnabled(context: Context, value: Boolean) =
        ProtectPrefsKeys.setAntiTamperEnabled(store(context), value)

    fun launcherHidden(context: Context): Boolean =
        ProtectPrefsKeys.launcherHidden(store(context))

    fun setLauncherHidden(context: Context, value: Boolean) =
        ProtectPrefsKeys.setLauncherHidden(store(context), value)

    fun sosDisabledOnPromotion(context: Context): Boolean =
        ProtectPrefsKeys.sosDisabledOnPromotion(store(context))

    fun setSosDisabledOnPromotion(context: Context, value: Boolean) =
        ProtectPrefsKeys.setSosDisabledOnPromotion(store(context), value)

    fun sosLastIntent(context: Context): Int =
        ProtectPrefsKeys.sosLastIntent(store(context))

    fun setSosLastIntent(context: Context, value: Int) =
        ProtectPrefsKeys.setSosLastIntent(store(context), value)

    fun powerMenuBlockWhenLocked(context: Context): Boolean =
        ProtectPrefsKeys.powerMenuBlockWhenLocked(store(context))

    fun setPowerMenuBlockWhenLocked(context: Context, value: Boolean) =
        ProtectPrefsKeys.setPowerMenuBlockWhenLocked(store(context), value)

    // --- C4 Dead-man switch ---

    fun deadmanBatteryPct(context: Context): Int =
        ProtectPrefsKeys.deadmanBatteryPct(store(context))

    fun setDeadmanBatteryPct(context: Context, value: Int) =
        ProtectPrefsKeys.setDeadmanBatteryPct(store(context), value)

    fun deadmanGraceSeconds(context: Context): Int =
        ProtectPrefsKeys.deadmanGraceSeconds(store(context))

    fun setDeadmanGraceSeconds(context: Context, value: Int) =
        ProtectPrefsKeys.setDeadmanGraceSeconds(store(context), value)

    fun deadmanRequireBt(context: Context): Boolean =
        ProtectPrefsKeys.deadmanRequireBt(store(context))

    fun setDeadmanRequireBt(context: Context, value: Boolean) =
        ProtectPrefsKeys.setDeadmanRequireBt(store(context), value)

    fun deadmanRequireGsm(context: Context): Boolean =
        ProtectPrefsKeys.deadmanRequireGsm(store(context))

    fun setDeadmanRequireGsm(context: Context, value: Boolean) =
        ProtectPrefsKeys.setDeadmanRequireGsm(store(context), value)

    fun deadmanRequireWifi(context: Context): Boolean =
        ProtectPrefsKeys.deadmanRequireWifi(store(context))

    fun setDeadmanRequireWifi(context: Context, value: Boolean) =
        ProtectPrefsKeys.setDeadmanRequireWifi(store(context), value)

    fun deadmanDisarmMinutesAfterUnlock(context: Context): Int =
        ProtectPrefsKeys.deadmanDisarmMinutesAfterUnlock(store(context))

    fun setDeadmanDisarmMinutesAfterUnlock(context: Context, value: Int) =
        ProtectPrefsKeys.setDeadmanDisarmMinutesAfterUnlock(store(context), value)

    // --- B5 Known internet packages ---

    fun knownInternetPackages(context: Context): Set<String> =
        ProtectPrefsKeys.knownInternetPackages(store(context))

    fun setKnownInternetPackages(context: Context, packages: Set<String>) =
        ProtectPrefsKeys.setKnownInternetPackages(store(context), packages)

    // --- Launch gate biometric ---

    fun launchBiometricEnabled(context: Context): Boolean =
        ProtectPrefsKeys.launchBiometricEnabled(store(context))

    fun setLaunchBiometricEnabled(context: Context, value: Boolean) =
        ProtectPrefsKeys.setLaunchBiometricEnabled(store(context), value)

    // --- Tamper timeline ---

    fun timelineEnabled(context: Context): Boolean = ProtectPrefsKeys.timelineEnabled(store(context))
    fun setTimelineEnabled(context: Context, value: Boolean) = ProtectPrefsKeys.setTimelineEnabled(store(context), value)

    fun timelineLastAliveMs(context: Context): Long = ProtectPrefsKeys.timelineLastAliveMs(store(context))
    fun setTimelineLastAliveMs(context: Context, value: Long) =
        ProtectPrefsKeys.setTimelineLastAliveMs(store(context), value)

    fun timelineLastBootCount(context: Context): Int = ProtectPrefsKeys.timelineLastBootCount(store(context))
    fun setTimelineLastBootCount(context: Context, value: Int) =
        ProtectPrefsKeys.setTimelineLastBootCount(store(context), value)

    fun timelineSimSnapshot(context: Context): String? = ProtectPrefsKeys.timelineSimSnapshot(store(context))
    fun setTimelineSimSnapshot(context: Context, value: String?) =
        ProtectPrefsKeys.setTimelineSimSnapshot(store(context), value)

    fun timelineAdbEnabled(context: Context): Int = ProtectPrefsKeys.timelineAdbEnabled(store(context))
    fun setTimelineAdbEnabled(context: Context, value: Int) = ProtectPrefsKeys.setTimelineAdbEnabled(store(context), value)

    fun timelineDeviceSecure(context: Context): Int = ProtectPrefsKeys.timelineDeviceSecure(store(context))
    fun setTimelineDeviceSecure(context: Context, value: Int) =
        ProtectPrefsKeys.setTimelineDeviceSecure(store(context), value)

    fun timelineBiometricStatus(context: Context): Int = ProtectPrefsKeys.timelineBiometricStatus(store(context))
    fun setTimelineBiometricStatus(context: Context, value: Int) =
        ProtectPrefsKeys.setTimelineBiometricStatus(store(context), value)

    fun timelineSentinelArmed(context: Context): Boolean = ProtectPrefsKeys.timelineSentinelArmed(store(context))
    fun setTimelineSentinelArmed(context: Context, value: Boolean) =
        ProtectPrefsKeys.setTimelineSentinelArmed(store(context), value)

    fun timelineFailedAttemptsSeen(context: Context): Int = ProtectPrefsKeys.timelineFailedAttemptsSeen(store(context))
    fun setTimelineFailedAttemptsSeen(context: Context, value: Int) =
        ProtectPrefsKeys.setTimelineFailedAttemptsSeen(store(context), value)

    // --- C6 Unlock deadline ---

    fun unlockDeadlineHours(context: Context): Int = ProtectPrefsKeys.unlockDeadlineHours(store(context))
    fun setUnlockDeadlineHours(context: Context, value: Int) = ProtectPrefsKeys.setUnlockDeadlineHours(store(context), value)

    fun unlockDeadlineGraceSeconds(context: Context): Int = ProtectPrefsKeys.unlockDeadlineGraceSeconds(store(context))
    fun setUnlockDeadlineGraceSeconds(context: Context, value: Int) =
        ProtectPrefsKeys.setUnlockDeadlineGraceSeconds(store(context), value)

    fun unlockDeadlineArmedAtMs(context: Context): Long = ProtectPrefsKeys.unlockDeadlineArmedAtMs(store(context))
    fun setUnlockDeadlineArmedAtMs(context: Context, value: Long) =
        ProtectPrefsKeys.setUnlockDeadlineArmedAtMs(store(context), value)

    fun unlockDeadlineSeenUnlockedMs(context: Context): Long =
        ProtectPrefsKeys.unlockDeadlineSeenUnlockedMs(store(context))
    fun setUnlockDeadlineSeenUnlockedMs(context: Context, value: Long) =
        ProtectPrefsKeys.setUnlockDeadlineSeenUnlockedMs(store(context), value)

    // --- Anti-snatch motion lock ---

    fun motionLockEnabled(context: Context): Boolean = ProtectPrefsKeys.motionLockEnabled(store(context))
    fun setMotionLockEnabled(context: Context, value: Boolean) = ProtectPrefsKeys.setMotionLockEnabled(store(context), value)

    fun motionLockSensitivity(context: Context): Int = ProtectPrefsKeys.motionLockSensitivity(store(context))
    fun setMotionLockSensitivity(context: Context, value: Int) =
        ProtectPrefsKeys.setMotionLockSensitivity(store(context), value)

    // --- Lockdown mode ---

    fun lockdownEnabled(context: Context): Boolean = ProtectPrefsKeys.lockdownEnabled(store(context))
    fun setLockdownEnabled(context: Context, value: Boolean) = ProtectPrefsKeys.setLockdownEnabled(store(context), value)
}
