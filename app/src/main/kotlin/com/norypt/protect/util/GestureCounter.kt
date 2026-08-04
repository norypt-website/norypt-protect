package com.norypt.protect.util

/**
 * Counts discrete events within a rolling time window.
 * Returns true from [onEvent] when [threshold] events have occurred within [windowMs] milliseconds.
 */
class GestureCounter(private val threshold: Int, private val windowMs: Long) {
    private val events = ArrayDeque<Long>()

    /**
     * Record an event at [nowMs], which must come from a monotonic source such as
     * [android.os.SystemClock.elapsedRealtime]. Returns true if threshold reached within window.
     */
    fun onEvent(nowMs: Long): Boolean {
        // A backwards time step makes `nowMs - first` negative, so no entry would ever age
        // out of the window and unrelated events would accumulate until they hit the
        // threshold. Callers pass a monotonic clock; discard history if one goes backwards
        // anyway, because reaching the threshold here fires an irreversible wipe.
        if (events.isNotEmpty() && nowMs < events.last()) {
            events.clear()
        }
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
