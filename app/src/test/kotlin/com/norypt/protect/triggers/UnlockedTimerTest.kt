package com.norypt.protect.triggers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnlockedTimerTest {

    private val boot = 7

    @Test
    fun `time unlocked is measured on the monotonic clock of this boot`() {
        assertEquals(90_000L, UnlockedTimer.unlockedForMs(10_000L, boot, 100_000L, boot))
    }

    @Test
    fun `nothing is measured before an unlock was seen in this boot`() {
        assertNull(UnlockedTimer.unlockedForMs(0L, boot, 100_000L, boot))
        assertNull(UnlockedTimer.unlockedForMs(10_000L, boot - 1, 100_000L, boot))
    }

    @Test
    fun `a stamp that is somehow ahead of now counts as just unlocked`() {
        assertEquals(0L, UnlockedTimer.unlockedForMs(200_000L, boot, 100_000L, boot))
    }
}
