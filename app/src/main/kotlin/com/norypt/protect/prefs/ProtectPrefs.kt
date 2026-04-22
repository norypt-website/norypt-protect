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
    const val KEY_LAST_UNLOCK_MS = "last_unlock_ms"
    const val KEY_DURESS_THRESHOLD = "duress_threshold"
    const val KEY_ANTI_TAMPER_ENABLED = "anti_tamper_enabled"
    const val KEY_LAUNCHER_HIDDEN = "launcher_hidden"
    const val KEY_SOS_DISABLED_ON_PROMOTION = "sos_disabled_on_promotion"

    // C4 Dead-man switch keys
    const val KEY_DEADMAN_BATTERY_PCT = "deadman_battery_pct"
    const val KEY_DEADMAN_GRACE_SECONDS = "deadman_grace_seconds"
    const val KEY_DEADMAN_REQUIRE_BT = "deadman_require_bt"
    const val KEY_DEADMAN_REQUIRE_GSM = "deadman_require_gsm"
    const val KEY_DEADMAN_REQUIRE_WIFI = "deadman_require_wifi"
    const val KEY_DEADMAN_DISARM_MINUTES_AFTER_UNLOCK = "deadman_disarm_minutes_after_unlock"

    // B5 Package internet watcher
    const val KEY_KNOWN_INTERNET_PACKAGES = "known_internet_packages"

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

    fun dryRun(store: KvStore): Boolean =
        store.getBoolean(KEY_DRY_RUN, false)

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

    fun incrementFailedAttempts(store: KvStore) =
        store.putInt(KEY_FAILED_ATTEMPTS, failedAttempts(store) + 1)

    fun resetFailedAttempts(store: KvStore) =
        store.putInt(KEY_FAILED_ATTEMPTS, 0)

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
}

/**
 * Typed singleton accessors for Norypt Protect preferences.
 * Backed by EncryptedSharedPreferences (file: "norypt_protect_prefs").
 */
object ProtectPrefs {

    @Volatile private var cachedStore: KvStore? = null

    private fun store(context: Context): KvStore {
        cachedStore?.let { return it }
        return synchronized(this) {
            cachedStore ?: buildStore(context.applicationContext).also { cachedStore = it }
        }
    }

    private fun buildStore(appContext: Context): KvStore {
        val masterKey: MasterKey = KeystoreHelper.masterKey(appContext)
        val prefs = EncryptedSharedPreferences.create(
            appContext,
            "norypt_protect_prefs",
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

    fun incrementFailedAttempts(context: Context) =
        ProtectPrefsKeys.incrementFailedAttempts(store(context))

    fun resetFailedAttempts(context: Context) =
        ProtectPrefsKeys.resetFailedAttempts(store(context))

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
}
