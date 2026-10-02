package com.norypt.protect.security

import com.norypt.protect.prefs.KvStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MapStore : KvStore {
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

class AppPinCheckTest {

    private val wall = 1_800_000_000_000L
    private val elapsed = 50_000_000L

    @Test
    fun `correct PIN passes and clears earlier failures`() {
        val store = MapStore()
        AppPin.checkWith(store, wall, elapsed) { false }
        assertEquals(PinCheck.Ok, AppPin.checkWith(store, wall, elapsed) { true })
        assertEquals(0, PinLockout.attempts(store))
    }

    @Test
    fun `wrong PIN counts the attempt`() {
        val result = AppPin.checkWith(MapStore(), wall, elapsed) { false }
        assertEquals(PinCheck.Wrong(attempts = 1), result)
    }

    @Test
    fun `the last allowed failure starts the lockout`() {
        val store = MapStore()
        repeat(PinLockout.MAX_ATTEMPTS - 1) { AppPin.checkWith(store, wall, elapsed) { false } }
        val result = AppPin.checkWith(store, wall, elapsed) { false }
        assertTrue(result is PinCheck.LockedOut)
    }

    @Test
    fun `during a lockout the PIN is not even compared`() {
        val store = MapStore()
        repeat(PinLockout.MAX_ATTEMPTS) { AppPin.checkWith(store, wall, elapsed) { false } }
        var compared = false
        val result = AppPin.checkWith(store, wall + 1_000, elapsed + 1_000) { compared = true; true }
        assertTrue(result is PinCheck.LockedOut)
        assertFalse(compared)
    }

    @Test
    fun `trivial PINs are rejected with a reason`() {
        listOf("000000", "777777", "123456", "654321", "345678", "121212", "123123", "1234567890", "1212121")
            .forEach { assertNotNull("expected $it to be weak", AppPin.weakness(it)) }
    }

    @Test
    fun `ordinary PINs are accepted`() {
        listOf("583920", "204871", "90817263", "112358")
            .forEach { assertNull("expected $it to be accepted", AppPin.weakness(it)) }
    }

    @Test
    fun `only ASCII digits are accepted as PIN input`() {
        assertTrue(AppPin.isPinInput("0123456789"))
        assertFalse(AppPin.isPinInput("12345٦")) // Arabic-Indic six
        assertFalse(AppPin.isPinInput("１２３４５６")) // full-width digits
        assertFalse(AppPin.isPinInput("1234567890123")) // longer than 12
    }
}
