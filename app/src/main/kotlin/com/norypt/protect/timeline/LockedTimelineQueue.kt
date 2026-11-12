package com.norypt.protect.timeline

import java.util.Base64

/**
 * Timeline entries recorded before the first unlock after a restart, while the encrypted Timeline
 * cannot be opened. Held as one string in device-protected storage and moved into the Timeline at
 * the first unlock, with their own times. Pure, so the format is unit-tested.
 *
 * One entry per line, tab-separated: epoch ms, kind, severity and the detail in Base64.
 */
internal object LockedTimelineQueue {

    /** Room for a run of wrong PINs and a wipe; the oldest entries give way beyond it. */
    const val MAX_ENTRIES = 50

    const val KEY = "locked_timeline_queue"

    data class Entry(val epochMs: Long, val kind: TamperKind, val severity: Severity, val detail: String)

    fun append(encoded: String?, entry: Entry): String =
        encode((decode(encoded) + entry).takeLast(MAX_ENTRIES))

    fun encode(entries: List<Entry>): String = entries.joinToString("\n") { entry ->
        val detail = Base64.getEncoder().encodeToString(entry.detail.toByteArray(Charsets.UTF_8))
        "${entry.epochMs}\t${entry.kind.name}\t${entry.severity.name}\t$detail"
    }

    /** A line that does not parse (a kind a later version dropped, say) is skipped. */
    fun decode(encoded: String?): List<Entry> {
        if (encoded.isNullOrEmpty()) return emptyList()
        return encoded.lineSequence().mapNotNull(::parse).toList()
    }

    private fun parse(line: String): Entry? {
        val fields = line.split('\t')
        if (fields.size != 4) return null
        return runCatching {
            Entry(
                epochMs = fields[0].toLong(),
                kind = TamperKind.valueOf(fields[1]),
                severity = Severity.valueOf(fields[2]),
                detail = String(Base64.getDecoder().decode(fields[3]), Charsets.UTF_8),
            )
        }.getOrNull()
    }
}
