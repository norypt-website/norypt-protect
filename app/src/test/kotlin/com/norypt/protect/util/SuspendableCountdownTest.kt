package com.norypt.protect.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuspendableCountdownTest {

    private val t0 = 10_000L
    private val grace = 60_000L
    private val maxPause = 60_000L

    private fun newCountdown() = SuspendableCountdown(t0, grace, maxPause)

    @Test
    fun `runs to expiry when never paused`() {
        val c = newCountdown()

        assertEquals(30_000L, c.advance(t0 + 30_000, paused = false))
        assertFalse(c.isExpired(t0 + 30_000))
        assertEquals(0L, c.advance(t0 + grace, paused = false))
        assertTrue(c.isExpired(t0 + grace))
    }

    @Test
    fun `pausing holds the countdown so a cancel can be authenticated`() {
        val c = newCountdown()
        c.advance(t0 + 50_000, paused = false)

        // 10 s left; user taps Cancel and spends 8 s at the credential prompt.
        c.advance(t0 + 58_000, paused = true)
        assertFalse(c.isExpired(t0 + 58_000))
        assertEquals(10_000L, c.remainingMs(t0 + 58_000))
    }

    @Test
    fun `suspension is capped so an abandoned prompt cannot disable the wipe`() {
        val c = newCountdown()

        // Attacker taps Cancel and walks away, leaving the prompt up for an hour.
        var now = t0
        repeat(60) {
            now += 60_000
            c.advance(now, paused = true)
        }

        // The pause budget is spent, so the deadline stopped moving and the wipe fires.
        assertEquals(0L, c.pauseBudgetRemainingMs)
        assertTrue(c.isExpired(now))
    }

    @Test
    fun `total extension never exceeds the pause budget`() {
        val c = newCountdown()
        var now = t0
        repeat(200) {
            now += 1_000
            c.advance(now, paused = true)
        }

        assertEquals(t0 + grace + maxPause, c.deadlineElapsedMs)
        assertEquals(maxPause, c.pauseUsedMs)
    }

    @Test
    fun `budget is consumed only while paused`() {
        val c = newCountdown()

        c.advance(t0 + 20_000, paused = false)
        assertEquals(maxPause, c.pauseBudgetRemainingMs)

        c.advance(t0 + 25_000, paused = true)
        assertEquals(maxPause - 5_000, c.pauseBudgetRemainingMs)

        c.advance(t0 + 40_000, paused = false)
        assertEquals(maxPause - 5_000, c.pauseBudgetRemainingMs)
    }

    @Test
    fun `resuming after a pause continues rather than restarting the grace period`() {
        val c = newCountdown()
        c.advance(t0 + 55_000, paused = false)   // 5 s left
        c.advance(t0 + 75_000, paused = true)    // 20 s paused, still 5 s left

        assertEquals(5_000L, c.remainingMs(t0 + 75_000))
        assertEquals(0L, c.advance(t0 + 80_000, paused = false))
    }

    @Test
    fun `deadline survives reconstruction so an Activity restart cannot restart the timer`() {
        val c = newCountdown()
        c.advance(t0 + 40_000, paused = false)

        // Rebuilt from saved state the way the Activity does after a configuration change.
        val restored = SuspendableCountdown(
            startElapsedMs = t0 + 40_000,
            graceMs = grace,
            maxPauseMs = maxPause,
            pauseUsedMs = c.pauseUsedMs,
            deadlineElapsedMs = c.deadlineElapsedMs,
        )

        assertEquals(20_000L, restored.remainingMs(t0 + 40_000))
        assertTrue(restored.isExpired(t0 + grace))
    }

    @Test
    fun `repeated recreation cannot postpone the wipe`() {
        var deadline = t0 + grace
        var used = 0L
        var now = t0

        // Simulates an attacker rotating the phone every second for two minutes.
        repeat(120) {
            val c = SuspendableCountdown(now, grace, maxPause, used, deadline)
            now += 1_000
            c.advance(now, paused = false)
            deadline = c.deadlineElapsedMs
            used = c.pauseUsedMs
        }

        assertTrue(SuspendableCountdown(now, grace, maxPause, used, deadline).isExpired(now))
    }

    @Test
    fun `a backwards clock step cannot rewind the deadline`() {
        val c = newCountdown()
        c.advance(t0 + 30_000, paused = true)
        val deadlineAfterPause = c.deadlineElapsedMs

        c.advance(t0 + 1_000, paused = true)

        assertEquals(deadlineAfterPause, c.deadlineElapsedMs)
    }

    @Test
    fun `zero pause budget means suspension has no effect`() {
        val c = SuspendableCountdown(t0, grace, maxPauseMs = 0L)

        assertEquals(0L, c.advance(t0 + grace, paused = true))
        assertTrue(c.isExpired(t0 + grace))
    }
}
