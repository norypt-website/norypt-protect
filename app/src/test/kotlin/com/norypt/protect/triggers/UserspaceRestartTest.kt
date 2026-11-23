package com.norypt.protect.triggers

import com.norypt.protect.triggers.ForcedRestartRule.Action
import com.norypt.protect.util.CountdownResume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** C9 and the elapsedRealtime stamps with the kernel boot_id as the boot identity (fix 1.3.6). */
class UserspaceRestartTest {

    private val k1 = "62ed2879-f806-4dba-a52c-5b1d1c8fbd83" // the watched kernel
    private val k2 = "0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9" // a later kernel

    /** No ACTION_SHUTDOWN record is 0 here, no orderly-reboot mark null ([CleanShutdownRule.cleanBoot]). */
    private fun decide(
        watched: Int,
        current: Int,
        kernel: KernelIds,
        shutdownMark: Int = 0,
        signalMark: Int? = null,
        pending: Boolean = false,
    ) = ForcedRestartRule.decide(
        watchedBoot = watched,
        currentBoot = current,
        cleanBoot = CleanShutdownRule.cleanBoot(shutdownMark, signalMark, current),
        pending = pending,
        kernel = kernel,
    )

    // ── the decision ──

    @Test
    fun `a userspace restart starts nothing`() {
        // system_server / zygote restarted: count +1, same kernel, no ACTION_SHUTDOWN, no SIGTERM mark.
        assertEquals(Action.NONE, decide(41, 42, KernelIds(watched = k1, current = k1)))
        // Several in a row, or one the app missed: still the same kernel.
        assertEquals(Action.NONE, decide(41, 44, KernelIds(watched = k1, current = k1)))
    }

    @Test
    fun `a held power button still starts the countdown`() {
        assertEquals(Action.COUNTDOWN, decide(41, 42, KernelIds(watched = k1, current = k2)))
        // A stale mark of an earlier boot changes nothing.
        assertEquals(
            Action.COUNTDOWN,
            decide(41, 42, KernelIds(watched = k1, current = k2, cleanShutdown = k1, cleanShutdownBoot = 40), shutdownMark = 40),
        )
    }

    @Test
    fun `a new kernel with an equal count is never the same boot`() {
        assertEquals(Action.COUNTDOWN, decide(41, 41, KernelIds(watched = k1, current = k2)))
        assertEquals(Action.COUNTDOWN, decide(41, 40, KernelIds(watched = k1, current = k2)))
        // A pending countdown is not escaped that way either.
        assertEquals(Action.WIPE, decide(41, 41, KernelIds(watched = k1, current = k2), pending = true))
        // An old clean mark of the boot before the watched one does not vouch for it (the count was read too early).
        assertEquals(Action.COUNTDOWN, decide(41, 41, KernelIds(watched = k1, current = k2), shutdownMark = 40))
        assertEquals(Action.COUNTDOWN, decide(41, 40, KernelIds(watched = k1, current = k2), shutdownMark = 39))
        assertEquals(Action.COUNTDOWN, decide(41, 41, KernelIds(watched = k1, current = k2), signalMark = 40))
        // Without the kernel ids (unknown) the boot count decides, as in 1.3.5.
        assertEquals(Action.NONE, decide(41, 41, KernelIds.UNKNOWN))
    }

    @Test
    fun `a mark written by the watched boot itself vouches`() {
        // ACTION_SHUTDOWN (power-menu restart) of boot 41 in kernel k1.
        val shutdown = KernelIds(watched = k1, current = k2, cleanShutdown = k1, cleanShutdownBoot = 41)
        assertEquals(Action.NONE, decide(41, 42, shutdown, shutdownMark = 41))
        // init's SIGTERM (adb reboot, GrapheneOS auto-reboot) of boot 41 in kernel k1.
        val signal = KernelIds(watched = k1, current = k2, signalMark = k1, signalMarkBoot = 41)
        assertEquals(Action.NONE, decide(41, 42, signal, signalMark = 41))
        // With a count that did not go up, the mark decides.
        assertEquals(Action.NONE, decide(41, 41, signal))
        // A boot Norypt missed in between may have been the forced one: the mark vouches only for the next boot.
        assertEquals(Action.COUNTDOWN, decide(41, 43, signal))
    }

