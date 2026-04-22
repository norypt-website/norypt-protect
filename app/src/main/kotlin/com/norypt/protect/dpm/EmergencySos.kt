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

    /** Returns the best-effort current value of the emergency_gesture_enabled secure setting.
     *  Order:
     *    1. Direct Settings.Secure.getInt — works on stock AOSP / most OEMs.
     *    2. Local cache of the last value Norypt Protect successfully wrote — used on
     *       GrapheneOS where Secure reads of this key are scoped out even with
     *       WRITE_SECURE_SETTINGS granted.
     *    3. -1 if neither path knows. */
    fun currentValue(ctx: Context): Int {
        val direct = runCatching {
            Settings.Secure.getInt(ctx.contentResolver, KEY, -1)
        }.getOrDefault(-1)
        if (direct != -1) return direct
        return com.norypt.protect.prefs.ProtectPrefs.sosLastIntent(ctx)
    }

    private fun contextState(ctx: Context): SosState = object : SosState {
        private val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        private val admin = ComponentName(ctx, ProtectAdminReceiver::class.java)

        override fun currentValue(): Int =
            runCatching { Settings.Secure.getInt(ctx.contentResolver, KEY, -1) }.getOrDefault(-1)

        override fun hasWriteSecureSettings(): Boolean =
            ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED

        override fun isDeviceOwner(): Boolean =
            dpm.isDeviceOwnerApp(ctx.packageName)

        override fun applyViaSecureSettings(value: Int): Boolean = runCatching {
            Settings.Secure.putInt(ctx.contentResolver, KEY, value)
            // Cache the intent so the UI can display state even when the read
            // path is scoped out (GrapheneOS).
            com.norypt.protect.prefs.ProtectPrefs.setSosLastIntent(ctx, value)
            true
        }.getOrDefault(false)

        override fun applyViaDpm(value: Int): Boolean = runCatching {
            dpm.setSecureSetting(admin, KEY, value.toString())
            com.norypt.protect.prefs.ProtectPrefs.setSosLastIntent(ctx, value)
            true
        }.getOrDefault(false)

        override fun openSettingsFallback(): Boolean = openBestEmergencyGestureScreen(ctx)
    }
}

/**
 * Tries multiple known intents to land the user on the Emergency SOS settings
 * screen directly, in order of specificity. Falls back to general Settings if
 * nothing resolves.
 *
 * Verified paths (ordered most specific → least specific):
 *  1. AOSP / GrapheneOS / Pixel 9a — activity-alias exposed by Settings.apk
 *  2. AOSP / generic — package-level action present on Android 13+
 *  3. Android 12+ — Safety & Emergency dashboard
 *  4. Android 12+ — Safety Center (covers OEMs that moved SOS inside it)
 *  5. Last resort — top-level Settings
 */
private fun openBestEmergencyGestureScreen(ctx: Context): Boolean {
    val candidates = listOf(
        // Pixel / GrapheneOS / AOSP — activity alias for the Emergency Gestures page specifically.
        Intent().setComponent(
            ComponentName(
                "com.android.settings",
                "com.android.settings.Settings\$EmergencyGestureSettingsActivity",
            ),
        ),
        // Pixel / GrapheneOS / AOSP — the Safety & Emergency dashboard. Verified to
        // resolve on Pixel 9a / Android 16 / GrapheneOS as Settings$EmergencyDashboardActivity.
        Intent("android.settings.EMERGENCY_SETTINGS"),
        // Public action for the Emergency Gesture page — present on some OEMs.
        Intent("android.settings.EMERGENCY_GESTURE_SETTINGS"),
        // Safety Center (Samsung / Xiaomi / some OEMs route Emergency SOS through this).
        Intent("android.settings.SAFETY_CENTER"),
        // OEM-specific Samsung deep-link, harmless on non-Samsung because resolveActivity skips it.
        Intent().setComponent(
            ComponentName("com.samsung.safetyassurance", "com.samsung.android.app.safetyassurance.MainActivity"),
        ),
        // Absolute last resort.
        Intent(Settings.ACTION_SETTINGS),
    )

    val pm = ctx.packageManager
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(pm) != null) {
            val ok = runCatching { ctx.startActivity(intent) }.isSuccess
            if (ok) return true
        }
    }
    return false
}
