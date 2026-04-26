package com.norypt.protect.dpm

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import com.norypt.protect.admin.ProtectAdminReceiver

/**
 * Anti-tamper hardening: blocks factory reset, prevents uninstall.
 * Both operations require Device Owner privilege.
 *
 * WARNING: Once applied, the only way to remove these restrictions is via
 * ADB or another Device Owner app. The UI must require App PIN to apply
 * and to release, and show a prominent warning before first enable.
 */
object AntiTamper {

    private fun dpm(ctx: Context): DevicePolicyManager =
        ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun admin(ctx: Context): ComponentName =
        ComponentName(ctx, ProtectAdminReceiver::class.java)

    fun isApplied(ctx: Context): Boolean {
        val d = dpm(ctx)
        val admin = admin(ctx)
        val factoryResetBlocked = runCatching {
            d.getUserRestrictions(admin)?.getBoolean(UserManager.DISALLOW_FACTORY_RESET, false) == true
        }.getOrDefault(false)
        val uninstallBlocked = runCatching { d.isUninstallBlocked(admin, ctx.packageName) }.getOrDefault(false)
        return factoryResetBlocked || uninstallBlocked
    }

    /**
     * Applies DISALLOW_FACTORY_RESET and setUninstallBlocked.
     * Returns true if Device Owner and all restrictions applied successfully.
     */
    fun apply(ctx: Context): Boolean {
        if (!dpm(ctx).isDeviceOwnerApp(ctx.packageName)) return false
        return runCatching {
            val d = dpm(ctx)
            val admin = admin(ctx)
            d.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
            d.setUninstallBlocked(admin, ctx.packageName, true)
            true
        }.getOrDefault(false)
    }

    /**
     * Removes factory-reset block and re-enables uninstall.
     * Returns true on success.
     */
    fun release(ctx: Context): Boolean {
        if (!dpm(ctx).isDeviceOwnerApp(ctx.packageName)) return false
        return runCatching {
            val d = dpm(ctx)
            val admin = admin(ctx)
            d.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
            d.setUninstallBlocked(admin, ctx.packageName, false)
            true
        }.getOrDefault(false)
    }
}
