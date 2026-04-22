package com.norypt.protect.triggers

import android.content.Context
import com.norypt.protect.admin.Tier
import com.norypt.protect.prefs.ProtectPrefs

/**
 * A11 — Duress fast-wipe panic threshold.
 *
 * Stricter than B1: if duressThreshold > 0 and failed attempts reach it, panic
 * fires immediately, regardless of B1's higher threshold.
 *
 * Like B1 / B4, this trigger lives on the DeviceAdminReceiver.onPasswordFailed
 * callback. Android 13+ stopped delivering that callback to non-Device-Owner
 * Device Admins (verified on Pixel 9a / Android 16, commit dae1bbf), so the
 * trigger requires Device Owner.
 */
object DuressPanicTrigger : Trigger {
    override val id = "A11"
    override val label = "Duress fast-wipe threshold"
    override val description = "Stricter than B1: wipe at a lower failed-unlock count " +
        "(typical setting: 3) for coercion defense. Shares B1's counter but fires first " +
        "because the threshold is lower. Same Android 13+ callback restriction as B1 — " +
        "requires Device Owner."
    override val requiredTier = Tier.DeviceOwner

    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, id, true)
    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, id, false)
}
