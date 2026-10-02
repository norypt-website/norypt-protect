package com.norypt.protect.shield

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.norypt.protect.admin.ProtectAdminReceiver

/** Reads, for every installed app, the facts [AuditRules] judges. Slow: call it off the main thread. */
object AppAudit {

    /** Not a public constant, but readable by apps; when it cannot be read the screen links to Settings. */
    private const val ENABLED_LISTENERS = "enabled_notification_listeners"

    fun collect(ctx: Context): List<AppFacts> {
        val pm = ctx.packageManager
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        val admins = runCatching { dpm?.activeAdmins.orEmpty().map { it.packageName }.toSet() }.getOrDefault(emptySet())
        val vpn = runCatching { dpm?.getAlwaysOnVpnPackage(ComponentName(ctx, ProtectAdminReceiver::class.java)) }.getOrNull()
        val a11y = AccessShield.enabledAll(ctx, ShieldKind.ACCESSIBILITY)
        val keyboards = AccessShield.enabledAll(ctx, ShieldKind.KEYBOARD)
        val listeners = notificationListeners(ctx).orEmpty()
        val launcher = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.ResolveInfoFlags.of(0),
        ).map { it.activityInfo.packageName }.toSet()
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
        return pm.getInstalledPackages(flags).map { info ->
            val pkg = info.packageName
            AppFacts(
                packageName = pkg,
                label = info.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg,
                system = AccessShield.isSystem(pm, pkg),
                deviceAdmin = pkg in admins,
                accessibility = pkg in a11y,
                keyboard = pkg in keyboards,
                notificationAccess = pkg in listeners,
                alwaysOnVpn = pkg == vpn,
                installer = runCatching { pm.getInstallSourceInfo(pkg).installingPackageName }.getOrNull(),
                inLauncher = pkg in launcher,
                sensitive = info.requestedPermissions.orEmpty().filter { held(pm, pkg, it) }.toSet(),
            )
        }
    }

    /** Packages with notification access; null only when the platform will not say. */
    fun notificationListeners(ctx: Context): Set<String>? = runCatching {
        listenerPackages(Settings.Secure.getString(ctx.contentResolver, ENABLED_LISTENERS).orEmpty())
    }.getOrNull()

    /** The setting is a colon-separated list of flattened components, `package/class`. */
    internal fun listenerPackages(raw: String): Set<String> =
        raw.split(':').map { it.substringBefore('/') }.filter { it.isNotBlank() }.toSet()

    private fun held(pm: PackageManager, pkg: String, permission: String): Boolean =
        permission in AuditRules.SENSITIVE && pm.checkPermission(permission, pkg) == PackageManager.PERMISSION_GRANTED
}
