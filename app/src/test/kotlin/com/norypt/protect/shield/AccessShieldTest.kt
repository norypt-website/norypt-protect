package com.norypt.protect.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessShieldTest {

    @Test
    fun `an outside keyboard that is on and not approved blocks the policy`() {
        assertEquals(setOf("com.example.kb"), AccessShield.unapproved(setOf("com.example.kb"), emptySet()))
    }

    @Test
    fun `approved ones do not block it, and nothing on means nothing to approve`() {
        assertTrue(AccessShield.unapproved(setOf("com.example.kb"), setOf("com.example.kb", "org.other")).isEmpty())
        assertTrue(AccessShield.unapproved(emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun `a list that differs from the owner's is put back, order does not matter`() {
        assertTrue(AccessShield.needsReapply(setOf("a"), null))
        assertTrue(AccessShield.needsReapply(setOf("a"), listOf("a", "b")))
        assertFalse(AccessShield.needsReapply(setOf("a", "b"), listOf("b", "a")))
        assertFalse(AccessShield.needsReapply(emptySet(), emptyList()))
    }
}
