package com.norypt.protect.dpm

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.timeline.TamperKind
import com.norypt.protect.timeline.TamperLog

/**
 * Blocks app installation on the owner profile (DISALLOW_INSTALL_APPS plus
 * DISALLOW_INSTALL_UNKNOWN_SOURCES). Covers app stores, sideloading, and `adb install`,
 * which fails with INSTALL_FAILED_USER_RESTRICTED. Persists across reboots for as long as
 * the app is Device Owner.
 *
 * It also blocks updates — including updates to Norypt Protect itself — because an update
 * is an install of the same package. The UI says so; the owner turns it off to update.
 */
object InstallLockdown {

    private val RESTRICTIONS = listOf(
        UserManager.DISALLOW_INSTALL_APPS,
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
    )

    private fun dpm(ctx: Context): DevicePolicyManager =
        ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun admin(ctx: Context): ComponentName = ComponentName(ctx, ProtectAdminReceiver::class.java)

    /** Read from the platform, not from a stored flag. */
    fun isOn(ctx: Context): Boolean = runCatching {
        dpm(ctx).getUserRestrictions(admin(ctx))?.getBoolean(UserManager.DISALLOW_INSTALL_APPS, false) == true
    }.getOrDefault(false)

    fun enable(ctx: Context): Boolean {
        if (!dpm(ctx).isDeviceOwnerApp(ctx.packageName)) return false
        val ok = runCatching {
            RESTRICTIONS.forEach { dpm(ctx).addUserRestriction(admin(ctx), it) }
            true
        }.getOrDefault(false)
        if (ok) TamperLog.record(ctx, TamperKind.INSTALL_BLOCK, "App installation blocked on the owner profile.")
        return ok
    }

    fun disable(ctx: Context): Boolean {
        if (!dpm(ctx).isDeviceOwnerApp(ctx.packageName)) return false
        val ok = runCatching {
            RESTRICTIONS.forEach { dpm(ctx).clearUserRestriction(admin(ctx), it) }
            true
        }.getOrDefault(false)
        if (ok) TamperLog.record(ctx, TamperKind.INSTALL_BLOCK, "App installation allowed again.")
        return ok
    }
}
