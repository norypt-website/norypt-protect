package com.norypt.protect.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingBoundsTest {

    @Test
    fun `a failed-attempt limit of 0 or 1 cannot be stored, it would wipe on the first mistype`() {
        assertEquals(SettingBounds.FAILED_ATTEMPTS.first, SettingBounds.failedAttempts(0))
        assertEquals(SettingBounds.FAILED_ATTEMPTS.first, SettingBounds.failedAttempts(1))
        assertEquals(10, SettingBounds.failedAttempts(10))
    }

    @Test
    fun `the duress threshold is off at 0 and otherwise at least 2`() {
        assertEquals(0, SettingBounds.duressThreshold(0))
        assertEquals(0, SettingBounds.duressThreshold(-4))
        assertEquals(2, SettingBounds.duressThreshold(1))
        assertEquals(3, SettingBounds.duressThreshold(3))
    }

    @Test
    fun `the unlocked-time limit cannot drop to a minute or two`() {
        assertEquals(SettingBounds.UNLOCKED_MINUTES.first, SettingBounds.unlockedMinutes(1))
        assertEquals(360, SettingBounds.unlockedMinutes(360))
    }

    @Test
    fun `a countdown always leaves time to cancel`() {
        assertEquals(SettingBounds.GRACE_SECONDS.first, SettingBounds.graceSeconds(0))
        assertEquals(SettingBounds.GRACE_SECONDS.last, SettingBounds.graceSeconds(100_000))
    }

    @Test
    fun `a battery threshold of 100 percent would fire at any charge`() {
        assertEquals(SettingBounds.BATTERY_PCT.last, SettingBounds.batteryPct(100))
        assertEquals(SettingBounds.BATTERY_PCT.first, SettingBounds.batteryPct(0))
    }

    @Test
    fun `typed input that is not a number keeps the previous value`() {
        assertEquals(42, SettingBounds.parse("", 42) { it })
        assertEquals(42, SettingBounds.parse("abc", 42) { it })
        assertEquals(15, SettingBounds.parse("3", 42, SettingBounds::unlockedMinutes))
    }
}
