package com.norypt.protect.dpm

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.prefs.ProtectPrefs

/**
 * Norypt's own runtime permissions, held fixed only while a feature needs them.
 *
 * Promotion used to grant SMS and Bluetooth for good, fixed by policy, so the owner could not
 * take them back in Settings even with no feature using them.
 *
 * Nothing here revokes. Android kills an app's process the moment one of its own runtime
 * permissions is revoked: 1.2.0 did that at startup, and the process died before the monitoring
 * service could start. Releasing (DEFAULT) keeps the current grant and lets the owner decide.
 */
object OwnPermissions {

    private fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)
    private fun admin(ctx: Context) = ComponentName(ctx, ProtectAdminReceiver::class.java)

    /** Grants [permission] to this app; Device Owner only, otherwise a no-op. */
    fun grant(ctx: Context, permission: String) = setState(ctx, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)

    /** Stops holding [permission] fixed; whether it stays granted is then the owner's call. */
    fun release(ctx: Context, permission: String) =
        setState(ctx, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT)

    /** Releases grants no armed feature uses. Runs at every start; acts only on a fixed state. */
    fun releaseUnused(ctx: Context) {
        val dpm = dpm(ctx) ?: return
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return
        val unused = buildList {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            if (!ProtectPrefs.isTriggerEnabled(ctx, "A6")) add(Manifest.permission.RECEIVE_SMS)
        }
        unused.forEach { permission ->
            val fixed = runCatching {
                dpm.getPermissionGrantState(admin(ctx), ctx.packageName, permission) !=
                    DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
            }.getOrDefault(false)
            if (fixed) release(ctx, permission)
        }
    }

    private fun setState(ctx: Context, permission: String, state: Int) {
        val dpm = dpm(ctx) ?: return
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return
        runCatching { dpm.setPermissionGrantState(admin(ctx), ctx.packageName, permission, state) }
    }
}
