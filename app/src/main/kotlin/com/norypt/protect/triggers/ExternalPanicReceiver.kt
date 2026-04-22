package com.norypt.protect.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs

// Listens for the cross-app emergency-panic broadcast standard so external
// emergency apps can fire Norypt Protect.
class ExternalPanicReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ProtectPrefs.isTriggerEnabled(context, "A5")) return
        PanicHandler.panic(context, "external.panic")
    }
}

object ExternalPanicTrigger : Trigger {
    override val id = "A5"
    override val label = "External panic interop"
    override val description = "Respond to the cross-app emergency-panic broadcast so other emergency apps can trigger Norypt Protect."
    override val requiredTier = Tier.DeviceOwner
    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A5", true)
    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A5", false)
}
