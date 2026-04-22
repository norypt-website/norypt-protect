package com.norypt.protect.dpm

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import com.norypt.protect.admin.ProtectAdminReceiver

object UsbLockdown {

    private fun dpm(ctx: Context): DevicePolicyManager =
        ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun admin(ctx: Context): ComponentName =
        ComponentName(ctx, ProtectAdminReceiver::class.java)

    fun isOn(ctx: Context): Boolean {
        val restrictions = dpm(ctx).getUserRestrictions(admin(ctx))
        return restrictions?.getBoolean(UserManager.DISALLOW_USB_FILE_TRANSFER, false) == true
    }

    /**
     * Applies DISALLOW_USB_FILE_TRANSFER via DPM (requires Device Owner).
     * Also attempts USB HAL reflection as a best-effort supplement.
     * Returns true if the DPM restriction was applied successfully.
     */
    fun enable(ctx: Context): Boolean {
        if (!dpm(ctx).isDeviceOwnerApp(ctx.packageName)) return false
        return runCatching {
            dpm(ctx).addUserRestriction(admin(ctx), UserManager.DISALLOW_USB_FILE_TRANSFER)
            tryDisableUsbDataViaHal(ctx)
            true
        }.getOrDefault(false)
    }

    fun disable(ctx: Context): Boolean {
        if (!dpm(ctx).isDeviceOwnerApp(ctx.packageName)) return false
        return runCatching {
            dpm(ctx).clearUserRestriction(admin(ctx), UserManager.DISALLOW_USB_FILE_TRANSFER)
            tryEnableUsbDataViaHal(ctx)
            true
        }.getOrDefault(false)
    }

    private fun tryDisableUsbDataViaHal(ctx: Context): Boolean = runCatching {
        val um = ctx.getSystemService(Context.USB_SERVICE)
        val method = um.javaClass.getDeclaredMethod("setDataEnabled", Boolean::class.javaPrimitiveType)
        method.invoke(um, false)
        true
    }.getOrDefault(false)

    private fun tryEnableUsbDataViaHal(ctx: Context): Boolean = runCatching {
        val um = ctx.getSystemService(Context.USB_SERVICE)
        val method = um.javaClass.getDeclaredMethod("setDataEnabled", Boolean::class.javaPrimitiveType)
        method.invoke(um, true)
        true
    }.getOrDefault(false)
}