    @Test
    fun `a mark vouches only for the watched kernel and count`() {
        // Right count, another kernel's id.
        val k3 = "11111111-2222-3333-4444-555555555555"
        assertFalse(KernelIds(watched = k1, current = k2, cleanShutdown = k3, cleanShutdownBoot = 41).anyMarkVouchesFor(41))
        // Right kernel, another count.
        assertFalse(KernelIds(watched = k1, current = k2, signalMark = k1, signalMarkBoot = 40).anyMarkVouchesFor(41))
        // The running kernel's own mark: no reboot followed it.
        assertFalse(KernelIds(watched = k2, current = k2, signalMark = k2, signalMarkBoot = 41).anyMarkVouchesFor(41))
        // Ids unknown: nothing vouches through the kernel rule.
        assertFalse(KernelIds(watched = null, current = k2, signalMark = k1, signalMarkBoot = 41).anyMarkVouchesFor(41))
        assertTrue(KernelIds(watched = k1, current = k2, signalMark = k1, signalMarkBoot = 41).anyMarkVouchesFor(41))
    }

    @Test
    fun `unreadable ids fall back to the boot-count rule`() {
        for (kernel in listOf(
            KernelIds.UNKNOWN,
            KernelIds(watched = k1, current = null),
            KernelIds(watched = null, current = k1),
            KernelIds(watched = "garbage", current = "garbage"),
        )) {
            assertEquals(Action.COUNTDOWN, decide(41, 42, kernel))
            assertEquals(Action.NONE, decide(41, 42, kernel, shutdownMark = 41))
            assertEquals(Action.NONE, decide(41, 42, kernel, signalMark = 41))
            assertEquals(Action.NONE, decide(42, 42, kernel))
            assertEquals(Action.WIPE, decide(41, 42, kernel, pending = true))
        }
    }

    @Test
    fun `a mark of an unfinished shutdown never vouches after a userspace restart`() {
        // Boot 41 (k1) began a shutdown that never finished and wrote its mark; Android then restarted in the same
        // kernel (boot 42, watched now), and later the power button was held (boot 43, k2).
        val kernel = KernelIds(watched = k1, current = k2, cleanShutdown = k1, cleanShutdownBoot = 41, signalMark = k1, signalMarkBoot = 41)
        assertEquals(Action.COUNTDOWN, decide(42, 43, kernel, shutdownMark = 41))
        assertEquals(Action.COUNTDOWN, decide(42, 43, kernel, signalMark = 41))
    }

    @Test
    fun `a pending countdown still wipes after a userspace restart`() {
        assertEquals(Action.WIPE, decide(41, 42, KernelIds(watched = k1, current = k1), pending = true))
        // A service restart inside the same boot decides nothing (the tick carries the countdown on).
        assertEquals(Action.NONE, decide(42, 42, KernelIds(watched = k1, current = k1), pending = true))
    }

    @Test
    fun `nothing is decided before a boot was ever watched`() {
        assertEquals(Action.NONE, decide(0, 42, KernelIds(watched = null, current = k1)))
        // A mistyped watched boot reads as -1: a pending countdown still wipes.
        assertEquals(Action.WIPE, decide(-1, 42, KernelIds.UNKNOWN, pending = true))
        assertEquals(Action.NONE, decide(-1, 42, KernelIds.UNKNOWN))
    }

    // ── the stored pairs ──

    @Test
    fun `a paired id is trusted only beside its count`() {
        val v = PairedBootId.encode(41, k1)!!
        assertEquals("41 $k1", v)
        assertEquals(k1, PairedBootId.decode(v, 41))
        assertNull(PairedBootId.decode(v, 42)) // a newer count written by 1.3.5 next to an old id
        assertNull(PairedBootId.decode(v, 40))
        assertNull(PairedBootId.decode(v, null))
        assertNull(PairedBootId.decode(v, 0))
        assertNull(PairedBootId.decode(k1, 41)) // an id with no count
        assertNull(PairedBootId.decode(41, 41)) // mistyped
        for (bad in listOf("041 $k1", "41  $k1", "41 ${k1.uppercase()}", "-41 $k1", "41 $k1\n", "2147483648 $k1", " $k1")) {
            assertNull(bad, PairedBootId.parse(bad))
        }
        assertNull(PairedBootId.encode(0, k1))
        assertNull(PairedBootId.encode(41, null))
        assertNull(PairedBootId.encode(41, "garbage"))
    }

