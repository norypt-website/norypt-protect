package com.norypt.protect.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

enum class Tier { None, DeviceAdmin, DeviceOwner }

object Provisioning {

    /**
     * Returns true when this app is acting as a managed-profile owner.
     * Used by [WorkProfileTrigger] (A12) to gate the work-profile-only wipe path.
     */
    fun isProfileOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isProfileOwnerApp(context.packageName)
    }

    fun current(context: Context): Tier {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, ProtectAdminReceiver::class.java)
        val isAdminActive = dpm.isAdminActive(admin)
        val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
        return tierFrom(isAdminActive, isDeviceOwner)
    }

    /**
     * Pure function — injected values for testability.
     *
     * WRITE_SECURE_SETTINGS deliberately does not appear here. It was previously queried and
     * passed in but never read, which invited the reader to assume an ADB-granted permission
     * could raise the security tier. It cannot: the tier is admin-active plus device-owner.
     */
    internal fun tierFrom(isAdminActive: Boolean, isDeviceOwner: Boolean): Tier {
        return when {
            !isAdminActive -> Tier.None
            isDeviceOwner -> Tier.DeviceOwner
            else -> Tier.DeviceAdmin
        }
    }
}
