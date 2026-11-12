package com.norypt.protect.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockedTimelineQueueTest {

    private fun entry(i: Int, detail: String = "entry $i") =
        LockedTimelineQueue.Entry(1_000L + i, TamperKind.UNLOCK_FAILED, Severity.Notable, detail)

    @Test
    fun `entries come back unchanged, tabs and new lines in the detail included`() {
        val tricky = entry(1, "Trigger \"max.failed\"\twith a tab\nand a new line.")
        val encoded = LockedTimelineQueue.append(LockedTimelineQueue.append(null, entry(0)), tricky)
        assertEquals(listOf(entry(0), tricky), LockedTimelineQueue.decode(encoded))
    }

    @Test
    fun `the oldest entries give way beyond the cap`() {
        var encoded: String? = null
        repeat(LockedTimelineQueue.MAX_ENTRIES + 3) { encoded = LockedTimelineQueue.append(encoded, entry(it)) }
        val decoded = LockedTimelineQueue.decode(encoded)
        assertEquals(LockedTimelineQueue.MAX_ENTRIES, decoded.size)
        assertEquals(entry(3), decoded.first())
        assertEquals(entry(LockedTimelineQueue.MAX_ENTRIES + 2), decoded.last())
    }

    @Test
    fun `lines that do not parse are skipped`() {
        val good = LockedTimelineQueue.encode(listOf(entry(0)))
        assertEquals(listOf(entry(0)), LockedTimelineQueue.decode("junk\n7\tNO_SUCH_KIND\tInfo\tZA==\n$good"))
    }

    @Test
    fun `an empty queue decodes to nothing`() {
        assertTrue(LockedTimelineQueue.decode(null).isEmpty())
        assertTrue(LockedTimelineQueue.decode("").isEmpty())
    }
}
