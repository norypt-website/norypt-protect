package com.norypt.protect.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionDetectorTest {

    private val t0 = 100_000L
    private val warm = MotionDetector.WARMUP_MS

    private fun detector() = MotionDetector(jerkThreshold = 22f).apply { reset(t0) }

    @Test
    fun `nothing fires during the warm-up window`() {
        val d = detector()
        assertNull(d.onLinearAcceleration(t0 + 10, 40f))
        assertNull(d.onLinearAcceleration(t0 + 30, 40f))
        assertNull(d.onAcceleration(t0 + 10, 0f))
        assertNull(d.onAcceleration(t0 + 500, 0f))
    }

    @Test
    fun `two consecutive samples over the threshold are a snatch`() {
        val d = detector()
        assertNull(d.onLinearAcceleration(t0 + warm, 25f))
        assertEquals(MotionDetector.Event.SNATCH, d.onLinearAcceleration(t0 + warm + 20, 25f))
    }

    @Test
    fun `a single spike is not a snatch`() {
        val d = detector()
        assertNull(d.onLinearAcceleration(t0 + warm, 40f))
        assertNull(d.onLinearAcceleration(t0 + warm + 20, 5f))
        assertNull(d.onLinearAcceleration(t0 + warm + 40, 40f))
        assertNull(d.onLinearAcceleration(t0 + warm + 60, 5f))
    }

    @Test
    fun `ordinary handling stays below the threshold`() {
        val d = detector()
        var t = t0 + warm
        repeat(200) {
            assertNull(d.onLinearAcceleration(t, 4f))
            assertNull(d.onAcceleration(t, 9.8f))
            t += 20
        }
    }

    @Test
    fun `sustained near-zero acceleration is a drop`() {
        val d = detector()
        assertNull(d.onAcceleration(t0 + warm, 0.5f))
        assertNull(d.onAcceleration(t0 + warm + 50, 0.5f))
        assertEquals(MotionDetector.Event.DROP, d.onAcceleration(t0 + warm + 100, 0.5f))
    }

    @Test
    fun `a brief dip is not a drop`() {
        val d = detector()
        assertNull(d.onAcceleration(t0 + warm, 0.5f))
        assertNull(d.onAcceleration(t0 + warm + 40, 0.5f))
        assertNull(d.onAcceleration(t0 + warm + 60, 9.8f))
        // The free-fall window restarted; another 60 ms is still short of the minimum.
        assertNull(d.onAcceleration(t0 + warm + 80, 0.5f))
        assertNull(d.onAcceleration(t0 + warm + 120, 0.5f))
    }

    @Test
    fun `nothing fires again during the cooldown`() {
        val d = detector()
        d.onLinearAcceleration(t0 + warm, 30f)
        assertEquals(MotionDetector.Event.SNATCH, d.onLinearAcceleration(t0 + warm + 20, 30f))

        assertNull(d.onLinearAcceleration(t0 + warm + 40, 30f))
        assertNull(d.onLinearAcceleration(t0 + warm + 60, 30f))
        assertNull(d.onAcceleration(t0 + warm + 80, 0f))
        assertNull(d.onAcceleration(t0 + warm + 400, 0f))

        val after = t0 + warm + 20 + MotionDetector.COOLDOWN_MS
        assertNull(d.onLinearAcceleration(after, 30f))
        assertEquals(MotionDetector.Event.SNATCH, d.onLinearAcceleration(after + 20, 30f))
    }

    @Test
    fun `reset restarts the warm-up`() {
        val d = detector()
        d.onLinearAcceleration(t0 + warm, 30f)
        d.reset(t0 + warm + 20)
        assertNull(d.onLinearAcceleration(t0 + warm + 40, 30f))
        assertNull(d.onLinearAcceleration(t0 + warm + 60, 30f))
    }

    @Test
    fun `sensitivity presets order the thresholds`() {
        val low = MotionDetector.jerkThresholdFor(MotionDetector.SENSITIVITY_LOW)
        val medium = MotionDetector.jerkThresholdFor(MotionDetector.SENSITIVITY_MEDIUM)
        val high = MotionDetector.jerkThresholdFor(MotionDetector.SENSITIVITY_HIGH)
        assertTrue(low > medium && medium > high)
        assertEquals(medium, MotionDetector.jerkThresholdFor(99))
    }
}
