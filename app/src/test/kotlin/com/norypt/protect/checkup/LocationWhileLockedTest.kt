package com.norypt.protect.checkup

import com.norypt.protect.checkup.LocationWhileLocked.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationWhileLockedTest {

    @Test
    fun `screen off turns location off only when the feature is on and location is on`() {
        assertEquals(Action.TURN_OFF, LocationWhileLocked.onScreenOff(featureOn = true, locationOn = true))
        assertEquals(Action.NOTHING, LocationWhileLocked.onScreenOff(featureOn = true, locationOn = false))
        assertEquals(Action.NOTHING, LocationWhileLocked.onScreenOff(featureOn = false, locationOn = true))
    }

    @Test
    fun `unlocking turns location back on only if Norypt switched it off`() {
        assertEquals(Action.TURN_ON, LocationWhileLocked.onUnlocked(weTurnedItOff = true))
        // The owner had it off already: unlocking must not switch it on.
        assertEquals(Action.NOTHING, LocationWhileLocked.onUnlocked(weTurnedItOff = false))
    }
}
