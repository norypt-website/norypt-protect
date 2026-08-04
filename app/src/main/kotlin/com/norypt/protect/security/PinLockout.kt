package com.norypt.protect.security

import android.content.Context
import android.os.SystemClock
import com.norypt.protect.prefs.KvStore
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.prefs.ProtectPrefsKeys

/**
 * Throttles PIN entry on the launch gate.
 *
 * The attempt count has to be persisted rather than held in composition state: a rotation
 * destroys and recreates the Activity, and force-stopping clears the process, so an
 * in-memory counter is defeated by exactly the action the lockout message used to suggest.
 *
 * The lockout expires on its own instead of being permanent. A hard lock would leave the
 * legitimate owner unable to reach their own trigger configuration, and the derivation cost
 * in [AppPin] already makes exhaustive guessing impractical — the point here is to bound the
 * rate, not to brick the app.
 */
object PinLockout {

    const val MAX_ATTEMPTS = 8
    const val LOCKOUT_MS = 5 * 60_000L

    /**
     * Milliseconds left before PIN entry is allowed again; 0 when entry is permitted.
     *
     * Two independent deadlines are stored and the lockout holds until **both** expire,
     * because each clock alone is bypassable:
     *
     * - Wall clock alone: moving the system date forward clears the lockout instantly —
     *   the same class of bug as the power-gesture trigger reading `currentTimeMillis`,
     *   and here it hands an attacker unlimited PIN attempts.
     * - Monotonic clock alone: `elapsedRealtime` resets to near zero on reboot, so
     *   power-cycling the phone clears the lockout.
     *
     * Either deadline is discarded if it sits further out than a whole lockout period,
     * which is what a backwards wall-clock step, or a reboot, looks like on that clock.
     */
    internal fun remainingLockoutMs(store: KvStore, nowWallMs: Long, nowElapsedMs: Long): Long {
        val wall = remainingOn(ProtectPrefsKeys.gateLockedUntilMs(store), nowWallMs)
        val elapsed = remainingOn(ProtectPrefsKeys.gateLockedUntilElapsedMs(store), nowElapsedMs)
        return maxOf(wall, elapsed)
    }

    private fun remainingOn(until: Long, now: Long): Long {
        if (until <= 0L) return 0L
        val remaining = until - now
        if (remaining <= 0L || remaining > LOCKOUT_MS) return 0L
        return remaining
    }

    internal fun isLockedOut(store: KvStore, nowWallMs: Long, nowElapsedMs: Long): Boolean =
        remainingLockoutMs(store, nowWallMs, nowElapsedMs) > 0L

    /**
     * Records a failed PIN entry and returns the resulting attempt count. Starting a
     * lockout resets the counter, so each lockout costs a fresh [MAX_ATTEMPTS] run.
     */
    internal fun recordFailure(store: KvStore, nowWallMs: Long, nowElapsedMs: Long): Int {
        val attempts = ProtectPrefsKeys.gateAttempts(store) + 1
        if (attempts >= MAX_ATTEMPTS) {
            ProtectPrefsKeys.setGateAttempts(store, 0)
            ProtectPrefsKeys.setGateLockedUntilMs(store, nowWallMs + LOCKOUT_MS)
            ProtectPrefsKeys.setGateLockedUntilElapsedMs(store, nowElapsedMs + LOCKOUT_MS)
        } else {
            ProtectPrefsKeys.setGateAttempts(store, attempts)
        }
        return attempts
    }

    /** Clears throttle state after a successful unlock. */
    internal fun recordSuccess(store: KvStore) {
        ProtectPrefsKeys.setGateAttempts(store, 0)
        ProtectPrefsKeys.setGateLockedUntilMs(store, 0L)
        ProtectPrefsKeys.setGateLockedUntilElapsedMs(store, 0L)
    }

    internal fun attempts(store: KvStore): Int = ProtectPrefsKeys.gateAttempts(store)

    // --- Context-based entry points used by the UI ---

    fun remainingLockoutMs(context: Context): Long =
        remainingLockoutMs(
            ProtectPrefs.store(context),
            System.currentTimeMillis(),
            SystemClock.elapsedRealtime(),
        )

    fun recordFailure(context: Context): Int =
        recordFailure(
            ProtectPrefs.store(context),
            System.currentTimeMillis(),
            SystemClock.elapsedRealtime(),
        )

    fun recordSuccess(context: Context) = recordSuccess(ProtectPrefs.store(context))

    fun attempts(context: Context): Int = attempts(ProtectPrefs.store(context))
}
