package com.norypt.protect.admin

import android.Manifest
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import com.norypt.protect.R
import com.norypt.protect.dpm.EmergencySos
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.service.ProtectForegroundService
import com.norypt.protect.timeline.SecurityLogFeature
import com.norypt.protect.timeline.TamperKind
import com.norypt.protect.timeline.TamperLog
import com.norypt.protect.util.DebugTelemetry
import com.norypt.protect.service.NotificationIds

class ProtectAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        ProtectForegroundService.start(context)

        // Auto-grant POST_NOTIFICATIONS so failed-auth alerts and the persistent
        // armed-status notification cannot be silently suppressed by the user.
        // Only Device Owner can self-grant runtime permissions; Device Admin
        // tier still has to ask the user once via the in-app prompt.
        if (Provisioning.current(context) == Tier.DeviceOwner) {
            grantNotificationPermission(context)
            // Retried at every app start too: the permission it needs is granted after promotion.
            EmergencySos.disableAfterPromotion(context)
        }
    }

    private fun grantNotificationPermission(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, ProtectAdminReceiver::class.java)
        // Only what the app needs whatever is armed. A policy grant is fixed (Settings cannot
        // revoke it), so RECEIVE_SMS is granted only while the SMS trigger is armed.
        val runtimePermissions = listOf(Manifest.permission.POST_NOTIFICATIONS)
        // Granted one by one. Never setPermissionPolicy(AUTO_GRANT): that policy is device-wide
        // and would silently grant every other app's permission requests (see PermissionPolicyGuard).
        runCatching {
            runtimePermissions.forEach { perm ->
                dpm.setPermissionGrantState(
                    admin,
                    context.packageName,
                    perm,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
                )
            }
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        TamperLog.record(context, TamperKind.ADMIN_DISABLED, "Device admin was deactivated.")
        // DO tier: admin being revoked is treated as a tamper event — wipe immediately.
        // Device Admin tier: allow graceful removal (v0.1.0 behaviour).
        if (Provisioning.current(context) == Tier.DeviceOwner) {
            PanicHandler.panic(context, reason = "admin.disabled")
        }
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        debugIncrement(context, "on_password_failed_calls")
        // B4 — failed-auth notification
        postFailedAuthNotification(context)

        // Shared run length (used by both A11 and B1)
        val count = ProtectPrefs.recordFailedAttempt(context)
        // With Android's security log on, failed unlocks reach the Timeline from it (it also sees attempts
        // before the first unlock after a restart); A11 and B1 below keep counting from this callback.
        if (!SecurityLogFeature.isOn(context)) TamperLog.record(context, TamperKind.UNLOCK_FAILED, "$count in a row.")

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
        debugIncrement(context, "on_password_succeeded_calls")
        ProtectPrefs.resetFailedAttempts(context)
    }

    override fun onPasswordChanged(context: Context, intent: Intent, user: UserHandle) {
        super.onPasswordChanged(context, intent, user)
        TamperLog.record(context, TamperKind.CREDENTIAL_CHANGED, "The device PIN, pattern or password was changed.")
    }

    override fun onSecurityLogsAvailable(context: Context, intent: Intent) {
        super.onSecurityLogsAvailable(context, intent)
        // A batch can hold thousands of events: read it off the main thread.
        val pending = goAsync()
        Thread {
            try {
                SecurityLogFeature.importNew(context)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun debugIncrement(ctx: Context, key: String) = DebugTelemetry.bump(ctx, key)

    private fun postFailedAuthNotification(context: Context) {
        if (!ProtectPrefs.isTriggerEnabled(context, "B4")) return
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        val notif = android.app.Notification.Builder(context, "auth-failed")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Failed unlock attempt")
            .setContentText("Norypt Protect detected a failed unlock.")
            // Seen by the owner after unlocking, not by the person making the attempts.
            .setVisibility(android.app.Notification.VISIBILITY_SECRET)
            .setAutoCancel(true)
            .build()
        nm.notify(NotificationIds.FAILED_UNLOCK, notif)
    }
}
