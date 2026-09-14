package com.norypt.protect.service

import android.content.Intent

/**
 * Which trigger a full-screen wipe countdown belongs to. Carried as an intent extra into
 * [WipeCountdownActivity], which is otherwise identical for both.
 */
enum class CountdownMode(val extraValue: String, val notificationId: Int, val reason: String) {
    /** C4 — low battery with every monitored connection down. */
    DEADMAN("deadman", 5001, "deadman"),

    /** C6 — the device has not been unlocked for the configured number of hours. */
    UNLOCK_DEADLINE("unlock_deadline", 5003, "unlock.deadline"),
    ;

    companion object {
        const val EXTRA = "countdown_mode"

        /** Defaults to [DEADMAN] so a stale PendingIntent from a pre-C6 build still behaves as it did. */
        fun fromIntent(intent: Intent?): CountdownMode =
            entries.firstOrNull { it.extraValue == intent?.getStringExtra(EXTRA) } ?: DEADMAN
    }
}
