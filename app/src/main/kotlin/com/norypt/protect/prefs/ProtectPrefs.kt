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
}

/**
 * Typed singleton accessors for Norypt Protect preferences.
 * Backed by EncryptedSharedPreferences (file: "norypt_protect_prefs").
 */
object ProtectPrefs {

    private fun store(context: Context): KvStore {
        val masterKey: MasterKey = KeystoreHelper.masterKey(context)
        val prefs = EncryptedSharedPreferences.create(
            context,
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
}
