package com.norypt.protect.timeline

import java.text.DateFormat
import java.util.Date

/** Locale-aware formatting shared by the Timeline tab and the detail strings it stores. */
object TimelineFormat {

    fun time(epochMs: Long): String =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMs))

    fun dateTime(epochMs: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))

    fun date(epochMs: Long): String =
        DateFormat.getDateInstance(DateFormat.LONG).format(Date(epochMs))

    /** "3d 2h", "2h 15m", "45m" or "30s", for gaps and clock steps. */
    fun duration(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1000L
        val days = totalSeconds / SECONDS_PER_DAY
        val hours = (totalSeconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val SECONDS_PER_DAY = 86_400L
}
