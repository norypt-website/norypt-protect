package com.norypt.protect.security

/**
 * When the App PIN gate closes again after the app was left.
 *
 * It used to open once per process and stay open: open the app, press Home, hand the phone
 * over, and Recents brought back the unlocked screens, including the dry-run and trigger
 * switches. A short grace keeps a trip to a Settings page and back from asking again.
 */
object GateRelock {

    const val BACKGROUND_GRACE_MS = 30_000L

    /**
     * @param stoppedAtElapsed elapsedRealtime when the app left the screen, 0 if unknown.
     * @param screenWentOff whether the screen was off when it left, i.e. the phone was put away.
     */
    fun shouldRelock(stoppedAtElapsed: Long, nowElapsed: Long, screenWentOff: Boolean): Boolean =
        screenWentOff || stoppedAtElapsed <= 0L || nowElapsed - stoppedAtElapsed > BACKGROUND_GRACE_MS
}
