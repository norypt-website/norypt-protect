package com.norypt.protect.service

/**
 * Every notification id the app posts, in one place. Ids used to be chosen per file, and two
 * features sharing one replace each other's notification: the internet-permission alerts
 * counted up from 5000 into the countdown and wipe-failure ids.
 */
object NotificationIds {
    const val SERVICE = 1001
    const val COUNTDOWN_DEADMAN = 5001
    const val WIPE_FAILED = 5002
    const val COUNTDOWN_UNLOCK_DEADLINE = 5003
    const val COUNTDOWN_UNLOCKED_TOO_LONG = 5006
    const val PERMISSION_REVIEW = 5004

    /** One notification for failed unlocks, updated in place rather than one per attempt. */
    const val FAILED_UNLOCK = 5005

    private const val INTERNET_ALERT_BASE = 6000
    private const val INTERNET_ALERT_SLOTS = 0x3FF

    /** Internet-permission alerts get one stable id per app, in 6000..7023. */
    fun internetAlert(packageName: String): Int = INTERNET_ALERT_BASE + (packageName.hashCode() and INTERNET_ALERT_SLOTS)
}
