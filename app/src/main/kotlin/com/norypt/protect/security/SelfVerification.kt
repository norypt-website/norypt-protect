package com.norypt.protect.security

import android.content.Context
import android.content.pm.PackageManager
import com.norypt.protect.BuildConfig
import java.security.MessageDigest

/**
 * APK signing-certificate verification.
 *
 * Reads the running APK's signing cert SHA-256 and compares it against the
 * pinned Norypt release fingerprint. If a release-signed APK has been
 * repackaged or re-signed by an attacker, the cert changes and the check
 * fails — [MainActivity] then refuses to start the UI.
 *
 * Debug builds always pass through (a debug APK is signed with the local
 * Android debug keystore and would never match the release fingerprint).
 */
object SelfVerification {

    /**
     * Pinned SHA-256 of the Norypt Protect production signing certificate.
     *
     * Mirrored in:
     *  - README.md "Verify the app" section
     *  - Trust report screen (read live from [currentCertSha256] and compared
     *    visually by the user)
     *  - norypt.com/protect
     *  - Future F-Droid Reproducible Build entry
     *
     * Format is upper-case hex bytes separated by colons, matching what
     * `keytool -list -v` and `apksigner verify --print-certs` produce.
     */
    private const val EXPECTED_RELEASE_SHA256 =
        "13:50:25:10:A5:B5:0D:59:BF:78:23:CB:E5:96:B8:8C:7B:4C:B5:4B:41:BC:21:7A:AC:7C:25:19:17:53:6E:95"

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
     * Debug builds bypass the check unconditionally so the dev workflow
     * (locally-built APKs signed with the Android debug keystore) is not
     * blocked.
     */
    fun isTrustedCert(ctx: Context): Boolean {
        if (BuildConfig.DEBUG) return true
        return currentCertSha256(ctx)?.equals(EXPECTED_RELEASE_SHA256, ignoreCase = true) == true
    }

    /** Pinned fingerprint, surfaced on the Trust-report screen for comparison. */
    fun pinnedCertSha256(): String = EXPECTED_RELEASE_SHA256
}
