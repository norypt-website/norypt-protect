package com.norypt.protect.prefs

import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.service.CountdownMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProtectedKeysTest {

    private class MemoryStore : KvStore {
        val map = HashMap<String, Any?>()
        override fun getString(key: String, default: String?): String? = if (key in map) map[key] as String? else default
        override fun getInt(key: String, default: Int): Int = if (key in map) map[key] as Int else default
        override fun getBoolean(key: String, default: Boolean): Boolean = if (key in map) map[key] as Boolean else default
        override fun getLong(key: String, default: Long): Long = if (key in map) map[key] as Long else default
        override fun putString(key: String, value: String?) { map[key] = value }
        override fun putInt(key: String, value: Int) { map[key] = value }
        override fun putBoolean(key: String, value: Boolean) { map[key] = value }
        override fun putLong(key: String, value: Long) { map[key] = value }
    }

    /** The store before the first unlock: the encrypted half throws, as Android does then. */
    private val deviceProtected = MemoryStore()
    private val locked = RoutingKvStore(deviceProtected, { error("locked") })

    @Test
    fun `the switches of the triggers that run before the first unlock are routed, no others`() {
        listOf("A9", "A11", "B1", "B4", "C3", "C4", "C6").forEach {
            assertTrue(it, DeviceProtectedKeys.routed("trigger_enabled_$it"))
        }
        // A6's texts are held by Android until the first unlock; the rest need the owner or other apps.
        listOf("A3", "A4", "A5", "A6", "A7", "A8", "A10").forEach {
            assertFalse(it, DeviceProtectedKeys.routed("trigger_enabled_$it"))
        }
    }

    @Test
    fun `nothing secret is routed`() {
        DeviceProtectedKeys.NEVER_ROUTED.forEach { assertFalse(it, DeviceProtectedKeys.routed(it)) }
    }

    @Test
    fun `every key read before the first unlock is routed`() {
        val keys = listOf(
            "max_failed_attempts", "duress_threshold", "failed_attempts", "failed_attempt_last_ms",
            "dry_run", "wipe_external_storage", "wipe_euicc",
            "pending_wipe_reason", "pending_wipe_at_ms", "pending_wipe_dry_run", "pending_wipe_cancel_gen",
            "last_unlock_ms", "timeline_enabled",
            "deadman_battery_pct", "deadman_grace_seconds", "deadman_require_bt", "deadman_require_gsm",
            "deadman_require_wifi", "deadman_disarm_minutes_after_unlock", "deadman_snooze_until_elapsed",
            "deadman_snooze_boot",
            "unlock_deadline_hours", "unlock_deadline_grace_seconds", "unlock_deadline_armed_at_ms",
            "unlock_deadline_seen_unlocked_ms",
        ) + CountdownMode.entries.flatMap { listOf("countdown_deadline_${it.extraValue}", "countdown_boot_${it.extraValue}") }
        keys.forEach { assertTrue(it, DeviceProtectedKeys.routed(it)) }
    }

    @Test
    fun `the wrong-PIN wipe works on a locked store`() {
        ProtectPrefsKeys.setTriggerEnabled(deviceProtected, "B1", true)
        ProtectPrefsKeys.setMaxFailedAttempts(deviceProtected, 5)
        ProtectPrefsKeys.setDuressThreshold(deviceProtected, 3)
        assertTrue(ProtectPrefsKeys.isTriggerEnabled(locked, "B1", false))
        assertFalse(ProtectPrefsKeys.isTriggerEnabled(locked, "A11", false))
        assertEquals(5, ProtectPrefsKeys.maxFailedAttempts(locked))
        assertEquals(3, ProtectPrefsKeys.duressThreshold(locked))
        assertEquals(1, ProtectPrefsKeys.recordFailedAttempt(locked, 1_000L))
        assertEquals(2, ProtectPrefsKeys.recordFailedAttempt(locked, 2_000L))
        ProtectPrefsKeys.resetFailedAttempts(locked)
        assertEquals(1, ProtectPrefsKeys.recordFailedAttempt(locked, 3_000L))
    }

    @Test
    fun `a wipe that fails before the first unlock is queued on a locked store`() {
        assertTrue(ProtectPrefsKeys.dryRun(locked))
        ProtectPrefsKeys.setDryRun(deviceProtected, false)
        assertFalse(ProtectPrefsKeys.dryRun(locked))
        ProtectPrefsKeys.wipeExternalStorage(locked)
        ProtectPrefsKeys.wipeEuicc(locked)
        PanicHandler.recordOutcome(locked, PanicHandler.PanicOutcome("max.failed", alertUser = true), dryRun = false, nowMs = 10L)
        assertEquals("max.failed", ProtectPrefsKeys.pendingWipeReason(locked))
        assertEquals(PanicHandler.Retry.Run("max.failed", false), PanicHandler.retryAction(locked, 20L))
        PanicHandler.recordOutcome(locked, PanicHandler.PanicOutcome(null, alertUser = false), dryRun = false, nowMs = 30L)
        assertNull(ProtectPrefsKeys.pendingWipeReason(locked))
    }

    @Test
    fun `the C4 and C6 settings and countdowns work on a locked store`() {
        ProtectPrefsKeys.setCountdownDeadline(locked, CountdownMode.UNLOCK_DEADLINE.extraValue, 99L, 4)
        assertEquals(99L, ProtectPrefsKeys.countdownDeadline(locked, CountdownMode.UNLOCK_DEADLINE.extraValue))
        ProtectPrefsKeys.unlockDeadlineHours(locked)
        ProtectPrefsKeys.unlockDeadlineGraceSeconds(locked)
        ProtectPrefsKeys.unlockDeadlineArmedAtMs(locked)
        ProtectPrefsKeys.unlockDeadlineSeenUnlockedMs(locked)
        ProtectPrefsKeys.lastUnlockMs(locked)
        ProtectPrefsKeys.deadmanBatteryPct(locked)
        ProtectPrefsKeys.deadmanGraceSeconds(locked)
        ProtectPrefsKeys.deadmanSnoozeUntil(locked)
        ProtectPrefsKeys.timelineEnabled(locked)
    }

    @Test(expected = IllegalStateException::class)
    fun `an encrypted key still throws before the first unlock`() {
        ProtectPrefsKeys.smsSecretCode(locked)
    }
}
