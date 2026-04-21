package com.norypt.protect.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object AppPin {
    private const val PREF_FILE = "norypt_protect_pin"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_HASH = "pin_hash"

    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    /** Pure PBKDF2-HMAC-SHA256 key derivation — no I/O. */
    fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /** Constant-time array compare to avoid timing oracles on PIN verification. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    fun isSet(context: Context): Boolean {
        val prefs = open(context)
        return prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)
    }

    fun set(context: Context, pin: String) {
        require(pin.length >= 6) { "PIN must be at least 6 digits" }
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        open(context).edit()
            .putString(KEY_SALT, salt.toBase64())
            .putString(KEY_HASH, hash.toBase64())
            .commit()
    }

    fun verify(context: Context, pin: String): Boolean {
        val prefs = open(context)
        val salt = prefs.getString(KEY_SALT, null)?.fromBase64() ?: return false
        val stored = prefs.getString(KEY_HASH, null)?.fromBase64() ?: return false
        val attempt = derive(pin, salt)
        return constantTimeEquals(stored, attempt)
    }

    private fun open(context: Context): SharedPreferences {
        val master = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREF_FILE,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun ByteArray.toBase64(): String = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
    private fun String.fromBase64(): ByteArray = android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
}
