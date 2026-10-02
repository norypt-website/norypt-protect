package com.norypt.protect.prefs

/**
 * Safe ranges for every numeric trigger setting, applied when a value is saved and again
 * when it is read, so a value stored by an older version cannot slip through either.
 *
 * Each lower bound stops a setting from turning into an unintended wipe: one failed unlock,
 * one minute unlocked, a countdown too short to cancel, a battery threshold that is always met.
 */
object SettingBounds {

    val FAILED_ATTEMPTS = 3..50
    val DURESS_ATTEMPTS = 2..50
    val UNLOCKED_MINUTES = 15..1440
    val GRACE_SECONDS = 15..600
    val BATTERY_PCT = 1..50
    val DISARM_MINUTES = 0..1440
    val DEADLINE_HOURS = 1..720

    fun failedAttempts(value: Int): Int = value.coerceIn(FAILED_ATTEMPTS)

    /** 0 keeps A11 off; any other value is at least two attempts. */
    fun duressThreshold(value: Int): Int = if (value <= 0) 0 else value.coerceIn(DURESS_ATTEMPTS)

    fun unlockedMinutes(value: Int): Int = value.coerceIn(UNLOCKED_MINUTES)
    fun graceSeconds(value: Int): Int = value.coerceIn(GRACE_SECONDS)
    fun batteryPct(value: Int): Int = value.coerceIn(BATTERY_PCT)
    fun disarmMinutes(value: Int): Int = value.coerceIn(DISARM_MINUTES)
    fun deadlineHours(value: Int): Int = value.coerceIn(DEADLINE_HOURS)

    /** Parses what was typed and bounds it; text that is not a number keeps [current]. */
    fun parse(text: String, current: Int, bound: (Int) -> Int): Int = text.toIntOrNull()?.let(bound) ?: current
}
