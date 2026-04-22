package com.norypt.protect.util

/**
 * Counts discrete events within a rolling time window.
 * Returns true from [onEvent] when [threshold] events have occurred within [windowMs] milliseconds.
 */
class GestureCounter(private val threshold: Int, private val windowMs: Long) {
    private val events = ArrayDeque<Long>()

    /** Record an event at [nowMs] (epoch ms). Returns true if threshold reached within window. */
    fun onEvent(nowMs: Long): Boolean {
        events.addLast(nowMs)
        while (events.isNotEmpty() && nowMs - events.first() > windowMs) {
            events.removeFirst()
        }
        return events.size >= threshold
    }

    /** Clear all recorded events. */
    fun reset() {
        events.clear()
    }
}
