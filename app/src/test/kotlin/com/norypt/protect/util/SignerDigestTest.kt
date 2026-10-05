package com.norypt.protect.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SignerDigestTest {

    private val hex = "0123456789abcdef".repeat(4)

    // What the pairing prompt shows the owner to compare against the panic app's published key.
    @Test
    fun `a fingerprint is shown as upper-case byte pairs, eight per line`() {
        val shown = SignerDigest.formatFingerprint(hex)
        val lines = shown.lines()
        assertEquals(4, lines.size)
        assertEquals("01:23:45:67:89:AB:CD:EF", lines[0])
        lines.forEach { assertEquals("01:23:45:67:89:AB:CD:EF", it) }
    }

    @Test
    fun `round trip between hex and bytes`() {
        assertEquals(hex, SignerDigest.toHex(requireNotNull(SignerDigest.fromHex(hex))))
    }
}
