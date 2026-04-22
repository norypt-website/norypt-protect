package com.norypt.protect.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest

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
        val hasWriteSecureSettings = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        return tierFrom(isAdminActive, isDeviceOwner, hasWriteSecureSettings)
    }

    // Pure function — injected values for testability
    internal fun tierFrom(isAdminActive: Boolean, isDeviceOwner: Boolean, hasWriteSecureSettings: Boolean): Tier {
        return when {
            !isAdminActive -> Tier.None
            isDeviceOwner -> Tier.DeviceOwner
            else -> Tier.DeviceAdmin
        }
    }
}
