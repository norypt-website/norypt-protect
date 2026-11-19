package com.norypt.protect.triggers

import com.norypt.protect.triggers.ForcedRestartRule.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ShutdownSignalRulesTest {

    private fun b(s: String) = s.toByteArray(Charsets.US_ASCII)

    private val idA = "62ed2879-f806-4dba-a52c-5b1d1c8fbd83" // boot 41
    private val idB = "0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9" // boot 42 (running)
    private fun mark(boot: Int, id: String) = ShutdownSignalRules.Read.Mark(boot, id)

    // ── record / parse ──

    @Test
    fun `the record is the boot count, the boot id and a newline`() {
        assertEquals("42 $idB\n", ShutdownSignalRules.record(42, idB))
        assertEquals("${Int.MAX_VALUE} $idB\n", ShutdownSignalRules.record(Int.MAX_VALUE, idB))
        assertNull(ShutdownSignalRules.record(0, idB))
        assertNull(ShutdownSignalRules.record(-1, idB))
        assertNull(ShutdownSignalRules.record(42, null))
        assertNull(ShutdownSignalRules.record(42, idB.uppercase()))
        assertNull(ShutdownSignalRules.record(42, idB.dropLast(1)))
    }

    @Test
    fun `every record parses back to its boot`() {
        for (boot in listOf(1, 9, 10, 42, 1234567, Int.MAX_VALUE)) {
            assertEquals(mark(boot, idA), ShutdownSignalRules.parse(b(ShutdownSignalRules.record(boot, idA)!!)))
        }
        assertEquals(ShutdownSignalRules.MAX_BYTES, b(ShutdownSignalRules.record(Int.MAX_VALUE, idA)!!).size)
    }

    @Test
    fun `no file or an empty file is no mark`() {
        assertSame(ShutdownSignalRules.Read.None, ShutdownSignalRules.parse(null))
        assertSame(ShutdownSignalRules.Read.None, ShutdownSignalRules.parse(ByteArray(0)))
    }

    @Test
    fun `anything else is malformed`() {
        val bad = listOf(
            "42\n", "42 $idA", "42  $idA\n", " 42 $idA\n", "042 $idA\n", "0 $idA\n", "-4 $idA\n",
            "+4 $idA\n", "4a $idA\n", "2147483648 $idA\n", "99999999999 $idA\n", "42 $idA\r\n",
            "42 $idA\n\n", "42 ${idA.uppercase()}\n", "42 ${idA.replace('-', 'x')}\n", "42 ${idA}0\n",
            "42 ${idA.drop(1)}\n", "42\t$idA\n", " $idA\n", "\n",
        )
        for (s in bad) {
            assertSame(s, ShutdownSignalRules.Read.Malformed, ShutdownSignalRules.parse(b(s)))
        }
        assertSame(
            ShutdownSignalRules.Read.Malformed,
            ShutdownSignalRules.parse(b("42 $idA\n").also { it[0] = 0xB2.toByte() }),
        )
    }

    // ── which boot it vouches for ──

    @Test
    fun `only a mark of another kernel boot vouches`() {
        assertEquals(41, ShutdownSignalRules.vouchedBoot(mark(41, idA), idB))
        assertNull(ShutdownSignalRules.vouchedBoot(mark(42, idB), idB)) // this boot's own mark
        assertNull(ShutdownSignalRules.vouchedBoot(mark(41, idB), idB)) // own boot_id, whatever the count says
        assertNull(ShutdownSignalRules.vouchedBoot(ShutdownSignalRules.Read.None, idB))
        assertEquals(-1, ShutdownSignalRules.vouchedBoot(ShutdownSignalRules.Read.Malformed, idB))
        assertEquals(-1, ShutdownSignalRules.vouchedBoot(mark(41, idA), null)) // running boot_id unreadable
        assertEquals(-1, ShutdownSignalRules.vouchedBoot(mark(41, idA), "garbage"))
    }

    @Test
    fun `a mark vouches only for the boot right after it`() {
        assertTrue(ShutdownSignalRules.endedInOrderlyReboot(41, 42))
        assertFalse(ShutdownSignalRules.endedInOrderlyReboot(40, 42)) // a later boot ended without it
        assertFalse(ShutdownSignalRules.endedInOrderlyReboot(42, 42))
        assertFalse(ShutdownSignalRules.endedInOrderlyReboot(43, 42))
        assertFalse(ShutdownSignalRules.endedInOrderlyReboot(null, 42))
        assertFalse(ShutdownSignalRules.endedInOrderlyReboot(-1, 42))
        assertFalse(ShutdownSignalRules.endedInOrderlyReboot(0, 1))
        assertFalse(ShutdownSignalRules.endedInOrderlyReboot(-2, -1)) // unreadable boot count
        assertFalse(ShutdownSignalRules.endedInOrderlyReboot(41, -1))
    }

    @Test
    fun `only a mark of the running kernel boot is cleared at arming`() {
        assertTrue(ShutdownSignalRules.clearAtArm(mark(42, idB), idB))
        assertTrue(ShutdownSignalRules.clearAtArm(mark(41, idB), idB))
        // The previous boot's mark is what this boot reads, whatever boot count a process of this boot sees.
        assertFalse(ShutdownSignalRules.clearAtArm(mark(41, idA), idB))
        assertFalse(ShutdownSignalRules.clearAtArm(mark(42, idA), idB))
        assertFalse(ShutdownSignalRules.clearAtArm(ShutdownSignalRules.Read.None, idB))
        assertFalse(ShutdownSignalRules.clearAtArm(ShutdownSignalRules.Read.Malformed, idB))
        assertFalse(ShutdownSignalRules.clearAtArm(mark(42, idB), null))
    }

    @Test
    fun `a boot count not raised for a new kernel boot is stale`() {
        assertTrue(ShutdownSignalRules.countIsStale(idA, 41, idB, 41)) // new boot, same count: not raised yet
        assertTrue(ShutdownSignalRules.countIsStale(idA, 41, idB, 40))
        assertFalse(ShutdownSignalRules.countIsStale(idA, 41, idB, 42))
        assertFalse(ShutdownSignalRules.countIsStale(idA, 41, idB, 45)) // boots without Norypt armed in between
        assertFalse(ShutdownSignalRules.countIsStale(idB, 42, idB, 42)) // a later arming in the same boot
        assertFalse(ShutdownSignalRules.countIsStale(null, 0, idB, 42)) // first arming ever
        assertFalse(ShutdownSignalRules.countIsStale("garbage", 99, idB, 42))
    }

    // ── with C9's decision ──

    /** No ACTION_SHUTDOWN record is 0 here ([RestartRecord.cleanBoot]). */
    private fun decide(shutdownMark: Int, signalMark: Int?, boot: Int = 42, pending: Boolean = false) =
        ForcedRestartRule.decide(
            watchedBoot = boot - 1,
            currentBoot = boot,
            cleanBoot = CleanShutdownRule.cleanBoot(shutdownMark, signalMark, boot),
            pending = pending,
        )

    @Test
    fun `an auto-reboot is a normal restart`() {
        // GrapheneOS auto-reboot: no ACTION_SHUTDOWN, init's SIGTERM marked the previous boot.
        assertEquals(Action.NONE, decide(shutdownMark = 0, signalMark = 41))
        assertEquals(Action.NONE, decide(shutdownMark = 30, signalMark = 41))
    }

    @Test
    fun `a held power button still starts the countdown`() {
        // Hardware reset: neither record names the previous boot.
        assertEquals(Action.COUNTDOWN, decide(shutdownMark = 0, signalMark = null))
        assertEquals(Action.COUNTDOWN, decide(shutdownMark = 40, signalMark = 40))
        assertEquals(Action.COUNTDOWN, decide(shutdownMark = 0, signalMark = -1)) // unreadable mark
        // An auto-reboot, then a held power button in the boot after it: the old mark vouches for nothing.
        assertEquals(Action.COUNTDOWN, decide(shutdownMark = 0, signalMark = 41, boot = 43))
    }

    @Test
    fun `a power-menu restart stays clean`() {
        assertEquals(Action.NONE, decide(shutdownMark = 41, signalMark = null))
        assertEquals(Action.NONE, decide(shutdownMark = 41, signalMark = 41))
        assertEquals(Action.NONE, decide(shutdownMark = 41, signalMark = -1))
    }

    @Test
    fun `a restart while the countdown is pending still wipes after an orderly reboot`() {
        assertEquals(Action.WIPE, decide(shutdownMark = 0, signalMark = 41, pending = true))
        assertEquals(Action.WIPE, decide(shutdownMark = 41, signalMark = 41, pending = true))
    }

    @Test
    fun `cleanBoot passes the shutdown record through when the signal says nothing`() {
        assertEquals(41, CleanShutdownRule.cleanBoot(0, 41, 42))
        assertEquals(30, CleanShutdownRule.cleanBoot(30, 40, 42))
        assertEquals(0, CleanShutdownRule.cleanBoot(0, null, 42))
        assertEquals(0, CleanShutdownRule.cleanBoot(0, -1, 42))
    }

    @Test
    fun `the file written at an auto-reboot keeps the next boot clean`() {
        val written = b(ShutdownSignalRules.record(41, idA)!!)
        val vouched = ShutdownSignalRules.vouchedBoot(ShutdownSignalRules.parse(written), idB)
        assertEquals(Action.NONE, decide(shutdownMark = 0, signalMark = vouched))
        // Read again in a later boot that a held power button started: it vouches for nothing there.
        val later = ShutdownSignalRules.vouchedBoot(
            ShutdownSignalRules.parse(written),
            "11111111-2222-3333-4444-555555555555",
        )
        assertEquals(Action.COUNTDOWN, decide(shutdownMark = 0, signalMark = later, boot = 43))
        // Read in the boot that wrote it (another process, no reboot yet): nothing.
        assertNull(ShutdownSignalRules.vouchedBoot(ShutdownSignalRules.parse(written), idA))
    }
}
