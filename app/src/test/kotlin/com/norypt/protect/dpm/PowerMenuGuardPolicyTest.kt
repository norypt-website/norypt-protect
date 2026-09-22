package com.norypt.protect.dpm

import com.norypt.protect.dpm.PowerMenuGuardPolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class PowerMenuGuardPolicyTest {

    @Test
    fun `lock screen up without a lock task means engage, screen on or off`() {
        assertEquals(Action.ENGAGE, PowerMenuGuardPolicy.decide(keyguardLocked = true, interactive = false, lockTaskActive = false))
        assertEquals(Action.ENGAGE, PowerMenuGuardPolicy.decide(keyguardLocked = true, interactive = true, lockTaskActive = false))
    }

    @Test
    fun `lock screen up with the lock task already held is steady state`() {
        assertEquals(Action.NONE, PowerMenuGuardPolicy.decide(keyguardLocked = true, interactive = false, lockTaskActive = true))
        assertEquals(Action.NONE, PowerMenuGuardPolicy.decide(keyguardLocked = true, interactive = true, lockTaskActive = true))
    }

    @Test
    fun `screen off before the lock-after delay has fired waits for the keyguard`() {
        // Timeout turned the screen off; "Lock after screen timeout" has not elapsed yet.
        // Engaging here is the bug: a wake inside that window returns to an unlocked
        // session with no USER_PRESENT, so the lock task would never be released.
        assertEquals(Action.WAIT_FOR_LOCK, PowerMenuGuardPolicy.decide(keyguardLocked = false, interactive = false, lockTaskActive = false))
    }

    @Test
    fun `a wake inside the lock-after window leaves nothing to undo`() {
        assertEquals(Action.NONE, PowerMenuGuardPolicy.decide(keyguardLocked = false, interactive = true, lockTaskActive = false))
    }

    @Test
    fun `an unlocked session that still holds the lock task is released`() {
        // The stuck state: quick settings dead and the power menu gone while unlocked.
        assertEquals(Action.RELEASE, PowerMenuGuardPolicy.decide(keyguardLocked = false, interactive = true, lockTaskActive = true))
    }

    @Test
    fun `screen off and unlocked with the lock task held is left alone until the wake or the lock`() {
        assertEquals(Action.NONE, PowerMenuGuardPolicy.decide(keyguardLocked = false, interactive = false, lockTaskActive = true))
    }

    @Test
    fun `polling is fast at first and backs off once the lock-after delay is clearly long`() {
        assertEquals(PowerMenuGuardPolicy.FAST_POLL_MS, PowerMenuGuardPolicy.pollIntervalMs(elapsedMs = 0L))
        val window = PowerMenuGuardPolicy.FAST_POLL_WINDOW_MS
        assertEquals(PowerMenuGuardPolicy.FAST_POLL_MS, PowerMenuGuardPolicy.pollIntervalMs(elapsedMs = window - 1))
        assertEquals(PowerMenuGuardPolicy.SLOW_POLL_MS, PowerMenuGuardPolicy.pollIntervalMs(elapsedMs = window))
        assertEquals(PowerMenuGuardPolicy.SLOW_POLL_MS, PowerMenuGuardPolicy.pollIntervalMs(elapsedMs = 30L * 60_000L))
    }
}
