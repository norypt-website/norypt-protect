package com.norypt.protect.triggers

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.norypt.protect.admin.Tier
import com.norypt.protect.prefs.ProtectPrefs

/**
 * B5 — App Internet permission monitor.
 *
 * Polled on each FGS tick. Diffs the set of installed packages that hold
 * INTERNET permission against the stored known-set. New entries trigger a
 * notification on the "alerts" channel.
 */
object PackageInternetWatcher {

    private var notificationId = 5000

    fun tick(ctx: Context) {
        if (!ProtectPrefs.isTriggerEnabled(ctx, "B5")) return

        val current = currentInternetPackages(ctx)
        val known = ProtectPrefs.knownInternetPackages(ctx)

        val newEntries = current - known

        if (known.isEmpty()) {
            // First run — seed the known set without alerting
            ProtectPrefs.setKnownInternetPackages(ctx, current)
            return
        }

        if (newEntries.isNotEmpty()) {
            newEntries.forEach { pkg ->
                postAlert(ctx, pkg)
            }
            ProtectPrefs.setKnownInternetPackages(ctx, current)
        }
    }

    private fun currentInternetPackages(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val packages = pm.getInstalledPackages(
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
        )
        return packages
            .filter { hasGrantedInternet(it) }
            .map { it.packageName }
            .toSet()
    }

    private fun hasGrantedInternet(info: PackageInfo): Boolean {
        val perms = info.requestedPermissions ?: return false
        val flags = info.requestedPermissionsFlags ?: return false
        return perms.indices.any { i ->
            perms[i] == Manifest.permission.INTERNET &&
                (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
        }
    }

    private fun postAlert(ctx: Context, pkg: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val notification = Notification.Builder(ctx, "alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("New internet-access app detected")
            .setContentText("$pkg now has internet access")
            .setAutoCancel(true)
            .build()
        nm.notify(notificationId++, notification)
    }
}

object PackageInternetTrigger : Trigger {
    override val id = "B5"
    override val label = "Internet permission monitor"
    override val description =
        "Alert when a newly installed app gains internet access permission."
    override val requiredTier = Tier.DeviceAdmin

    override fun arm(context: Context) =
        ProtectPrefs.setTriggerEnabled(context, id, true)

    override fun disarm(context: Context) =
        ProtectPrefs.setTriggerEnabled(context, id, false)
}
