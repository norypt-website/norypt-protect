package com.norypt.protect.dpm

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import com.norypt.protect.admin.ProtectAdminReceiver

/**
 * Hands back to the owner the runtime permissions the old auto-grant policy locked on other apps.
 *
 * The auto-grant policy that versions up to 1.1.1 set did more than grant: each grant was fixed
 * by policy, so Settings shows it as controlled by the admin and the owner cannot revoke it.
 * Resetting the policy ([PermissionPolicyGuard]) stops new silent grants but leaves those locked.
 *
 * Those grants are not in the policy engine's records (Android 14+ keeps only what an admin set
 * explicitly), so setting them to DEFAULT finds nothing to remove and changes nothing; that is
 * what the first 1.2.1 build did on the first phone it ran on. Recording the grant as an admin
 * grant and then removing the record is what clears the lock. The app keeps the access it has,
 * and the owner can take it away.
 *
 * The flag cannot be read without system permissions, so every granted runtime permission of
 * every other app is handed back, and only on a phone that was on auto-grant, where that is what
 * the old policy produced.
 */
object PolicyGrantRelease {

    /** Whether a runtime permission of [packageName] in this state is one to hand back. */
    internal fun shouldRelease(selfPackage: String, packageName: String, granted: Boolean): Boolean =
        packageName != selfPackage && granted

    /**
     * Whether the release is due: once, and only where auto-grant was in force. [earlierBuildRan]
     * is the marker the first 1.2.1 build left on the one phone it ran on, whose policy 1.2.0 had
     * already reset without recording it.
     */
    internal fun shouldRun(autoGrantWasOn: Boolean, earlierBuildRan: Boolean, alreadyDone: Boolean): Boolean =
        !alreadyDone && (autoGrantWasOn || earlierBuildRan)

    /**
     * Hands back every granted runtime permission of every other app and returns how many, or
     * null when this app is not Device Owner. Hundreds of binder calls: run it off the main thread.
     */
    fun releaseAll(ctx: Context): Int? {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java) ?: return null
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return null
        val admin = ComponentName(ctx, ProtectAdminReceiver::class.java)
        val pm = ctx.packageManager
        val runtime = HashMap<String, Boolean>()
        fun isRuntime(permission: String): Boolean = runtime.getOrPut(permission) {
            runCatching { pm.getPermissionInfo(permission, 0).protection == PermissionInfo.PROTECTION_DANGEROUS }
                .getOrDefault(false)
        }
        var released = 0
        val packages = pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        packages.forEach { info ->
            info.requestedPermissions.orEmpty().filter(::isRuntime).forEach { permission ->
                val granted = pm.checkPermission(permission, info.packageName) == PackageManager.PERMISSION_GRANTED
                if (shouldRelease(ctx.packageName, info.packageName, granted) && handBack(dpm, admin, info.packageName, permission)) {
                    released++
                }
            }
        }
        return released
    }

    /** Records the grant as an admin grant, then removes the record; removing it always runs. */
    private fun handBack(dpm: DevicePolicyManager, admin: ComponentName, pkg: String, permission: String): Boolean {
        runCatching { dpm.setPermissionGrantState(admin, pkg, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED) }
        return runCatching {
            dpm.setPermissionGrantState(admin, pkg, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT)
        }.getOrDefault(false)
    }
}
