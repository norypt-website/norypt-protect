package com.norypt.protect.triggers

import android.content.Context
import com.norypt.protect.admin.Tier
import com.norypt.protect.prefs.ProtectPrefs

object MaxFailedTrigger : Trigger {
    override val id = "B1"
    override val label = "Max failed unlock attempts"
    override val description = "Wipe device after a configurable number of consecutive failed unlocks."
    override val requiredTier = Tier.DeviceAdmin
    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "B1", true)
    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "B1", false)
}

object FailedAuthNotifTrigger : Trigger {
    override val id = "B4"
    override val label = "Failed unlock notification"
    override val description = "Send a notification whenever a failed unlock attempt is detected."
    override val requiredTier = Tier.DeviceAdmin
    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "B4", true)
    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "B4", false)
}
