package com.norypt.protect.security

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.security.crypto.EncryptedSharedPreferences
import com.norypt.protect.prefs.KvStore
import com.norypt.protect.prefs.ProtectPrefs
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object AppPin {
    private const val PREF_FILE = "norypt_protect_pin"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_HASH = "pin_hash"

    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 12

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

    /**
     * The only way to test a PIN. Every prompt goes through here, so [PinLockout] applies to
     * all of them, not just the launch gate: a dialog that compared directly would be an
     * unthrottled guessing oracle.
     */
    fun check(context: Context, pin: String): PinCheck =
        checkWith(ProtectPrefs.store(context), System.currentTimeMillis(), SystemClock.elapsedRealtime()) {
            verify(context, pin)
        }

    /** [check] with the clocks and the comparison injected, for unit tests. */
    internal fun checkWith(store: KvStore, nowWallMs: Long, nowElapsedMs: Long, matches: () -> Boolean): PinCheck {
        val locked = PinLockout.remainingLockoutMs(store, nowWallMs, nowElapsedMs)
        if (locked > 0L) return PinCheck.LockedOut(locked)
        if (matches()) {
            PinLockout.recordSuccess(store)
            return PinCheck.Ok
        }
        val attempts = PinLockout.recordFailure(store, nowWallMs, nowElapsedMs)
        val lockedNow = PinLockout.remainingLockoutMs(store, nowWallMs, nowElapsedMs)
        return if (lockedNow > 0L) PinCheck.LockedOut(lockedNow) else PinCheck.Wrong(attempts)
    }

    private fun verify(context: Context, pin: String): Boolean {
        val prefs = open(context)
        val salt = prefs.getString(KEY_SALT, null)?.fromBase64() ?: return false
        val stored = prefs.getString(KEY_HASH, null)?.fromBase64() ?: return false
        val attempt = derive(pin, salt)
        return constantTimeEquals(stored, attempt)
    }

    /**
     * Accepts only ASCII digits. `Char.isDigit` also takes Arabic-Indic and full-width digits,
     * which some keyboards emit; a PIN set that way may be impossible to type again, and the
     * lockdown screen's only other exit is a factory reset.
     */
    fun isPinInput(value: String): Boolean = value.length <= MAX_LENGTH && value.all { it in '0'..'9' }

    /**
     * Why [pin] is too easy to guess, or null if it is acceptable: one repeated digit, a
     * straight run up or down, or a short block repeated (121212, 123123).
     */
    fun weakness(pin: String): String? {
        val digits = pin.map { it - '0' }
        if (digits.size < 2) return null
        // Modulo 10, so a run that wraps from 9 to 0 (1234567890) still counts as a sequence.
        val steps = digits.zipWithNext { a, b -> (b - a + 10) % 10 }.toSet()
        return when {
            steps == setOf(0) -> "One digit repeated is the first thing anyone tries."
            steps == setOf(1) || steps == setOf(9) -> "Digits in sequence are the first thing anyone tries."
            (2..3).any { len -> pin.length > len && pin == pin.take(len).repeat(pin.length).take(pin.length) } ->
                "A repeated pattern is easy to guess."
            else -> null
        }
    }

    @Volatile private var cachedPrefs: SharedPreferences? = null

    /**
     * Cached like [com.norypt.protect.prefs.ProtectPrefs] does: each call otherwise builds a
     * fresh MasterKey (a Keystore round-trip) and decrypts the Tink keyset, and these run on
     * the main thread from PIN dialogs and composition.
     */
    private fun open(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        return synchronized(this) {
            cachedPrefs ?: build(context.applicationContext).also { cachedPrefs = it }
        }
    }

    private fun build(appContext: Context): SharedPreferences {
        val master = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREF_FILE,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun ByteArray.toBase64(): String = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
    private fun String.fromBase64(): ByteArray = android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
}
