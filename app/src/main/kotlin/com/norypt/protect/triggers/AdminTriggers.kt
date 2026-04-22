package com.norypt.protect.triggers

import android.content.Context
import com.norypt.protect.admin.Tier
import com.norypt.protect.prefs.ProtectPrefs

object MaxFailedTrigger : Trigger {
    override val id = "B1"
    override val label = "Max failed unlock attempts"
    override val description = "Wipe after N consecutive failed system-lockscreen attempts. " +
        "Android 13+ delivers the failed-attempt callback only to Device Owner — this trigger " +
        "is silently inert at Device Admin tier; upgrade via ADB to activate it."
    override val requiredTier = Tier.DeviceOwner
    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "B1", true)
    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "B1", false)
}

object FailedAuthNotifTrigger : Trigger {
    override val id = "B4"
    override val label = "Failed unlock notification"
    override val description = "Local notification on each failed unlock. Same Android 13+ " +
        "callback restriction as B1: only reaches the app when running as Device Owner."
    override val requiredTier = Tier.DeviceOwner
    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "B4", true)
    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "B4", false)
}
