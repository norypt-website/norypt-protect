package com.norypt.protect.util

/**
 * Deadline-driven countdown that may be suspended for a bounded total time.
 *
 * Used by the dead-man grace period, where both failure directions are costly:
 *
 * - Running while the user authenticates to cancel wipes a device mid-cancellation
 *   (false positive), so suspension has to exist at all.
 * - Suspending without a cap hands an attacker a trivial disable: open the credential
 *   prompt and walk away, and the wipe never fires (false negative). So the suspension
 *   budget is finite, and once spent the countdown runs regardless.
 *
 * State is a deadline rather than a remaining-seconds counter so it can be carried across
 * an Activity recreation — otherwise rotating the phone restarts the grace period, which is
 * the same false negative by another route.
 *
 * All timestamps must come from a monotonic source ([android.os.SystemClock.elapsedRealtime]).
 */
class SuspendableCountdown(
    startElapsedMs: Long,
    graceMs: Long,
    private val maxPauseMs: Long,
    pauseUsedMs: Long = 0L,
    deadlineElapsedMs: Long = startElapsedMs + graceMs,
) {
    var deadlineElapsedMs: Long = deadlineElapsedMs
        private set

    var pauseUsedMs: Long = pauseUsedMs
        private set

    private var lastTickMs: Long = startElapsedMs

    val pauseBudgetRemainingMs: Long get() = (maxPauseMs - pauseUsedMs).coerceAtLeast(0L)

    /**
     * Advances the clock to [nowMs] and returns the milliseconds left, 0 meaning expired.
     * While [paused], the deadline is pushed out by the elapsed interval, but only for as
     * long as the pause budget lasts.
     */
    fun advance(nowMs: Long, paused: Boolean): Long {
        // A monotonic clock cannot go backwards, but clamping keeps a bad caller from
        // being able to rewind the deadline.
        val delta = (nowMs - lastTickMs).coerceAtLeast(0L)
        lastTickMs = nowMs
        if (paused) {
            val granted = minOf(delta, pauseBudgetRemainingMs)
            pauseUsedMs += granted
            deadlineElapsedMs += granted
        }
        return (deadlineElapsedMs - nowMs).coerceAtLeast(0L)
    }

    fun remainingMs(nowMs: Long): Long = (deadlineElapsedMs - nowMs).coerceAtLeast(0L)

    fun isExpired(nowMs: Long): Boolean = remainingMs(nowMs) == 0L
}

/**
 * Where a wipe countdown resumes when its screen comes back without having reached an
 * outcome (cancelled with the credential, conditions cleared, or expired).
 *
 * Without a stored deadline every relaunch restarted the full grace period, so closing the
 * screen once a minute postponed the wipe forever.
 */
object CountdownResume {

    /** Shortest time a resumed countdown is shown, so a closed screen never becomes an unseen wipe. */
    const val MIN_RESUME_MS = 10_000L

    /**
     * @param savedDeadline elapsedRealtime deadline stored by the previous screen, 0 if none.
     * @param savedBoot boot count when it was stored; elapsedRealtime restarts at every boot.
     */
    fun deadline(savedDeadline: Long, savedBoot: Int, nowElapsed: Long, bootCount: Int, graceMs: Long): Long =
        when {
            savedDeadline <= 0L || savedBoot != bootCount -> nowElapsed + graceMs
            savedDeadline - nowElapsed > graceMs -> nowElapsed + graceMs
            else -> maxOf(savedDeadline, nowElapsed + MIN_RESUME_MS)
        }
}
