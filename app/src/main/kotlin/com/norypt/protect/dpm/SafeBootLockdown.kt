package com.norypt.protect.dpm

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import com.norypt.protect.admin.ProtectAdminReceiver

object SafeBootLockdown {

    private fun dpm(ctx: Context): DevicePolicyManager =
        ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun admin(ctx: Context): ComponentName =
        ComponentName(ctx, ProtectAdminReceiver::class.java)

    fun isOn(ctx: Context): Boolean = runCatching {
        dpm(ctx).getUserRestrictions(admin(ctx))
            ?.getBoolean(UserManager.DISALLOW_SAFE_BOOT, false) == true
    }.getOrDefault(false)

    /**
     * Applies DISALLOW_SAFE_BOOT via DPM (requires Device Owner).
     * Returns true if the restriction was applied successfully.
     */
    fun enable(ctx: Context): Boolean {
        if (!dpm(ctx).isDeviceOwnerApp(ctx.packageName)) return false
        return runCatching {
            dpm(ctx).addUserRestriction(admin(ctx), UserManager.DISALLOW_SAFE_BOOT)
            true
        }.getOrDefault(false)
    }

    fun disable(ctx: Context): Boolean {
        if (!dpm(ctx).isDeviceOwnerApp(ctx.packageName)) return false
        return runCatching {
            dpm(ctx).clearUserRestriction(admin(ctx), UserManager.DISALLOW_SAFE_BOOT)
            true
        }.getOrDefault(false)
    }
}
