package com.norypt.protect.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.norypt.protect.R
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.service.ProtectForegroundService

class ProtectAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        ProtectForegroundService.start(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // DO tier: admin being revoked is treated as a tamper event — wipe immediately.
        // Device Admin tier: allow graceful removal (v0.1.0 behaviour).
        if (Provisioning.current(context) == Tier.DeviceOwner) {
            PanicHandler.panic(context, reason = "admin.disabled")
        }
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        // B4 — failed-auth notification
        postFailedAuthNotification(context)

        // Increment shared counter (used by both A11 and B1)
        ProtectPrefs.incrementFailedAttempts(context)
        val count = ProtectPrefs.failedAttempts(context)

        // A11 — duress fast-wipe (stricter threshold, checked first)
        if (ProtectPrefs.isTriggerEnabled(context, "A11")) {
            val duress = ProtectPrefs.duressThreshold(context)
            if (duress > 0 && count >= duress) {
                PanicHandler.panic(context, reason = "duress.threshold")
                return
            }
        }

        // B1 — max failed attempts
        if (!ProtectPrefs.isTriggerEnabled(context, "B1")) return
        val max = ProtectPrefs.maxFailedAttempts(context)
        if (count >= max) {
            PanicHandler.panic(context, reason = "max.failed")
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        ProtectPrefs.resetFailedAttempts(context)
    }

    private fun postFailedAuthNotification(context: Context) {
        if (!ProtectPrefs.isTriggerEnabled(context, "B4")) return
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        val notif = android.app.Notification.Builder(context, "auth-failed")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Failed unlock attempt")
            .setContentText("Norypt Protect detected a failed unlock.")
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }
}
