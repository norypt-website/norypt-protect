package com.norypt.protect.dpm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.norypt.protect.R
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.service.NotificationIds

/**
 * Keeps the device-wide runtime-permission policy at "ask the user".
 *
 * Up to 1.1.1, Device Owner promotion set PERMISSION_POLICY_AUTO_GRANT to spare the owner
 * Norypt's own permission prompts. That policy is not per-app: every runtime permission any
 * app requested was granted silently, camera, microphone and location included when the admin
 * may grant sensor permissions. Norypt's own permissions are granted one by one instead, so
 * the policy is put back to PROMPT wherever an older version left it on auto-grant.
 */
object PermissionPolicyGuard {

    /** True when the current policy has to go back to PROMPT. Split out so it is unit-testable. */
    internal fun needsReset(isDeviceOwner: Boolean, policy: Int): Boolean =
        isDeviceOwner && policy == DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT

    /**
     * Restores PROMPT if an older version left auto-grant on, and tells the owner once, because
     * permissions handed out while it was on stay granted. Cheap enough for every process start.
     */
    fun enforce(ctx: Context) {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java) ?: return
        val admin = ComponentName(ctx, ProtectAdminReceiver::class.java)
        val isDeviceOwner = dpm.isDeviceOwnerApp(ctx.packageName)
        val policy = runCatching { dpm.getPermissionPolicy(admin) }.getOrNull() ?: return
        if (!needsReset(isDeviceOwner, policy)) return
        val reset = runCatching {
            dpm.setPermissionPolicy(admin, DevicePolicyManager.PERMISSION_POLICY_PROMPT)
        }.isSuccess
        if (reset) notifyReviewPermissions(ctx)
    }

    private fun notifyReviewPermissions(ctx: Context) {
        runCatching {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            val open = PendingIntent.getActivity(
                ctx,
                0,
                Intent(Settings.ACTION_PRIVACY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val text = "An earlier version let apps receive permissions without asking you. " +
                "Open Permission manager and remove camera, microphone, location and other " +
                "permissions from apps that should not have them."
            val notif = Notification.Builder(ctx, "alerts")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Review app permissions")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(NotificationIds.PERMISSION_REVIEW, notif)
        }
    }
}
