package com.norypt.protect.motion

/**
 * Recognises the two motion signatures of a phone leaving its owner's hand, from raw
 * accelerometer magnitudes. Pure: no Android types, so the thresholds are unit-tested.
 *
 * - **Snatch**: linear acceleration (gravity removed) above [jerkThreshold] on
 *   [JERK_CONSECUTIVE_SAMPLES] consecutive samples. Two samples filter the single-sample
 *   spikes a tap on the table produces.
 * - **Drop**: gravity-inclusive magnitude below [freeFallThreshold] for at least
 *   [freeFallMinMs]. A phone in free fall reads close to zero; 100 ms is a fall of a few
 *   centimetres, enough to exclude a hand dipping.
 *
 * Samples in the first [warmupMs] after [reset] are ignored (sensor settling), and nothing
 * fires for [cooldownMs] after an event.
 *
 * All timestamps are milliseconds from one monotonic source.
 */
class MotionDetector(
    private val jerkThreshold: Float,
    private val freeFallThreshold: Float = FREE_FALL_THRESHOLD,
    private val freeFallMinMs: Long = FREE_FALL_MIN_MS,
    private val cooldownMs: Long = COOLDOWN_MS,
    private val warmupMs: Long = WARMUP_MS,
) {
    enum class Event { SNATCH, DROP }

    private var startedAtMs = Long.MIN_VALUE
    private var lastEventMs = Long.MIN_VALUE
    private var jerkSamples = 0
    private var freeFallSinceMs = Long.MIN_VALUE

    /** Call when the sensor is (re)registered; the warm-up window starts here. */
    fun reset(nowMs: Long) {
        startedAtMs = nowMs
        jerkSamples = 0
        freeFallSinceMs = Long.MIN_VALUE
    }

    /** Feed one linear-acceleration sample (gravity removed), magnitude in m/s². */
    fun onLinearAcceleration(nowMs: Long, magnitude: Float): Event? {
        if (!ready(nowMs)) return null
        if (magnitude >= jerkThreshold) {
            jerkSamples++
            if (jerkSamples >= JERK_CONSECUTIVE_SAMPLES) return fire(nowMs, Event.SNATCH)
        } else {
            jerkSamples = 0
        }
        return null
    }

    /** Feed one accelerometer sample (gravity included), magnitude in m/s². */
    fun onAcceleration(nowMs: Long, magnitude: Float): Event? {
        if (!ready(nowMs)) return null
        if (magnitude < freeFallThreshold) {
            if (freeFallSinceMs == Long.MIN_VALUE) {
                freeFallSinceMs = nowMs
            } else if (nowMs - freeFallSinceMs >= freeFallMinMs) {
                return fire(nowMs, Event.DROP)
            }
        } else {
            freeFallSinceMs = Long.MIN_VALUE
        }
        return null
    }

    private fun ready(nowMs: Long): Boolean {
        if (startedAtMs == Long.MIN_VALUE) startedAtMs = nowMs
        if (nowMs - startedAtMs < warmupMs) return false
        return lastEventMs == Long.MIN_VALUE || nowMs - lastEventMs >= cooldownMs
    }

    private fun fire(nowMs: Long, event: Event): Event {
        lastEventMs = nowMs
        jerkSamples = 0
        freeFallSinceMs = Long.MIN_VALUE
        return event
    }

    companion object {
        const val JERK_CONSECUTIVE_SAMPLES = 2
        const val FREE_FALL_THRESHOLD = 3f
        const val FREE_FALL_MIN_MS = 100L
        const val COOLDOWN_MS = 5_000L
        const val WARMUP_MS = 1_000L

        const val SENSITIVITY_LOW = 0
        const val SENSITIVITY_MEDIUM = 1
        const val SENSITIVITY_HIGH = 2

        /** Linear acceleration a yank must reach, per sensitivity preset. */
        fun jerkThresholdFor(sensitivity: Int): Float = when (sensitivity) {
            SENSITIVITY_LOW -> 30f
            SENSITIVITY_HIGH -> 15f
            else -> 22f
        }
    }
}
