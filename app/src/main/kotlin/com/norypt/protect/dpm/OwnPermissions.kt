package com.norypt.protect.dpm

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.prefs.ProtectPrefs

/**
 * Norypt's own runtime permissions, held only while a feature needs them.
 *
 * Promotion used to grant SMS and Bluetooth for good. An admin grant is fixed, so the owner
 * could not take them back in Settings even with no feature using them.
 */
object OwnPermissions {

    private fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)
    private fun admin(ctx: Context) = ComponentName(ctx, ProtectAdminReceiver::class.java)

    /** Grants [permission] to this app; Device Owner only, otherwise a no-op. */
    fun grant(ctx: Context, permission: String) = setState(ctx, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)

    /** Takes [permission] back and leaves it to the owner (not fixed either way). */
    fun revoke(ctx: Context, permission: String) {
        setState(ctx, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED)
        setState(ctx, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT)
    }

    /**
     * Releases grants older versions fixed at promotion that no armed feature uses. Runs at
     * every start; it only acts on a grant that is still fixed by policy.
     */
    fun releaseUnused(ctx: Context) {
        val dpm = dpm(ctx) ?: return
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return
        val unused = buildList {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            if (!ProtectPrefs.isTriggerEnabled(ctx, "A6")) add(Manifest.permission.RECEIVE_SMS)
        }
        unused.forEach { permission ->
            val fixedGranted = runCatching {
                dpm.getPermissionGrantState(admin(ctx), ctx.packageName, permission) ==
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            }.getOrDefault(false)
            if (fixedGranted) revoke(ctx, permission)
        }
    }

    private fun setState(ctx: Context, permission: String, state: Int) {
        val dpm = dpm(ctx) ?: return
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return
        runCatching { dpm.setPermissionGrantState(admin(ctx), ctx.packageName, permission, state) }
    }
}
