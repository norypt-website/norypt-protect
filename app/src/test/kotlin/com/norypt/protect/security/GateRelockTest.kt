package com.norypt.protect.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GateRelockTest {

    private val stoppedAt = 1_000_000L

    @Test
    fun `a quick trip to Settings and back keeps the app open`() {
        assertFalse(GateRelock.shouldRelock(stoppedAt, stoppedAt + 10_000L, screenWentOff = false))
    }

    @Test
    fun `away longer than the grace period asks for the PIN again`() {
        assertTrue(GateRelock.shouldRelock(stoppedAt, stoppedAt + GateRelock.BACKGROUND_GRACE_MS + 1, screenWentOff = false))
    }

    @Test
    fun `the screen turning off locks the app at once`() {
        assertTrue(GateRelock.shouldRelock(stoppedAt, stoppedAt + 1_000L, screenWentOff = true))
    }

    @Test
    fun `an unknown stop time locks rather than guesses`() {
        assertTrue(GateRelock.shouldRelock(0L, stoppedAt, screenWentOff = false))
    }
}
