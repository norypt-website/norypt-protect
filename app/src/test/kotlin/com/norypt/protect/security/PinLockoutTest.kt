package com.norypt.protect.security

import com.norypt.protect.prefs.KvStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeKvStore : KvStore {
    private val map = HashMap<String, Any?>()

    override fun getString(key: String, default: String?): String? =
        if (map.containsKey(key)) map[key] as String? else default

    override fun getInt(key: String, default: Int): Int =
        if (map.containsKey(key)) map[key] as Int else default

    override fun getBoolean(key: String, default: Boolean): Boolean =
        if (map.containsKey(key)) map[key] as Boolean else default

    override fun getLong(key: String, default: Long): Long =
        if (map.containsKey(key)) map[key] as Long else default

    override fun putString(key: String, value: String?) { map[key] = value }
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun putLong(key: String, value: Long) { map[key] = value }
}

class PinLockoutTest {

    private lateinit var store: KvStore
    private val t0 = 1_000_000L      // wall
    private val e0 = 50_000L         // elapsedRealtime

    @Before
    fun setUp() {
        store = FakeKvStore()
    }

    private fun failTimes(n: Int, wall: Long = t0, elapsed: Long = e0) {
        repeat(n) { PinLockout.recordFailure(store, wall, elapsed) }
    }

    @Test
    fun `entry is permitted before any failure`() {
        assertFalse(PinLockout.isLockedOut(store, t0, e0))
        assertEquals(0, PinLockout.attempts(store))
    }

    @Test
    fun `failures below the threshold do not lock out`() {
        failTimes(PinLockout.MAX_ATTEMPTS - 1)

        assertFalse(PinLockout.isLockedOut(store, t0, e0))
        assertEquals(PinLockout.MAX_ATTEMPTS - 1, PinLockout.attempts(store))
    }

    @Test
    fun `reaching the threshold starts a lockout`() {
        failTimes(PinLockout.MAX_ATTEMPTS)

        assertTrue(PinLockout.isLockedOut(store, t0, e0))
        assertEquals(PinLockout.LOCKOUT_MS, PinLockout.remainingLockoutMs(store, t0, e0))
    }

    @Test
    fun `lockout survives a simulated process restart`() {
        failTimes(PinLockout.MAX_ATTEMPTS)

        // A new PinLockout read against the same persisted store stands in for the
        // rotation or force-stop that used to reset an in-memory counter.
        assertTrue(PinLockout.isLockedOut(store, t0 + 1_000, e0 + 1_000))
    }

    @Test
    fun `lockout expires on its own so the owner is not permanently locked out`() {
        failTimes(PinLockout.MAX_ATTEMPTS)

        assertTrue(PinLockout.isLockedOut(store, t0 + PinLockout.LOCKOUT_MS - 1, e0 + PinLockout.LOCKOUT_MS - 1))
        assertFalse(PinLockout.isLockedOut(store, t0 + PinLockout.LOCKOUT_MS, e0 + PinLockout.LOCKOUT_MS))
        assertEquals(0L, PinLockout.remainingLockoutMs(store, t0 + PinLockout.LOCKOUT_MS + 1, e0 + PinLockout.LOCKOUT_MS + 1))
    }

    @Test
    fun `a fresh run of attempts is available after the lockout expires`() {
        failTimes(PinLockout.MAX_ATTEMPTS)
        val afterWall = t0 + PinLockout.LOCKOUT_MS
        val afterElapsed = e0 + PinLockout.LOCKOUT_MS

        assertEquals(1, PinLockout.recordFailure(store, afterWall, afterElapsed))
        assertFalse(PinLockout.isLockedOut(store, afterWall, afterElapsed))
    }

    @Test
    fun `success clears attempts and any pending lockout`() {
        failTimes(PinLockout.MAX_ATTEMPTS)
        PinLockout.recordSuccess(store)

        assertFalse(PinLockout.isLockedOut(store, t0, e0))
        assertEquals(0, PinLockout.attempts(store))
    }

    @Test
    fun `a forward clock jump cannot strand the user in a lockout`() {
        failTimes(PinLockout.MAX_ATTEMPTS)

        // Clock steps back far enough that the stored deadline is further out than a whole
        // lockout period; treat that as expired rather than holding the gate shut.
        val wayBack = t0 - 10 * PinLockout.LOCKOUT_MS
        assertFalse(PinLockout.isLockedOut(store, wayBack, e0 - 10 * PinLockout.LOCKOUT_MS))
    }

    // --- Clock-manipulation attacks. Each of these previously cleared the lockout. ---

    @Test
    fun `moving the system clock forward does not clear the lockout`() {
        failTimes(PinLockout.MAX_ATTEMPTS)

        // Attacker jumps the wall clock a year ahead; the monotonic clock is untouched.
        val wallJump = t0 + 365L * 24 * 60 * 60 * 1000
        assertTrue(PinLockout.isLockedOut(store, wallJump, e0 + 1_000))
    }

    @Test
    fun `rebooting does not clear the lockout`() {
        failTimes(PinLockout.MAX_ATTEMPTS)

        // elapsedRealtime resets to near zero after a reboot; wall clock advanced a little.
        assertTrue(PinLockout.isLockedOut(store, t0 + 30_000, nowElapsedMs = 900L))
    }

    @Test
    fun `lockout still expires when both clocks advance normally`() {
        failTimes(PinLockout.MAX_ATTEMPTS)

        val wall = t0 + PinLockout.LOCKOUT_MS
        val elapsed = e0 + PinLockout.LOCKOUT_MS
        assertFalse(PinLockout.isLockedOut(store, wall, elapsed))
    }

    @Test
    fun `clock forward plus reboot together still cannot clear it early`() {
        failTimes(PinLockout.MAX_ATTEMPTS)

        // Wall deadline defeated by the jump, monotonic deadline defeated by the reboot —
        // but the reboot leaves the wall deadline in force, so the lockout holds.
        assertTrue(PinLockout.isLockedOut(store, t0 + 60_000, nowElapsedMs = 500L))
    }
}
