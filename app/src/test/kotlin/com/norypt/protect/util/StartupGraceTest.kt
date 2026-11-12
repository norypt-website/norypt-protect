package com.norypt.protect.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupGraceTest {

    @Test
    fun `held in the first minutes of uptime and released after`() {
        assertTrue(StartupGrace.within(0L, StartupGrace.COUNTDOWN_MS))
        assertTrue(StartupGrace.within(StartupGrace.COUNTDOWN_MS - 1, StartupGrace.COUNTDOWN_MS))
        assertFalse(StartupGrace.within(StartupGrace.COUNTDOWN_MS, StartupGrace.COUNTDOWN_MS))
    }

    @Test
    fun `the USB grace is shorter than the countdown grace`() {
        // A cable plugged in more than two minutes after boot is not the owner's charger at boot.
        assertTrue(StartupGrace.USB_MS < StartupGrace.COUNTDOWN_MS)
    }
}
