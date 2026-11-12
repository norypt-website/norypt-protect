package com.norypt.protect.triggers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootCableTest {

    @Test
    fun `the cable plugged in at boot is ignored within the grace`() {
        assertTrue(BootCable().apply { atStart(connected = true) }.suppress(inGrace = true))
    }

    @Test
    fun `the boot cable counts once the grace has passed`() {
        assertFalse(BootCable().apply { atStart(connected = true) }.suppress(inGrace = false))
    }

    @Test
    fun `a cable plugged in after the start counts within the grace`() {
        assertFalse(BootCable().apply { atStart(connected = false) }.suppress(inGrace = true))
        // Without a sticky reading nothing is exempt either.
        assertFalse(BootCable().suppress(inGrace = true))
    }

    @Test
    fun `unplugging the boot cable ends its exemption`() {
        val cable = BootCable().apply { atStart(connected = true) }
        cable.unplugged()
        assertFalse(cable.suppress(inGrace = true))
    }
}
