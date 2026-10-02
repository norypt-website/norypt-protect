package com.norypt.protect.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownResumeTest {

    private val now = 1_000_000L
    private val grace = 60_000L
    private val boot = 42

    @Test
    fun `a first countdown gets the full grace period`() {
        assertEquals(now + grace, CountdownResume.deadline(0L, 0, now, boot, grace))
    }

    @Test
    fun `a reopened countdown keeps its deadline instead of restarting the grace period`() {
        val saved = now + 20_000L
        assertEquals(saved, CountdownResume.deadline(saved, boot, now, boot, grace))
    }

    @Test
    fun `a deadline that passed while closed still shows briefly before the wipe`() {
        val saved = now - 5_000L
        assertEquals(now + CountdownResume.MIN_RESUME_MS, CountdownResume.deadline(saved, boot, now, boot, grace))
    }

    @Test
    fun `a deadline from before a reboot is meaningless and starts over`() {
        assertEquals(now + grace, CountdownResume.deadline(now + 5_000L, boot - 1, now, boot, grace))
    }

    @Test
    fun `a stored deadline further out than the grace period is clamped`() {
        assertEquals(now + grace, CountdownResume.deadline(now + 10 * grace, boot, now, boot, grace))
    }
}
