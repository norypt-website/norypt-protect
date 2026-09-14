package com.norypt.protect.timeline

import com.norypt.protect.prefs.KvStore

/**
 * Fixed-capacity ring buffer of [TamperEvent]s over a [KvStore].
 *
 * One entry per key, so an append is two small writes and never rewrites the whole history.
 * Once full, the oldest entry is overwritten. Reads return newest first.
 */
internal class TamperLogStore(
    private val store: KvStore,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    fun size(): Int = store.getInt(KEY_SIZE, 0).coerceIn(0, capacity)

    fun append(event: TamperEvent) {
        val head = store.getInt(KEY_HEAD, 0).let { if (it in 0 until capacity) it else 0 }
        store.putString(KEY_ENTRY_PREFIX + head, encode(event))
        store.putInt(KEY_HEAD, (head + 1) % capacity)
        store.putInt(KEY_SIZE, minOf(size() + 1, capacity))
    }

    /** Newest first. An entry that fails to decode is skipped rather than aborting the read. */
    fun all(): List<TamperEvent> {
        val size = size()
        val head = store.getInt(KEY_HEAD, 0)
        val out = ArrayList<TamperEvent>(size)
        for (i in 1..size) {
            val index = Math.floorMod(head - i, capacity)
            val raw = store.getString(KEY_ENTRY_PREFIX + index, null) ?: continue
            decode(raw)?.let(out::add)
        }
        return out
    }

    /** Removes every entry. Slots are nulled so cleared text does not linger in the file. */
    fun clear() {
        val size = size()
        val head = store.getInt(KEY_HEAD, 0)
        for (i in 1..size) {
            store.putString(KEY_ENTRY_PREFIX + Math.floorMod(head - i, capacity), null)
        }
        store.putInt(KEY_HEAD, 0)
        store.putInt(KEY_SIZE, 0)
    }

    companion object {
        const val DEFAULT_CAPACITY = 800
        const val KEY_HEAD = "tl_head"
        const val KEY_SIZE = "tl_size"
        const val KEY_ENTRY_PREFIX = "tl_e_"

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