    @Test
    fun `a new kernel with an unraised count is recorded without its id`() {
        val newKernel = KernelIds(watched = k1, current = k2)
        assertEquals(WatchedBoot(41, null), WatchedBoot.record(41, k2, previousBoot = 41, kernel = newKernel))
        assertEquals(WatchedBoot(40, null), WatchedBoot.record(40, k2, previousBoot = 41, kernel = newKernel))
        assertEquals(WatchedBoot(42, k2), WatchedBoot.record(42, k2, previousBoot = 41, kernel = newKernel))
        // Same kernel, or nothing known about the previous one: recorded with its id.
        assertEquals(WatchedBoot(42, k1), WatchedBoot.record(42, k1, previousBoot = 41, kernel = KernelIds(watched = k1, current = k1)))
        assertEquals(WatchedBoot(42, k2), WatchedBoot.record(42, k2, previousBoot = 0, kernel = KernelIds(current = k2)))
        // Then the start that reads the raised count is not taken for a userspace restart: the count rule decides.
        assertEquals(Action.COUNTDOWN, decide(41, 42, KernelIds(watched = null, current = k2)))
    }

    @Test
    fun `a clean mark of the running kernel is voided`() {
        // At a service start: Android came back in the kernel that wrote it.
        assertTrue(CleanMarkRule.void(k1, 5_000L, k1, nowElapsedMs = 6_000L, minAgeMs = 0L))
        // On the tick: only once the process outlived it by ten minutes, or when its time cannot be this kernel's.
        val max = CleanMarkRule.MAX_AGE_MS
        assertFalse(CleanMarkRule.void(k1, 5_000L, k1, nowElapsedMs = 5_000L + max - 1, minAgeMs = max))
        assertTrue(CleanMarkRule.void(k1, 5_000L, k1, nowElapsedMs = 5_000L + max, minAgeMs = max))
        assertTrue(CleanMarkRule.void(k1, null, k1, nowElapsedMs = 6_000L, minAgeMs = max))
        assertTrue(CleanMarkRule.void(k1, 9_000L, k1, nowElapsedMs = 6_000L, minAgeMs = max))
        // A mark of another kernel is the previous boot's record: never voided.
        assertFalse(CleanMarkRule.void(k1, 5_000L, k2, nowElapsedMs = 6_000L, minAgeMs = 0L))
        assertFalse(CleanMarkRule.void(null, 5_000L, k2, nowElapsedMs = 6_000L, minAgeMs = 0L))
        // The running id unreadable: nothing can be told apart, nothing is voided (the count rule decides).
        assertFalse(CleanMarkRule.void(k1, 5_000L, null, nowElapsedMs = 6_000L, minAgeMs = 0L))
    }

    // ── the debug line ──

    @Test
    fun `the start label holds labels only`() {
        val kernels = listOf(
            KernelIds.UNKNOWN,
            KernelIds(watched = k1, current = k1),
            KernelIds(watched = k1, current = k2, signalMark = k1, signalMarkBoot = 1234567),
        )
        for (kernel in kernels) for (w in listOf(0, 41, 42)) for (c in listOf(41, 42, 43)) {
            for (armed in listOf(true, false)) for (action in Action.values()) {
                val label = StartLabel.of(w, c, kernel, armed, action)
                assertFalse(label, label.any { it.isDigit() })
                assertFalse(label, label.contains(k1) || label.contains(k2))
                assertTrue(label, label.startsWith("start: "))
            }
        }
        assertEquals(
            "start: userspace restart (same kernel), decision none",
            StartLabel.of(41, 42, KernelIds(watched = k1, current = k1), armed = true, Action.NONE),
        )
        assertEquals(
            "start: restart without a clean mark, new kernel, decision countdown",
            StartLabel.of(41, 42, KernelIds(watched = k1, current = k2), armed = true, Action.COUNTDOWN),
        )
    }

    // ── the clock boot of elapsedRealtime stamps ──

    @Test
    fun `the clock boot stays across userspace restarts and changes with the kernel`() {
        // First read in kernel k1 at boot 41: stored.
        val first = ClockBootRule.resolve(null, 41, k1)
        assertEquals(ClockBootRule.Result(41, "41 $k1", known = true), first)
        // Userspace restarts in k1: the clock boot stays 41.
        assertEquals(ClockBootRule.Result(41, null, known = true), ClockBootRule.resolve(first.store, 42, k1))
        assertEquals(ClockBootRule.Result(41, null, known = true), ClockBootRule.resolve(first.store, 45, k1))
        // A new kernel at boot 46: a new clock boot, stored.
        assertEquals(ClockBootRule.Result(46, "46 $k2", known = true), ClockBootRule.resolve(first.store, 46, k2))
    }

