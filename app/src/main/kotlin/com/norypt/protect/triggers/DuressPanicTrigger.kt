package com.norypt.protect.triggers

import android.content.Context
import com.norypt.protect.admin.Tier
import com.norypt.protect.prefs.ProtectPrefs

/**
 * A11 — Duress fast-wipe panic threshold.
 *
 * Works as a secondary (stricter) trip-wire on top of B1: if the duress
 * threshold is > 0 and failed attempts reach it, panic fires immediately
 * regardless of B1's higher threshold.
 *
 * Design note: Android does not expose which PIN the user typed on the
 * lockscreen, so a "secondary unlock PIN" approach is impossible from a
 * user-space app. This implementation uses a configurable fail-count sentinel
 * that trips before B1's threshold.
 */
object DuressPanicTrigger : Trigger {
    override val id = "A11"
    override val label = "Duress fast-wipe threshold"
    override val description =
        "Wipe immediately after a lower number of failed unlocks — acts as a hidden " +
        "duress sentinel before the main max-attempts limit. Set to 0 to disable."
    override val requiredTier = Tier.DeviceAdmin

    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, id, true)
    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, id, false)
}
