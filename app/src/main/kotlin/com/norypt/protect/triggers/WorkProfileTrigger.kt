package com.norypt.protect.triggers

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.norypt.protect.admin.Provisioning
import com.norypt.protect.admin.Tier
import com.norypt.protect.prefs.ProtectPrefs

/**
 * A12 — Work-profile-only wipe path.
 *
 * Only valid when the app is running as a managed-profile owner.
 * Arming when not in a work profile immediately disarms and posts a
 * notification to inform the user.
 *
 * The actual wipe code path is a passive hook in v0.4.0; no UI yet.
 */
object WorkProfileTrigger : Trigger {
    override val id = "A12"
    override val label = "Work-profile wipe"
    override val description =
        "Wipe only the managed work profile on trigger. Requires profile owner."
    override val requiredTier = Tier.DeviceAdmin

    override fun arm(context: Context) {
        if (!Provisioning.isProfileOwner(context)) {
            // Immediately disarm and notify
            ProtectPrefs.setTriggerEnabled(context, id, false)
            postNotProfileOwnerNotification(context)
            return
        }
        ProtectPrefs.setTriggerEnabled(context, id, true)
    }

    override fun disarm(context: Context) =
        ProtectPrefs.setTriggerEnabled(context, id, false)

    private fun postNotProfileOwnerNotification(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val notification = Notification.Builder(ctx, "alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Work-profile wipe unavailable")
            .setContentText("Norypt Protect is not running in a work profile.")
            .setAutoCancel(true)
            .build()
        nm.notify(1200, notification)
    }
}