    @Test
    fun `the clock boot falls back to the boot count when it cannot be told`() {
        val stored = "41 $k1"
        // The boot_id unreadable.
        assertEquals(ClockBootRule.Result(43, null, known = false), ClockBootRule.resolve(stored, 43, null))
        // A new kernel whose count was not raised yet: not stored, so the raised count becomes its clock boot.
        assertEquals(ClockBootRule.Result(41, null, known = false), ClockBootRule.resolve(stored, 41, k2))
        assertEquals(ClockBootRule.Result(42, "42 $k2", known = true), ClockBootRule.resolve(stored, 42, k2))
        // A count that went down inside one kernel (set over adb).
        assertEquals(ClockBootRule.Result(40, null, known = false), ClockBootRule.resolve(stored, 40, k1))
        // No readable count at all.
        assertEquals(ClockBootRule.Result(0, null, known = false), ClockBootRule.resolve(stored, 0, k1))
        // A malformed stored value is replaced.
        assertEquals(ClockBootRule.Result(43, "43 $k1", known = true), ClockBootRule.resolve("garbage", 43, k1))
    }

    @Test
    fun `a userspace restart gives a running countdown no fresh grace`() {
        // A countdown was stored at clock boot 41 with 20 s left; Android restarted itself (BOOT_COUNT 42, same kernel).
        val clockBoot = ClockBootRule.resolve("41 $k1", 42, k1).clockBoot
        val resumed = CountdownResume.deadline(
            savedDeadline = 120_000L, savedBoot = 41, nowElapsed = 100_000L, bootCount = clockBoot, graceMs = 60_000L,
        )
        assertEquals(120_000L, resumed)
        // As 1.3.5 keyed it on BOOT_COUNT, the full grace came back.
        assertEquals(
            160_000L,
            CountdownResume.deadline(savedDeadline = 120_000L, savedBoot = 41, nowElapsed = 100_000L, bootCount = 42, graceMs = 60_000L),
        )
        // The C9 screen past its deadline is still wiped by the tick after it.
        assertTrue(ForcedRestartMonitor.overdue(deadline = 50_000L, deadlineBoot = 41, nowElapsed = 100_000L, bootCount = clockBoot))
    }

    @Test
    fun `the boot entry names a userspace restart only inside one kernel`() {
        // Clock boot 41 (k1), the Timeline last saw boot 41, now 42: Android restarted itself.
        assertTrue(ClockBootRule.userspaceRestartSince(clockBoot = 41, previousBoot = 41, currentBoot = 42))
        assertTrue(ClockBootRule.userspaceRestartSince(clockBoot = 41, previousBoot = 43, currentBoot = 44))
        // The last boot seen was in an earlier kernel.
        assertFalse(ClockBootRule.userspaceRestartSince(clockBoot = 42, previousBoot = 41, currentBoot = 42))
        // A fresh kernel (clock boot = the count) or a fallback.
        assertFalse(ClockBootRule.userspaceRestartSince(clockBoot = 42, previousBoot = 42, currentBoot = 42))
        assertFalse(ClockBootRule.userspaceRestartSince(clockBoot = 0, previousBoot = 41, currentBoot = 42))
    }

    @Test
    fun `a kernel recorded at its first start knows a later userspace restart, so the boot unlock stamp is skipped`() {
        val id = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0"
        val other = "1a2b3c4d-5e6f-7081-92a3-b4c5d6e7f809"
        // Recorded at the service start of boot 41, before anything was stamped (found on a phone: the Timeline
        // and the unlock stamp missed the restart when the kernel was first recorded after it).
        val first = ClockBootRule.resolve(null, 41, id)
        assertEquals(41, first.clockBoot)
        assertFalse(ClockBootRule.restartedInKernel(first.clockBoot, 41))
        // Android restarted itself: BOOT_COMPLETED comes again with nobody unlocking.
        val after = ClockBootRule.resolve(first.store, 42, id)
        assertEquals(41, after.clockBoot)
        assertTrue(ClockBootRule.restartedInKernel(after.clockBoot, 42))
        assertTrue(ClockBootRule.userspaceRestartSince(after.clockBoot, previousBoot = 41, currentBoot = 42))
        // A real boot: a new kernel, so the first unlock is stamped as before.
        val boot = ClockBootRule.resolve(after.store ?: first.store, 43, other)
        assertFalse(ClockBootRule.restartedInKernel(boot.clockBoot, 43))
        // Unreadable id: the fallback is the count itself, so nothing is skipped (as up to 1.9.1).
        assertFalse(ClockBootRule.restartedInKernel(ClockBootRule.resolve(first.store, 42, null).clockBoot, 42))
        assertFalse(ClockBootRule.restartedInKernel(0, 42))
    }
}
