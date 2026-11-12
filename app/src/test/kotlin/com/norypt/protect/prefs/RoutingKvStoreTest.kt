package com.norypt.protect.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingKvStoreTest {

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

    private val routed = { key: String -> key.startsWith("dp_") }

    @Test
    fun `each key goes to its own half`() {
        val dp = MemoryStore()
        val enc = MemoryStore()
        val store = RoutingKvStore(dp, { enc }, routed)
        store.putInt("dp_count", 3)
        store.putString("secret", "x")
        assertEquals(3, dp.map["dp_count"])
        assertEquals("x", enc.map["secret"])
        assertFalse("dp_count" in enc.map)
        assertFalse("secret" in dp.map)
        assertEquals(3, store.getInt("dp_count", 0))
        assertEquals("x", store.getString("secret", null))
    }

    @Test
    fun `a routed key never asks for the encrypted half`() {
        var asked = 0
        val store = RoutingKvStore(MemoryStore(), { asked++; error("locked") }, routed)
        store.putLong("dp_a", 1L)
        store.getLong("dp_a", 0L)
        store.getBoolean("dp_b", true)
        assertEquals(0, asked)
    }

    @Test
    fun `the move copies missing routed keys and removes the routed keys it settled`() {
        val encrypted = mapOf("dp_new" to 5, "dp_kept" to 1L, "secret" to "s", "dp_flag" to true)
        val plan = DeviceProtectedMigration.plan(encrypted, deviceProtectedKeys = setOf("dp_kept"), routed = routed)
        // A value already in the device-protected file (a wrong-PIN count, say) is newer and wins.
        assertEquals(mapOf("dp_new" to 5, "dp_flag" to true), plan.copy)
        assertEquals(setOf("dp_new", "dp_kept", "dp_flag"), plan.remove)
    }

    @Test
    fun `a value of a type the settings never use is neither copied nor removed`() {
        val plan = DeviceProtectedMigration.plan(mapOf("dp_set" to setOf("a"), "dp_n" to 1), emptySet(), routed)
        assertEquals(mapOf("dp_n" to 1), plan.copy)
        assertEquals(setOf("dp_n"), plan.remove)
    }

    @Test
    fun `nothing to move once moved`() {
        val plan = DeviceProtectedMigration.plan(mapOf("secret" to "s"), emptySet(), routed)
        assertTrue(plan.copy.isEmpty())
        assertTrue(plan.remove.isEmpty())
    }

    @Test
    fun `the real routing list moves a 1_3_1 settings file`() {
        val encrypted = mapOf(
            "trigger_enabled_B1" to true,
            "trigger_enabled_A6" to true,
            "dry_run" to false,
            "sms_secret_code" to "code",
            "pin_hash" to "h",
            "unlock_deadline_hours" to 24,
        )
        val plan = DeviceProtectedMigration.plan(encrypted, emptySet())
        assertEquals(setOf("trigger_enabled_B1", "dry_run", "unlock_deadline_hours"), plan.copy.keys)
    }

    @Test
    fun `the lazy encrypted half is retried after a failure`() {
        var unlocked = false
        val lazy = RetryingLazy { check(unlocked) { "locked" }; "store" }
        assertTrue(runCatching { lazy.get() }.isFailure)
        unlocked = true
        assertEquals("store", lazy.get())
    }
}
