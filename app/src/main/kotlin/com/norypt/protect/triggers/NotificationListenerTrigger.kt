package com.norypt.protect.triggers

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.norypt.protect.admin.Tier
import com.norypt.protect.prefs.ProtectPrefs

object NotificationListenerTrigger : Trigger {
    override val id = "B6"
    override val label = "Notification listener"
    override val description =
        "Allow Norypt Protect to observe notifications. Grant access in Settings to enable."
    override val requiredTier = Tier.DeviceAdmin

    override fun arm(context: Context) {
        ProtectPrefs.setTriggerEnabled(context, id, true)
        // Deep-link to Notification Listener Settings so the user can grant access
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun disarm(context: Context) =
        ProtectPrefs.setTriggerEnabled(context, id, false)
}
