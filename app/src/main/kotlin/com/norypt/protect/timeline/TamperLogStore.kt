package com.norypt.protect.timeline

import com.norypt.protect.prefs.KvStore

/**
 * Fixed-capacity ring buffers of [TamperEvent]s over a [KvStore].
 *
 * Alerts get a ring of their own. With one shared ring, anything cheap to repeat (a cable
 * plugged in and out) could overwrite the record of a SIM swap or a credential change in
 * minutes. One entry per key, so an append is two small writes and never rewrites the
 * history. Once a ring is full its oldest entry is overwritten. Reads return newest first.
 */
internal class TamperLogStore(
    private val store: KvStore,
    capacity: Int = DEFAULT_CAPACITY,
    alertCapacity: Int = DEFAULT_ALERT_CAPACITY,
) {
    init {
        require(capacity > 0 && alertCapacity > 0) { "capacities must be positive" }
    }

    private val routine = Ring(store, KEY_HEAD, KEY_SIZE, KEY_ENTRY_PREFIX, capacity)
    private val alerts = Ring(store, KEY_ALERT_HEAD, KEY_ALERT_SIZE, KEY_ALERT_PREFIX, alertCapacity)

    fun size(): Int = routine.size() + alerts.size()

    fun append(event: TamperEvent) {
        (if (event.severity == Severity.Alert) alerts else routine).append(encode(event))
    }

    /** Newest first. An entry that fails to decode is skipped rather than aborting the read. */
    fun all(): List<TamperEvent> {
        val merged = routine.all().mapNotNull(::decode) + alerts.all().mapNotNull(::decode)
        return merged.sortedWith(compareByDescending<TamperEvent> { it.epochMs }.thenByDescending { it.elapsedMs })
    }

    /** Removes every entry. Slots are nulled so cleared text does not linger in the file. */
    fun clear() {
        routine.clear()
        alerts.clear()
    }

    /** One ring: [capacity] slots under [prefix], plus a head index and a size. */
    private class Ring(
        private val store: KvStore,
        private val headKey: String,
        private val sizeKey: String,
        private val prefix: String,
        private val capacity: Int,
    ) {
        fun size(): Int = store.getInt(sizeKey, 0).coerceIn(0, capacity)

        fun append(raw: String) {
            val head = store.getInt(headKey, 0).let { if (it in 0 until capacity) it else 0 }
            store.putString(prefix + head, raw)
            store.putInt(headKey, (head + 1) % capacity)
            store.putInt(sizeKey, minOf(size() + 1, capacity))
        }

        /** Raw entries, newest first. */
        fun all(): List<String> {
            val size = size()
            val head = store.getInt(headKey, 0)
            return (1..size).mapNotNull { i -> store.getString(prefix + Math.floorMod(head - i, capacity), null) }
        }

        fun clear() {
            val size = size()
            val head = store.getInt(headKey, 0)
            for (i in 1..size) store.putString(prefix + Math.floorMod(head - i, capacity), null)
            store.putInt(headKey, 0)
            store.putInt(sizeKey, 0)
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 800
        const val DEFAULT_ALERT_CAPACITY = 200
        const val KEY_HEAD = "tl_head"
        const val KEY_SIZE = "tl_size"
        const val KEY_ENTRY_PREFIX = "tl_e_"
        const val KEY_ALERT_HEAD = "tl_a_head"
        const val KEY_ALERT_SIZE = "tl_a_size"
        const val KEY_ALERT_PREFIX = "tl_a_e_"

        /** ASCII unit separator (0x1F): never typed by a person, never in the platform strings stored here. */
        val SEP: Char = 0x1F.toChar()
        private const val VERSION = "1"
        private const val FIELD_COUNT = 6

        fun encode(e: TamperEvent): String = listOf(
            VERSION,
            e.epochMs.toString(),
            e.elapsedMs.toString(),
            e.kind.name,
            e.severity.name,
            sanitize(e.detail),
        ).joinToString(SEP.toString())

        fun decode(raw: String): TamperEvent? {
            val parts = raw.split(SEP)
            if (parts.size < FIELD_COUNT || parts[0] != VERSION) return null
            val epoch = parts[1].toLongOrNull() ?: return null
            val elapsed = parts[2].toLongOrNull() ?: return null
            val kind = TamperKind.entries.firstOrNull { it.name == parts[3] } ?: return null
            val severity = Severity.entries.firstOrNull { it.name == parts[4] } ?: return null
            val detail = parts.subList(FIELD_COUNT - 1, parts.size).joinToString(SEP.toString())
            return TamperEvent(epoch, elapsed, kind, severity, detail)
        }

        /** Keeps the separator and line breaks out of free text so one entry stays one record. */
        fun sanitize(detail: String): String =
            detail.replace(SEP, ' ').replace('\n', ' ').replace('\r', ' ').trim()
    }
}
