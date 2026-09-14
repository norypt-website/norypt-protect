package com.norypt.protect.timeline

import com.norypt.protect.prefs.KvStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeKvStore : KvStore {
    val map = HashMap<String, Any?>()

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

class TamperLogStoreTest {

    private fun event(n: Long, kind: TamperKind = TamperKind.UNLOCK, detail: String = "d$n") =
        TamperEvent(epochMs = 1_000_000L + n, elapsedMs = n, kind = kind, severity = kind.defaultSeverity, detail = detail)

    @Test
    fun `empty store reads as empty`() {
        val store = TamperLogStore(FakeKvStore(), capacity = 4)
        assertEquals(0, store.size())
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `entries come back newest first`() {
        val store = TamperLogStore(FakeKvStore(), capacity = 4)
        store.append(event(1))
        store.append(event(2))
        store.append(event(3))

        assertEquals(listOf(3L, 2L, 1L), store.all().map { it.elapsedMs })
        assertEquals(3, store.size())
    }

    @Test
    fun `the buffer wraps and keeps only the newest capacity entries`() {
        val store = TamperLogStore(FakeKvStore(), capacity = 3)
        (1L..5L).forEach { store.append(event(it)) }

        assertEquals(3, store.size())
        assertEquals(listOf(5L, 4L, 3L), store.all().map { it.elapsedMs })
    }

    @Test
    fun `an append is a bounded write regardless of history size`() {
        val kv = FakeKvStore()
        val store = TamperLogStore(kv, capacity = 3)
        (1L..50L).forEach { store.append(event(it)) }

        // Three slots plus head and size: a full buffer never grows the file.
        assertEquals(5, kv.map.size)
    }

    @Test
    fun `clear empties the buffer and nulls the slots`() {
        val kv = FakeKvStore()
        val store = TamperLogStore(kv, capacity = 3)
        (1L..4L).forEach { store.append(event(it)) }

        store.clear()

        assertEquals(0, store.size())
        assertTrue(store.all().isEmpty())
        assertTrue(kv.map.filterKeys { it.startsWith(TamperLogStore.KEY_ENTRY_PREFIX) }.values.all { it == null })

        store.append(event(9))
        assertEquals(listOf(9L), store.all().map { it.elapsedMs })
    }

    @Test
    fun `every field survives an encode-decode round trip`() {
        val e = TamperEvent(1_700_000_000_000L, 12_345L, TamperKind.SIM_CHANGED, Severity.Alert, "Slot 1: SIM removed")
        assertEquals(e, TamperLogStore.decode(TamperLogStore.encode(e)))
    }

    @Test
    fun `severity is stored per entry, not derived from the kind`() {
        val e = TamperEvent(1L, 2L, TamperKind.USB_CONNECTED, Severity.Alert, "data while locked")
        assertEquals(Severity.Alert, TamperLogStore.decode(TamperLogStore.encode(e))?.severity)
    }

    @Test
    fun `detail text cannot break the record format`() {
        val e = event(1, detail = "line one\nline two" + TamperLogStore.SEP + "extra")
        val decoded = TamperLogStore.decode(TamperLogStore.encode(e))
        assertEquals("line one line two extra", decoded?.detail)
    }

    @Test
    fun `corrupt or unknown entries are skipped rather than failing the read`() {
        val kv = FakeKvStore()
        val store = TamperLogStore(kv, capacity = 4)
        store.append(event(1))
        store.append(event(2))
        store.append(event(3))
        // Simulate an entry written by a future version with a kind this build does not know,
        // and one that is plain garbage.
        kv.map[TamperLogStore.KEY_ENTRY_PREFIX + 1] = TamperLogStore.encode(event(2)).replace("UNLOCK", "FUTURE_KIND")
        kv.map[TamperLogStore.KEY_ENTRY_PREFIX + 0] = "garbage"

        assertEquals(listOf(3L), store.all().map { it.elapsedMs })
        assertNull(TamperLogStore.decode("garbage"))
        assertNull(TamperLogStore.decode(""))
    }

    @Test
    fun `a corrupted head index does not throw`() {
        val kv = FakeKvStore()
        kv.putInt(TamperLogStore.KEY_HEAD, 999)
        kv.putInt(TamperLogStore.KEY_SIZE, -5)
        val store = TamperLogStore(kv, capacity = 4)

        assertEquals(0, store.size())
        store.append(event(1))
        assertEquals(listOf(1L), store.all().map { it.elapsedMs })
    }
}
