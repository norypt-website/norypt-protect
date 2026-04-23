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
        val sp = ctx.getSharedPreferences("norypt_admin_debug", Context.MODE_PRIVATE)
        sp.edit().putInt("b5_tick_entered", sp.getInt("b5_tick_entered", 0) + 1).apply()

        if (!ProtectPrefs.isTriggerEnabled(ctx, "B5")) {
            sp.edit().putInt("b5_tick_disabled", sp.getInt("b5_tick_disabled", 0) + 1).apply()
            return
        }

        val current = currentInternetPackages(ctx)
        val known = ProtectPrefs.knownInternetPackages(ctx)

        val newEntries = current - known

        sp.edit()
            .putInt("b5_current_size", current.size)
            .putInt("b5_known_size", known.size)
            .putInt("b5_new_size", newEntries.size)
            .apply()

        if (known.isEmpty()) {
            sp.edit().putInt("b5_seeded", sp.getInt("b5_seeded", 0) + 1).apply()
            ProtectPrefs.setKnownInternetPackages(ctx, current)
            return
        }

        if (newEntries.isNotEmpty()) {
            newEntries.forEach { pkg ->
                postAlert(ctx, pkg)
                sp.edit().putInt("b5_alerts_posted", sp.getInt("b5_alerts_posted", 0) + 1).apply()
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
        "Alert when a newly installed app gains internet access permission. " +
        "Detection runs on the background tick (~10 s cadence) — Android 14+ blocks " +
        "instant PACKAGE_ADDED delivery to third-party receivers on both stock and GrapheneOS."
    override val requiredTier = Tier.DeviceAdmin

    override fun arm(context: Context) =
        ProtectPrefs.setTriggerEnabled(context, id, true)

    override fun disarm(context: Context) =
        ProtectPrefs.setTriggerEnabled(context, id, false)
}
