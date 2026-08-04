package com.norypt.protect.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.util.DebugTelemetry

class ExternalTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DebugTelemetry.bump(context, "a7_receiver_invocations")
        if (!ProtectPrefs.isTriggerEnabled(context, "A7")) {
            DebugTelemetry.bump(context, "a7_skip_disabled")
            return
        }
        PanicHandler.panic(context, "ext.broadcast")
    }
}

object ExternalBroadcastTrigger : Trigger {
    override val id = "A7"
    override val label = "External Broadcast"
    override val description = "Trigger panic via a signed broadcast from a trusted companion app. " +
        "Requires Device Owner — the wipe call is denied for non-DO admins on Android 13+."
    override val requiredTier = Tier.DeviceOwner
    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A7", true)
    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A7", false)
}
