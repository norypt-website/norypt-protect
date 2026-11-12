package com.norypt.protect.util

import android.os.SystemClock

/**
 * The first minutes after power-on. The triggers now run from boot, before the first unlock, while
 * the radios and the USB port are still coming up and report passing states: no connection, a
 * data function set up for a cable that was already plugged in. Read as evidence, they would start
 * a countdown, or a wipe, right after an ordinary restart. A condition that still holds afterwards
 * fires then, so a restart buys whoever holds the phone only these minutes.
 */
object StartupGrace {

    /** No new C4 or C6 countdown starts before this much uptime. */
    const val COUNTDOWN_MS = 5 * 60_000L

    /** A9 ignores the cable that was already plugged in at boot for this much uptime. */
    const val USB_MS = 2 * 60_000L

    fun within(uptimeMs: Long, graceMs: Long): Boolean = uptimeMs in 0 until graceMs

    fun countdownsHeld(): Boolean = within(SystemClock.elapsedRealtime(), COUNTDOWN_MS)

    fun usbHeld(): Boolean = within(SystemClock.elapsedRealtime(), USB_MS)
}
