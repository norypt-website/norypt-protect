package com.norypt.protect.util

import android.content.pm.PackageManager
import java.security.MessageDigest

/** Identifies an installed app by its signing key rather than its package name. */
object SignerDigest {

    /**
     * SHA-256 (hex) of the certificate [pkg] is currently signed with, or null if it is not
     * installed or is signed by several signers, which a pairing does not support.
     */
    fun of(pm: PackageManager, pkg: String): String? = runCatching {
        val signers = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners
        if (signers == null || signers.size != 1) return null
        toHex(MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray()))
    }.getOrNull()

    /** Whether [pkg] is signed with the certificate [hexDigest] names, key rotation included. */
    fun matches(pm: PackageManager, pkg: String, hexDigest: String?): Boolean {
        val digest = hexDigest?.let(::fromHex) ?: return false
        return runCatching {
            pm.hasSigningCertificate(pkg, digest, PackageManager.CERT_INPUT_SHA256)
        }.getOrDefault(false)
    }

    internal fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    internal fun fromHex(hex: String): ByteArray? {
        if (hex.length % 2 != 0 || hex.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
        return ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
