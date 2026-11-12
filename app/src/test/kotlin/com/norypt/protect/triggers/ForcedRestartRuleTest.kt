package com.norypt.protect.triggers

import com.norypt.protect.admin.Tier
import com.norypt.protect.triggers.ForcedRestartRule.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForcedRestartRuleTest {

    @Test
    fun `a boot after a clean shutdown starts nothing`() {
        assertEquals(Action.NONE, ForcedRestartRule.decide(watchedBoot = 4, currentBoot = 5, cleanBoot = 4, pending = false))
    }

    @Test
    fun `a boot after a hardware reset starts the countdown`() {
        // Boot 4 ended with no shutdown record: the button was held until the phone restarted.
        assertEquals(Action.COUNTDOWN, ForcedRestartRule.decide(watchedBoot = 4, currentBoot = 5, cleanBoot = 3, pending = false))
    }

    @Test
    fun `a boot Norypt never reached counts as forced`() {
        // Reset again before the service started in boot 5: boot 5 left no record either.
        assertEquals(Action.COUNTDOWN, ForcedRestartRule.decide(watchedBoot = 4, currentBoot = 6, cleanBoot = 4, pending = false))
    }

    @Test
    fun `any restart while the countdown is pending wipes`() {
        assertEquals(Action.WIPE, ForcedRestartRule.decide(watchedBoot = 5, currentBoot = 6, cleanBoot = 4, pending = true))
        // Even a clean one: restarting from the menu during the countdown buys no time either.
        assertEquals(Action.WIPE, ForcedRestartRule.decide(watchedBoot = 5, currentBoot = 6, cleanBoot = 5, pending = true))
    }

    @Test
    fun `a service restart within the same boot decides nothing`() {
        assertEquals(Action.NONE, ForcedRestartRule.decide(watchedBoot = 5, currentBoot = 5, cleanBoot = 3, pending = true))
        assertEquals(Action.NONE, ForcedRestartRule.decide(watchedBoot = 5, currentBoot = 5, cleanBoot = 3, pending = false))
    }

    @Test
    fun `nothing is decided before a boot was ever watched`() {
        // The first start with C9's code: no record of the boot before.
        assertEquals(Action.NONE, ForcedRestartRule.decide(watchedBoot = 0, currentBoot = 5, cleanBoot = 0, pending = false))
    }

    @Test
    fun `the screen is wiped past its deadline only on the same boot`() {
        assertFalse(ForcedRestartMonitor.overdue(deadline = 100_000L, deadlineBoot = 5, nowElapsed = 110_000L, bootCount = 5))
        assertTrue(ForcedRestartMonitor.overdue(deadline = 100_000L, deadlineBoot = 5, nowElapsed = 110_001L, bootCount = 5))
        assertFalse(ForcedRestartMonitor.overdue(deadline = 100_000L, deadlineBoot = 4, nowElapsed = 900_000L, bootCount = 5))
        assertFalse(ForcedRestartMonitor.overdue(deadline = 0L, deadlineBoot = 5, nowElapsed = 900_000L, bootCount = 5))
    }

    @Test
    fun `C9 is a Device Owner trigger with a 30 second countdown`() {
        val trigger = TriggerRegistry.all.first { it.id == "C9" }
        assertEquals(Tier.DeviceOwner, trigger.requiredTier)
        assertEquals(30, ForcedRestartMonitor.GRACE_SECONDS)
    }
}
