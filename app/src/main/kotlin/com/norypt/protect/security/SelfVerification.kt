package com.norypt.protect.security

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

/**
 * APK signing-certificate verification.
 *
 * Read the app's current signing cert SHA-256 and compare against a known-good
 * fingerprint if one is pinned. Defends against repackaged APKs — if an attacker
 * substitutes the binary, the cert changes and the check catches it.
 *
 * The release keystore hasn't been generated yet, so [EXPECTED_RELEASE_SHA256]
 * is intentionally blank. Once the Norypt release key exists and F-Droid
 * Reproducible Builds are set up, paste the expected SHA-256 here and flip
 * the check to hard-fail (see [verifyOrRefuse]).
 */
object SelfVerification {

    /** Pinned SHA-256 of the Norypt release signing cert. Empty = unpinned. */
    private const val EXPECTED_RELEASE_SHA256 = ""

    /** Read the SHA-256 fingerprint of whichever cert signed this APK. */
    fun currentCertSha256(ctx: Context): String? = runCatching {
        val pkg = ctx.packageManager.getPackageInfo(
            ctx.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val signers = pkg.signingInfo ?: return@runCatching null
        val signature = signers.apkContentsSigners.firstOrNull() ?: return@runCatching null
        val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        digest.joinToString(":") { "%02X".format(it) }
    }.getOrNull()

    /**
     * True iff the APK's cert matches the pinned release fingerprint.
     * When the pin is empty (dev/debug builds), returns true unconditionally —
     * we can't verify what we don't have. Trust-report screen still surfaces
     * the raw fingerprint so users can compare against F-Droid's build.
     */
    fun isTrustedCert(ctx: Context): Boolean {
        if (EXPECTED_RELEASE_SHA256.isBlank()) return true
        return currentCertSha256(ctx)?.equals(EXPECTED_RELEASE_SHA256, ignoreCase = true) == true
    }

    /** Empty when pin is set for display purposes in the Trust screen. */
    fun pinnedCertSha256(): String = EXPECTED_RELEASE_SHA256
}
