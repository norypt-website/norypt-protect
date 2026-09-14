package com.norypt.protect.timeline

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Detects biometric enrollment changes without any privileged API.
 *
 * A Keystore key created with `setInvalidatedByBiometricEnrollment(true)` is permanently
 * invalidated by the platform the moment a fingerprint or face is added or removed (and when
 * the secure lock screen is removed). Initialising a cipher on it is enough to find out: the
 * key is never used to encrypt anything.
 */
internal object BiometricSentinel {

    enum class Probe { OK, INVALIDATED, MISSING, UNAVAILABLE }

    private const val ALIAS = "norypt_tamper_biometric_sentinel"
    private const val PROVIDER = "AndroidKeyStore"

    fun probe(): Probe = runCatching {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val key = ks.getKey(ALIAS, null) as? SecretKey ?: return Probe.MISSING
        val cipher = Cipher.getInstance(
            "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}",
        )
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key)
            Probe.OK
        } catch (e: KeyPermanentlyInvalidatedException) {
            // The key is dead; drop it so the next arm() starts clean.
            runCatching { ks.deleteEntry(ALIAS) }
            Probe.INVALIDATED
        }
    }.getOrDefault(Probe.UNAVAILABLE)

    /** Generates the sentinel. Fails (returns false) when no biometric is enrolled. */
    fun arm(): Boolean = runCatching {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                // Per-operation biometric auth: init() succeeds without a prompt, and only a
                // doFinal() — which never happens here — would need one.
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        generator.generateKey()
        true
    }.getOrDefault(false)

    fun disarm() {
        runCatching { KeyStore.getInstance(PROVIDER).apply { load(null) }.deleteEntry(ALIAS) }
    }
}
