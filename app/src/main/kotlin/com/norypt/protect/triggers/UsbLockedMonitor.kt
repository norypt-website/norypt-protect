package com.norypt.protect.triggers

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs

class UsbLockedMonitor : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ProtectPrefs.isTriggerEnabled(context, "A9")) return
        val km = context.getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked) {
            PanicHandler.panic(context, "usb.locked")
        }
    }
}

object UsbLockedTrigger : Trigger {
    override val id = "A9"
    override val label = "USB while locked"
    override val description = "Wipe device if USB power is connected while the screen is locked."
    override val requiredTier = Tier.DeviceAdmin
    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A9", true)
    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A9", false)
}
