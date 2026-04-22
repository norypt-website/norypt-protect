package com.norypt.protect.dpm

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.norypt.protect.admin.ProtectAdminReceiver

private const val KEY = "emergency_gesture_enabled"

/**
 * Tri-path utility to disable the Emergency SOS gesture.
 *
 * Path 1 (best): Settings.Secure.putInt — works when WRITE_SECURE_SETTINGS is granted via ADB.
 * Path 2: DPM.setSecureSetting — works when Device Owner.
 * Path 3 (fallback): Open Settings deep-link so user can toggle manually.
 *
 * [DisableResult] describes which path was taken (or failed).
 */
enum class DisableResult { PATH_SECURE_SETTINGS, PATH_DPM, PATH_FALLBACK_UI, ALREADY_DISABLED, FAILED }

object EmergencySos {

    /**
     * Injectable state interface so path-selection logic is unit-testable
     * without a real Context/DPM.
     */
    internal interface SosState {
        fun currentValue(): Int          // 0 = disabled, 1 = enabled, -1 = unknown
        fun hasWriteSecureSettings(): Boolean
        fun isDeviceOwner(): Boolean
        fun applyViaSecureSettings(value: Int): Boolean
        fun applyViaDpm(value: Int): Boolean
        fun openSettingsFallback(): Boolean
    }

    internal fun selectPath(state: SosState, targetValue: Int): DisableResult {
        val current = state.currentValue()
        if (current == targetValue) return DisableResult.ALREADY_DISABLED

        if (state.hasWriteSecureSettings()) {
            val ok = state.applyViaSecureSettings(targetValue)
            if (ok) return DisableResult.PATH_SECURE_SETTINGS
        }
        if (state.isDeviceOwner()) {
            val ok = state.applyViaDpm(targetValue)
            if (ok) return DisableResult.PATH_DPM
        }
        val opened = state.openSettingsFallback()
        return if (opened) DisableResult.PATH_FALLBACK_UI else DisableResult.FAILED
    }

    /** Disable Emergency SOS gesture. Returns which path was used. */
    fun disableIfPossible(ctx: Context): DisableResult =
        selectPath(contextState(ctx), 0)

    /** Re-enable Emergency SOS gesture. Returns which path was used. */
    fun enableIfPossible(ctx: Context): DisableResult =
        selectPath(contextState(ctx), 1)

    /** Returns the current value of the emergency_gesture_enabled secure setting. */
    fun currentValue(ctx: Context): Int =
        runCatching { Settings.Secure.getInt(ctx.contentResolver, KEY) }.getOrDefault(-1)

    private fun contextState(ctx: Context): SosState = object : SosState {
        private val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        private val admin = ComponentName(ctx, ProtectAdminReceiver::class.java)

        override fun currentValue(): Int =
            runCatching { Settings.Secure.getInt(ctx.contentResolver, KEY) }.getOrDefault(-1)

        override fun hasWriteSecureSettings(): Boolean =
            ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED

        override fun isDeviceOwner(): Boolean =
            dpm.isDeviceOwnerApp(ctx.packageName)

        override fun applyViaSecureSettings(value: Int): Boolean = runCatching {
            Settings.Secure.putInt(ctx.contentResolver, KEY, value)
            true
        }.getOrDefault(false)

        override fun applyViaDpm(value: Int): Boolean = runCatching {
            dpm.setSecureSetting(admin, KEY, value.toString())
            true
        }.getOrDefault(false)

        override fun openSettingsFallback(): Boolean = runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }
}
