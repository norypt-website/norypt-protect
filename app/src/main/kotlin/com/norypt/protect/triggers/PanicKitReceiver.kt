package com.norypt.protect.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs

class PanicKitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ProtectPrefs.isTriggerEnabled(context, "A5")) return
        PanicHandler.panic(context, "panickit")
    }
}

object PanicKitTrigger : Trigger {
    override val id = "A5"
    override val label = "PanicKit"
    override val description = "Respond to PanicKit triggers from other apps (Ripple, etc.)."
    override val requiredTier = Tier.DeviceAdmin
    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A5", true)
    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A5", false)
}
