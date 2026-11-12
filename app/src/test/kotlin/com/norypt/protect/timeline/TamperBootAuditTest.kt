package com.norypt.protect.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TamperBootAuditTest {

    @Test
    fun `a first observation never reports unobserved boots`() {
        assertEquals(0, TamperBootAudit.unobservedBoots(previousCount = 0, currentCount = 17))
    }

    @Test
    fun `one boot since the last session is the boot being logged`() {
        assertEquals(0, TamperBootAudit.unobservedBoots(previousCount = 16, currentCount = 17))
    }

    @Test
    fun `every boot beyond the current one was unobserved`() {
        assertEquals(1, TamperBootAudit.unobservedBoots(previousCount = 16, currentCount = 18))
        assertEquals(4, TamperBootAudit.unobservedBoots(previousCount = 10, currentCount = 15))
    }

    @Test
    fun `a boot count that did not advance reports nothing`() {
        assertEquals(0, TamperBootAudit.unobservedBoots(previousCount = 17, currentCount = 17))
        assertEquals(0, TamperBootAudit.unobservedBoots(previousCount = 20, currentCount = 17))
    }

    @Test
    fun `boot detail mentions the previous session only when one was seen before the boot`() {
        val bootAt = 1_700_000_000_000L
        val now = bootAt + 4 * 3_600_000L

        val withPrevious = TamperBootAudit.bootDetail(bootAt, now, lastAliveMs = bootAt - 3_600_000L, fromBootBroadcast = true)
        assertTrue(withPrevious.contains("Previous session last seen"))
        assertTrue(withPrevious.contains("1h 0m before the boot"))
        assertTrue(withPrevious.contains("first unlocked"))

        val noPrevious = TamperBootAudit.bootDetail(bootAt, now, lastAliveMs = 0L, fromBootBroadcast = false)
        assertFalse(noPrevious.contains("Previous session"))
        assertTrue(noPrevious.contains("monitoring resumed"))
    }

    @Test
    fun `a last-alive stamp after the boot is not reported as a previous session`() {
        val bootAt = 1_700_000_000_000L
        val detail = TamperBootAudit.bootDetail(bootAt, bootAt + 60_000L, lastAliveMs = bootAt + 30_000L, fromBootBroadcast = true)
        assertFalse(detail.contains("Previous session"))
    }

    @Test
    fun `unobserved detail states the count and the counter movement`() {
        val detail = TamperBootAudit.unobservedDetail(2, 10, 13)
        assertTrue(detail.startsWith("2 boot(s)"))
        assertTrue(detail.contains("10 → 13"))
    }

    @Test
    fun `a boot protected before the first unlock says so instead of monitoring resumed`() {
        val bootAt = 1_700_000_000_000L
        val detail = TamperBootAudit.bootDetail(
            bootAt, bootAt + 600_000L, lastAliveMs = 0L, fromBootBroadcast = false, protectedFromMs = bootAt + 20_000L,
        )
        assertTrue(detail.contains("protection on from"))
        assertTrue(detail.contains("before the first unlock; unlocked by"))
        assertFalse(detail.contains("monitoring resumed"))
        val fromBroadcast = TamperBootAudit.bootDetail(bootAt, bootAt + 600_000L, 0L, true, bootAt + 20_000L)
        assertTrue(fromBroadcast.contains("before the first unlock; first unlocked"))
    }

    @Test
    fun `only a locked service start in this boot counts as protection before the first unlock`() {
        val bootAt = 1_700_000_000_000L
        assertEquals(bootAt + 20_000L, TamperBootAudit.startedLockedAt(7, 20_000L, true, currentBoot = 7, bootAtMs = bootAt))
        // Started after the unlock (an update, or not a Device Owner), or in an earlier boot.
        assertNull(TamperBootAudit.startedLockedAt(7, 20_000L, false, currentBoot = 7, bootAtMs = bootAt))
        assertNull(TamperBootAudit.startedLockedAt(6, 20_000L, true, currentBoot = 7, bootAtMs = bootAt))
        assertNull(TamperBootAudit.startedLockedAt(-1, 0L, false, currentBoot = 7, bootAtMs = bootAt))
    }

    @Test
    fun `the service-start stamp is written before the first unlock, so it is routed`() {
        listOf("protection_start_boot", "protection_start_elapsed", "protection_start_locked").forEach {
            assertTrue(it, com.norypt.protect.prefs.DeviceProtectedKeys.routed(it))
        }
    }
}
