package com.norypt.protect.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** In-memory KvStore backed by a HashMap — no Android framework needed. */
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

    fun containsKey(key: String): Boolean = map.containsKey(key)
}

class ProtectPrefsTest {

    private lateinit var store: FakeKvStore

    @Before
    fun setUp() {
        store = FakeKvStore()
    }

    @Test
    fun `defaults are correct for all keys`() {
        // Numeric defaults
        assertEquals(10, ProtectPrefsKeys.maxFailedAttempts(store))
        assertEquals(360, ProtectPrefsKeys.maxUnlockedMinutes(store))
        assertEquals(0, ProtectPrefsKeys.failedAttempts(store))
        assertEquals(0L, ProtectPrefsKeys.lastUnlockMs(store))
        // Boolean defaults — dry-run defaults to TRUE so that any unintended
        // panic on a fresh install only broadcasts a test intent, never a
        // real factory reset. Users flip this to false in Wipe Options once
        // they've validated their trigger setup.
        assertTrue(ProtectPrefsKeys.dryRun(store))
        assertTrue(ProtectPrefsKeys.wipeExternalStorage(store))
        assertTrue(ProtectPrefsKeys.wipeEuicc(store))
        assertFalse(ProtectPrefsKeys.isTriggerEnabled(store, "any_trigger", false))
        // String defaults
        assertNull(ProtectPrefsKeys.smsSecretCode(store))
        assertNull(ProtectPrefsKeys.fakeMessengerPackage(store))
    }

    @Test
    fun `key names are stable and correctly prefixed`() {
        assertEquals("trigger_enabled_", ProtectPrefsKeys.KEY_TRIGGER_ENABLED_PREFIX)
        // Setting a trigger stores under the prefixed key
        ProtectPrefsKeys.setTriggerEnabled(store, "max_attempts", true)
        assertTrue(store.containsKey("trigger_enabled_max_attempts"))

        ProtectPrefsKeys.setMaxFailedAttempts(store, 5)
        assertTrue(store.containsKey(ProtectPrefsKeys.KEY_MAX_FAILED_ATTEMPTS))

        ProtectPrefsKeys.setLastUnlockMs(store, 9999L)
        assertTrue(store.containsKey(ProtectPrefsKeys.KEY_LAST_UNLOCK_MS))
    }

    @Test
    fun `consecutive failed attempts within the window accumulate`() {
        val t0 = 1_000_000L
        assertEquals(0, ProtectPrefsKeys.failedAttempts(store))

        assertEquals(1, ProtectPrefsKeys.recordFailedAttempt(store, t0))
        assertEquals(2, ProtectPrefsKeys.recordFailedAttempt(store, t0 + 1_000))
        assertEquals(3, ProtectPrefsKeys.recordFailedAttempt(store, t0 + 2_000))
        assertEquals(3, ProtectPrefsKeys.failedAttempts(store))

        ProtectPrefsKeys.resetFailedAttempts(store)
        assertEquals(0, ProtectPrefsKeys.failedAttempts(store))
    }

    @Test
    fun `failed attempt after the window starts a new run`() {
        val t0 = 1_000_000L
        val window = ProtectPrefsKeys.FAILED_ATTEMPT_WINDOW_MS

        assertEquals(1, ProtectPrefsKeys.recordFailedAttempt(store, t0))
        assertEquals(2, ProtectPrefsKeys.recordFailedAttempt(store, t0 + window))

        // One millisecond past the window: unrelated event, so the run restarts at 1
        // rather than reaching a duress threshold months after the first mistype.
        assertEquals(1, ProtectPrefsKeys.recordFailedAttempt(store, t0 + window + window + 1))
    }

    @Test
    fun `backwards clock step restarts the run instead of extending it`() {
        val t0 = 1_000_000L
        assertEquals(1, ProtectPrefsKeys.recordFailedAttempt(store, t0))
        assertEquals(2, ProtectPrefsKeys.recordFailedAttempt(store, t0 + 1_000))

        assertEquals(1, ProtectPrefsKeys.recordFailedAttempt(store, t0 - 60_000))
    }

    @Test
    fun `reset clears the run timestamp so the next failure starts at one`() {
        val t0 = 1_000_000L
        ProtectPrefsKeys.recordFailedAttempt(store, t0)
        ProtectPrefsKeys.recordFailedAttempt(store, t0 + 1_000)
        ProtectPrefsKeys.resetFailedAttempts(store)

        assertEquals(1, ProtectPrefsKeys.recordFailedAttempt(store, t0 + 2_000))
    }

    @Test
    fun `round-trip typed values persist correctly`() {
        ProtectPrefsKeys.setDryRun(store, true)
        assertTrue(ProtectPrefsKeys.dryRun(store))

        ProtectPrefsKeys.setWipeExternalStorage(store, false)
        assertFalse(ProtectPrefsKeys.wipeExternalStorage(store))

        ProtectPrefsKeys.setWipeEuicc(store, false)
        assertFalse(ProtectPrefsKeys.wipeEuicc(store))

        ProtectPrefsKeys.setSmsSecretCode(store, "PANIC123")
        assertEquals("PANIC123", ProtectPrefsKeys.smsSecretCode(store))

        ProtectPrefsKeys.setFakeMessengerPackage(store, "com.example.fake")
        assertEquals("com.example.fake", ProtectPrefsKeys.fakeMessengerPackage(store))

        ProtectPrefsKeys.setMaxFailedAttempts(store, 3)
        assertEquals(3, ProtectPrefsKeys.maxFailedAttempts(store))

        ProtectPrefsKeys.setMaxUnlockedMinutes(store, 60)
        assertEquals(60, ProtectPrefsKeys.maxUnlockedMinutes(store))

        ProtectPrefsKeys.setLastUnlockMs(store, 1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, ProtectPrefsKeys.lastUnlockMs(store))
    }
}
