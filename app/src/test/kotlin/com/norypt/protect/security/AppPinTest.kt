package com.norypt.protect.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPinTest {

    private val salt = ByteArray(16) { it.toByte() } // deterministic salt for tests

    @Test
    fun `derive produces deterministic hash for same PIN and salt`() {
        val h1 = AppPin.derive("123456", salt)
        val h2 = AppPin.derive("123456", salt)
        assertEquals(h1.toHex(), h2.toHex())
    }

    @Test
    fun `derive produces different hashes for different PINs`() {
        val h1 = AppPin.derive("123456", salt)
        val h2 = AppPin.derive("654321", salt)
        assertFalse(h1.contentEquals(h2))
    }

    @Test
    fun `constant-time compare returns true for equal hashes`() {
        val h = AppPin.derive("123456", salt)
        assertTrue(AppPin.constantTimeEquals(h, h.copyOf()))
    }

    @Test
    fun `constant-time compare returns false for unequal hashes`() {
        val a = AppPin.derive("123456", salt)
        val b = AppPin.derive("654321", salt)
        assertFalse(AppPin.constantTimeEquals(a, b))
    }

    @Test
    fun `constant-time compare returns false for different-length arrays`() {
        assertFalse(AppPin.constantTimeEquals(ByteArray(4), ByteArray(8)))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
