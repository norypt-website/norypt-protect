package com.norypt.protect.dpm

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.service.PowerMenuBlockerActivity

/**
 * Blocks the stock Power menu while the screen is locked by piggy-backing
 * on Device Owner Lock Task mode with LOCK_TASK_FEATURE_GLOBAL_ACTIONS
 * deliberately omitted. Mirrors the approach used by Norypt MDM's
 * PolicyManager — the only pattern Android exposes to third-party apps
 * that actually suppresses SystemUI's global-actions dialog.
 *
 * Flow when [enabled] is true:
 *  - SCREEN_OFF → set lock-task packages + features + launch the
 *    transparent [PowerMenuBlockerActivity] which calls startLockTask().
 *    The system's long-press-power menu is suppressed from that moment.
 *  - USER_PRESENT (successful unlock) → broadcast ACTION_STOP so the
 *    blocker activity can stopLockTask + finish, restoring the normal
 *    power menu for the unlocked session.
 *
 * A 30-second firmware hold (physical button press) still forces a
 * hardware reset — that is the only path out.
 */
object PowerMenuGuard {

    const val ACTION_STOP = "com.norypt.protect.action.POWER_MENU_GUARD_STOP"

    private var screenReceiver: BroadcastReceiver? = null

    /** Start listening for screen-off/user-present transitions. Called from FGS. */
    fun start(ctx: Context) {
        if (screenReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (!ProtectPrefs.powerMenuBlockWhenLocked(c)) return
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> applyLockState(c, locked = true)
                    Intent.ACTION_USER_PRESENT -> applyLockState(c, locked = false)
                }
            }
        }
        screenReceiver = r
        ctx.applicationContext.registerReceiver(
            r,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
    }

    fun stop(ctx: Context) {
        screenReceiver?.let { runCatching { ctx.applicationContext.unregisterReceiver(it) } }
        screenReceiver = null
    }

    /** Called by the Protect-tab toggle. */
    fun enable(ctx: Context) {
        ProtectPrefs.setPowerMenuBlockWhenLocked(ctx, true)
    }

    fun disable(ctx: Context) {
        ProtectPrefs.setPowerMenuBlockWhenLocked(ctx, false)
        // Release any in-progress lock task immediately.
        applyLockState(ctx, locked = false)
    }

    fun isEnabled(ctx: Context): Boolean = ProtectPrefs.powerMenuBlockWhenLocked(ctx)

    private fun applyLockState(ctx: Context, locked: Boolean) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(ctx, ProtectAdminReceiver::class.java)
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return

        runCatching {
            if (locked) {
                // Allow all installed packages so Lock Task mode doesn't kick
                // the user out of whatever app was foreground when the screen
                // turned off. The only difference from normal operation is
                // that the Power menu is suppressed.
                val all = ctx.packageManager.getInstalledApplications(0).map { it.packageName }
                val pkgs = (all + ctx.packageName).distinct().toTypedArray()
                dpm.setLockTaskPackages(admin, pkgs)

                // Features: keep keyguard + system info; deliberately NO
                // LOCK_TASK_FEATURE_GLOBAL_ACTIONS → no power menu.
                val features = DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD or
                    DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                    DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                    DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                    DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW
                dpm.setLockTaskFeatures(admin, features)

                val intent = Intent(ctx, PowerMenuBlockerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                ctx.startActivity(intent)
            } else {
                // Reset lock-task allow-list + tell the blocker activity to finish.
                dpm.setLockTaskPackages(admin, arrayOf())
                ctx.sendBroadcast(
                    Intent(ACTION_STOP).setPackage(ctx.packageName),
                )
            }
        }
    }
}
