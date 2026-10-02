package com.norypt.protect.dpm

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import com.norypt.protect.admin.ProtectAdminReceiver

/**
 * Hands back to the owner every runtime permission device policy holds fixed on other apps.
 *
 * The auto-grant policy that versions up to 1.1.1 set did more than grant: each grant was fixed
 * by policy, so Settings shows it as controlled by the admin and the owner cannot revoke it.
 * Turning the policy back to "ask" ([PermissionPolicyGuard]) stops new silent grants but leaves
 * those fixed. Setting each to DEFAULT keeps the app's current access and lets the owner take
 * it away. Norypt never fixes another app's permission on purpose, so every such grant came from
 * that policy.
 */
object PolicyGrantRelease {

    /** Whether a grant in [grantState] on [packageName] is one to hand back. */
    internal fun shouldRelease(selfPackage: String, packageName: String, grantState: Int): Boolean =
        packageName != selfPackage && grantState != DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT

    /**
     * Releases every fixed grant on other apps and returns how many, or null when this app is
     * not Device Owner. Hundreds of binder calls: run it off the main thread.
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
                val state = runCatching { dpm.getPermissionGrantState(admin, info.packageName, permission) }
                    .getOrDefault(DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT)
                if (shouldRelease(ctx.packageName, info.packageName, state) &&
                    runCatching {
                        dpm.setPermissionGrantState(
                            admin,
                            info.packageName,
                            permission,
                            DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT,
                        )
                    }.getOrDefault(false)
                ) {
                    released++
                }
            }
        }
        return released
    }
}
