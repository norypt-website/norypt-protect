package com.norypt.protect.triggers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockDeadlineTest {

    private val hour = 3_600_000L
    private val t0 = 1_700_000_000_000L

    @Test
    fun `deadline is reached exactly at N hours after the baseline`() {
        assertFalse(UnlockDeadlineMonitor.deadlineReached(t0, t0 + 48 * hour - 1, maxHours = 48))
        assertTrue(UnlockDeadlineMonitor.deadlineReached(t0, t0 + 48 * hour, maxHours = 48))
        assertTrue(UnlockDeadlineMonitor.deadlineReached(t0, t0 + 200 * hour, maxHours = 48))
    }

    @Test
    fun `no baseline means no deadline`() {
        // Nothing recorded yet: firing here would wipe a phone whose owner never left.
        assertFalse(UnlockDeadlineMonitor.deadlineReached(0L, t0 + 1_000 * hour, maxHours = 1))
    }

    @Test
    fun `a zero or negative hour setting disables the deadline`() {
        assertFalse(UnlockDeadlineMonitor.deadlineReached(t0, t0 + 1_000 * hour, maxHours = 0))
        assertFalse(UnlockDeadlineMonitor.deadlineReached(t0, t0 + 1_000 * hour, maxHours = -5))
    }

    @Test
    fun `a clock that stepped back before the baseline never expires`() {
        assertFalse(UnlockDeadlineMonitor.deadlineReached(t0, t0 - 10 * hour, maxHours = 1))
    }

    @Test
    fun `the baseline is the latest of the three presence stamps`() {
        assertEquals(30L, UnlockDeadlineMonitor.baselineMs(lastUnlockMs = 10L, armedAtMs = 30L, seenUnlockedMs = 20L))
        assertEquals(50L, UnlockDeadlineMonitor.baselineMs(lastUnlockMs = 50L, armedAtMs = 0L, seenUnlockedMs = 0L))
        assertEquals(0L, UnlockDeadlineMonitor.baselineMs(0L, 0L, 0L))
    }

    @Test
    fun `arming with no unlock ever recorded still gives a usable baseline`() {
        // A fresh install: last_unlock_ms is 0, but the arm time is set from the unlocked UI.
        val baseline = UnlockDeadlineMonitor.baselineMs(lastUnlockMs = 0L, armedAtMs = t0, seenUnlockedMs = 0L)
        assertFalse(UnlockDeadlineMonitor.deadlineReached(baseline, t0 + 47 * hour, maxHours = 48))
        assertTrue(UnlockDeadlineMonitor.deadlineReached(baseline, t0 + 48 * hour, maxHours = 48))
    }

    @Test
    fun `C6 is registered as a Device Owner trigger`() {
        val trigger = TriggerRegistry.all.first { it.id == "C6" }
        assertEquals(com.norypt.protect.admin.Tier.DeviceOwner, trigger.requiredTier)
    }
}
