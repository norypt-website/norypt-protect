package com.norypt.protect.dpm

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.timeline.TamperKind
import com.norypt.protect.timeline.TamperLog

/**
 * Lockdown mode: the device shows a blank screen and nothing else until the App PIN is entered.
 *
 * Implemented as a Device Owner kiosk. Norypt Protect's blank home activity becomes the
 * persistent preferred HOME and the only package allowed in lock-task mode, with lock-task
 * features limited to the keyguard. That removes the launcher, the status-bar shade, quick
 * settings, recents and the power menu, while the screen still locks and still needs the
 * device credential. User switching is blocked by restriction so the lock screen shows no
 * user avatar.
 *
 * Everything here is idempotent: [applyPolicies] runs again on every resume of the blank
 * home, so a temporary lift (a Settings excursion, a user switch) heals itself when the
 * owner returns.
 *
 * What is deliberately not done: hiding the Settings package with setApplicationHidden.
 * Lock task already makes it unreachable, and hiding a system package has OEM-specific
 * failure modes with no way back short of ADB.
 */
object LockdownMode {

    const val ACTION_STOP = "com.norypt.protect.action.LOCKDOWN_STOP"

    private const val HOME_ACTIVITY = "com.norypt.protect.service.LockdownHomeActivity"

    private val RESTRICTIONS = listOf(UserManager.DISALLOW_USER_SWITCH, UserManager.DISALLOW_ADD_USER)

    private fun dpm(ctx: Context): DevicePolicyManager =
        ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun admin(ctx: Context): ComponentName = ComponentName(ctx, ProtectAdminReceiver::class.java)

    fun homeComponent(ctx: Context): ComponentName = ComponentName(ctx.packageName, HOME_ACTIVITY)

    fun isDeviceOwner(ctx: Context): Boolean = runCatching { dpm(ctx).isDeviceOwnerApp(ctx.packageName) }.getOrDefault(false)

    /** The owner's intent, as stored. See [isApplied] for what the platform is enforcing. */
    fun isEnabled(ctx: Context): Boolean = ProtectPrefs.lockdownEnabled(ctx)

    /** Read back from the platform rather than from our own flag. */
    fun isApplied(ctx: Context): Boolean = runCatching {
        val d = dpm(ctx)
        val a = admin(ctx)
        d.getLockTaskPackages(a).contains(ctx.packageName) &&
            d.getUserRestrictions(a).getBoolean(UserManager.DISALLOW_USER_SWITCH, false)
    }.getOrDefault(false)

    /** Turns lockdown on and shows the blank home. False if not Device Owner or a policy call failed. */
    fun enable(ctx: Context): Boolean {
        if (!isDeviceOwner(ctx)) return false
        ProtectPrefs.setLockdownEnabled(ctx, true)
        if (!applyPolicies(ctx)) {
            ProtectPrefs.setLockdownEnabled(ctx, false)
            releasePolicies(ctx)
            return false
        }
        TamperLog.record(
            ctx,
            TamperKind.LOCKDOWN,
            "Lockdown enabled: the device shows a blank screen until the App PIN is entered.",
        )
        launchHome(ctx)
        return true
    }

    /** Turns lockdown off, releases every policy and tells the blank home to leave. */
    fun disable(ctx: Context): Boolean {
        if (!isDeviceOwner(ctx)) return false
        ProtectPrefs.setLockdownEnabled(ctx, false)
        val ok = releasePolicies(ctx)
        TamperLog.record(ctx, TamperKind.LOCKDOWN, "Lockdown disabled.")
        ctx.sendBroadcast(Intent(ACTION_STOP).setPackage(ctx.packageName))
        return ok
    }

    /** Applies (or re-applies) every lockdown policy. Safe to call repeatedly. */
    fun applyPolicies(ctx: Context): Boolean {
        if (!isDeviceOwner(ctx)) return false
        return runCatching {
            val d = dpm(ctx)
            val a = admin(ctx)
            ctx.packageManager.setComponentEnabledSetting(
                homeComponent(ctx),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            d.setLockTaskPackages(a, arrayOf(ctx.packageName))
            // Keyguard only: no status-bar info, no notifications, no home, no recents, and
            // no global actions, so the long-press power menu is gone too.
            d.setLockTaskFeatures(a, DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD)
            val home = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            d.addPersistentPreferredActivity(a, home, homeComponent(ctx))
            RESTRICTIONS.forEach { d.addUserRestriction(a, it) }
            true
        }.getOrDefault(false)
    }

    /** Undoes [applyPolicies]. Clearing the allow-list also ends any lock task the blank home holds. */
    fun releasePolicies(ctx: Context): Boolean {
        if (!isDeviceOwner(ctx)) return false
        return runCatching {
            val d = dpm(ctx)
            val a = admin(ctx)
            d.clearPackagePersistentPreferredActivities(a, ctx.packageName)
            RESTRICTIONS.forEach { d.clearUserRestriction(a, it) }
            d.setLockTaskPackages(a, arrayOf())
            d.setLockTaskFeatures(
                a,
                DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD,
            )
            ctx.packageManager.setComponentEnabledSetting(
                homeComponent(ctx),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Lets the owner into Settings. Lock task is released for the excursion; the next resume
     * of the blank home (the Home button, or Back out of Settings) re-applies everything.
     */
    fun startSettingsExcursion(activity: Activity): Boolean {
        runCatching { activity.stopLockTask() }
        TamperLog.record(ctx = activity, kind = TamperKind.LOCKDOWN, detail = "Settings opened from lockdown with the App PIN.")
        return runCatching {
            activity.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    /** Other users on the device (managed profiles excluded). Empty when not Device Owner. */
    fun secondaryUsers(ctx: Context): List<UserHandle> =
        runCatching { dpm(ctx).getSecondaryUsers(admin(ctx)) }.getOrDefault(emptyList())

    /** Switches to [user] (null = the owner). The restriction is lifted for the call; resume re-applies it. */
    fun switchUser(ctx: Context, user: UserHandle?): Boolean {
        if (!isDeviceOwner(ctx)) return false
        return runCatching {
            val d = dpm(ctx)
            val a = admin(ctx)
            d.clearUserRestriction(a, UserManager.DISALLOW_USER_SWITCH)
            TamperLog.record(ctx, TamperKind.LOCKDOWN, "User switch requested from lockdown with the App PIN.")
            d.switchUser(a, user)
        }.getOrDefault(false)
    }

    fun launchHome(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setComponent(homeComponent(ctx))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        }
    }

    /** Hands the screen back to whatever launcher the user had. */
    fun launchDefaultHome(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
