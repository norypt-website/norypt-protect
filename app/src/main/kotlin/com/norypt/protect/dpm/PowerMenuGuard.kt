package com.norypt.protect.dpm

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.dpm.PowerMenuGuardPolicy.Action
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.service.PowerMenuBlockerActivity

/**
 * The decision half of [PowerMenuGuard], kept free of Android so it can be unit tested.
 *
 * The guard's job is "hold a Device Owner lock task exactly while the lock screen is up".
 * It is driven by three platform facts, not by which broadcast arrived last:
 *  - keyguardLocked: the lock screen is showing.
 *  - interactive: the screen is on.
 *  - lockTaskActive: a lock task is in effect right now.
 *
 * Screen-off is not lock: after a timeout the platform keeps the session unlocked for
 * "Lock after screen timeout" (5 s by default, up to 30 min). A wake inside that window
 * shows no keyguard and sends no USER_PRESENT, which is why the guard must never engage
 * before the keyguard is actually up, and must release as soon as it sees an unlocked,
 * interactive session still holding the task.
 */
object PowerMenuGuardPolicy {
    enum class Action { ENGAGE, RELEASE, WAIT_FOR_LOCK, NONE }

    const val FAST_POLL_MS = 500L
    const val SLOW_POLL_MS = 2_000L
    const val FAST_POLL_WINDOW_MS = 15_000L

    fun decide(keyguardLocked: Boolean, interactive: Boolean, lockTaskActive: Boolean): Action = when {
        keyguardLocked && !lockTaskActive -> Action.ENGAGE
        !keyguardLocked && lockTaskActive && interactive -> Action.RELEASE
        !keyguardLocked && !lockTaskActive && !interactive -> Action.WAIT_FOR_LOCK
        else -> Action.NONE
    }

    /** How long to wait before re-reading the keyguard while the screen is off but not yet locked. */
    fun pollIntervalMs(elapsedMs: Long): Long = if (elapsedMs < FAST_POLL_WINDOW_MS) FAST_POLL_MS else SLOW_POLL_MS
}

/**
 * Blocks the stock Power menu while the screen is locked by piggy-backing
 * on Device Owner Lock Task mode with LOCK_TASK_FEATURE_GLOBAL_ACTIONS
 * deliberately omitted. Mirrors the approach used by Norypt MDM's
 * PolicyManager — the only pattern Android exposes to third-party apps
 * that actually suppresses SystemUI's global-actions dialog.
 *
 * Lock task also disables Quick Settings for as long as it is held, so holding it into an
 * unlocked session is a visible bug (no shade controls, no power menu at all). The guard
 * therefore converges on the platform state instead of trusting a sequence of broadcasts:
 *  - SCREEN_OFF → engage if the keyguard is already up, otherwise poll until it is or the
 *    screen comes back on.
 *  - SCREEN_ON → engage if the keyguard is up (a lock that fired while the SoC slept),
 *    release if the session is unlocked (a wake inside the lock-after window).
 *  - USER_PRESENT → release.
 *  - The foreground service tick and [start] call [reconcile] too, so a missed broadcast
 *    heals within one tick.
 *
 * Releasing means clearing the lock-task allow-list, which makes the platform end the lock
 * task and finish [PowerMenuBlockerActivity] whether or not its process is still alive;
 * the ACTION_STOP broadcast is the fast path. The blocker lives in its own task
 * (taskAffinity="") so that clearing never touches Norypt Protect's own activities.
 *
 * A 30-second firmware hold (physical button press) still forces a
 * hardware reset — that is the only path out.
 */
object PowerMenuGuard {

    const val ACTION_STOP = "com.norypt.protect.action.POWER_MENU_GUARD_STOP"

    /** A release seen from a keyguard read is confirmed once; isKeyguardLocked can lie briefly on wake. */
    private const val CONFIRM_RELEASE_MS = 500L

    private var screenReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pollingSince: Long? = null
    private val pollRunnable = Runnable { appContext?.let { reconcile(it, confirmed = false) } }
    private val confirmRunnable = Runnable { appContext?.let { reconcile(it, confirmed = true) } }

    /** Start listening for screen and unlock transitions. Called from FGS. */
    fun start(ctx: Context) {
        appContext = ctx.applicationContext
        if (screenReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (!ProtectPrefs.powerMenuBlockWhenLocked(c)) return
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF, Intent.ACTION_SCREEN_ON -> reconcile(c, confirmed = false)
                    // The platform's own word that the keyguard was just dismissed.
                    Intent.ACTION_USER_PRESENT -> {
                        cancelPending()
                        release(c)
                    }
                }
            }
        }
        screenReceiver = r
        ctx.applicationContext.registerReceiver(
            r,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
        // A service restart while the phone sits in a stale state (crash, force-stop, an update
        // installed from the stuck session) fixes it here rather than at the next unlock.
        reconcile(ctx, confirmed = false)
    }

    fun stop(ctx: Context) {
        cancelPending()
        screenReceiver?.let { runCatching { ctx.applicationContext.unregisterReceiver(it) } }
        screenReceiver = null
    }

    /** Called by the Protect-tab toggle. */
    fun enable(ctx: Context) {
        ProtectPrefs.setPowerMenuBlockWhenLocked(ctx, true)
        appContext = ctx.applicationContext
        reconcile(ctx, confirmed = false)
    }

    fun disable(ctx: Context) {
        ProtectPrefs.setPowerMenuBlockWhenLocked(ctx, false)
        cancelPending()
        // Release any in-progress lock task immediately.
        release(ctx)
    }

    fun isEnabled(ctx: Context): Boolean = ProtectPrefs.powerMenuBlockWhenLocked(ctx)

    /** Brings the lock task in line with the keyguard. Safe to call from anywhere, any time. */
    fun reconcile(ctx: Context) = reconcile(ctx, confirmed = false)

    private fun reconcile(ctx: Context, confirmed: Boolean) {
        if (!ProtectPrefs.powerMenuBlockWhenLocked(ctx) || !canAct(ctx)) {
            stopPolling()
            return
        }
        val action = PowerMenuGuardPolicy.decide(
            keyguardLocked = keyguardLocked(ctx),
            interactive = interactive(ctx),
            lockTaskActive = lockTaskActive(ctx),
        )
        when (action) {
            Action.ENGAGE -> {
                stopPolling()
                engage(ctx)
            }
            Action.RELEASE -> {
                stopPolling()
                if (confirmed) release(ctx) else scheduleConfirm()
            }
            Action.WAIT_FOR_LOCK -> continuePolling()
            Action.NONE -> stopPolling()
        }
    }

    private fun canAct(ctx: Context): Boolean {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return false
        // Lockdown mode holds its own lock task with the power menu already suppressed;
        // rewriting the allow-list here would throw the blank home out of it.
        return !LockdownMode.isEnabled(ctx)
    }

    private fun keyguardLocked(ctx: Context): Boolean {
        val km = ctx.getSystemService(KeyguardManager::class.java) ?: return false
        // isKeyguardLocked covers an insecure or trust-agent keyguard; isDeviceLocked is the
        // stable "credential required" bit that survives the wake transitions where the
        // first one briefly reads false.
        return km.isKeyguardLocked || km.isDeviceLocked
    }

    private fun interactive(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isInteractive == true

    private fun lockTaskActive(ctx: Context): Boolean =
        ctx.getSystemService(ActivityManager::class.java)?.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED

    private fun continuePolling() {
        val since = pollingSince ?: SystemClock.elapsedRealtime().also { pollingSince = it }
        handler.removeCallbacks(pollRunnable)
        handler.postDelayed(pollRunnable, PowerMenuGuardPolicy.pollIntervalMs(SystemClock.elapsedRealtime() - since))
    }

    private fun stopPolling() {
        pollingSince = null
        handler.removeCallbacks(pollRunnable)
    }

    private fun scheduleConfirm() {
        handler.removeCallbacks(confirmRunnable)
        handler.postDelayed(confirmRunnable, CONFIRM_RELEASE_MS)
    }

    private fun cancelPending() {
        stopPolling()
        handler.removeCallbacks(confirmRunnable)
    }

    private fun engage(ctx: Context) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(ctx, ProtectAdminReceiver::class.java)
        runCatching {
            // Allow all installed packages so Lock Task mode doesn't kick
            // the user out of whatever app was foreground when the screen
            // turned off, and so a notification tapped on the lock screen
            // can still open its app. The only difference from normal
            // operation is that the Power menu is suppressed.
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
        }
    }

    private fun release(ctx: Context) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(ctx, ProtectAdminReceiver::class.java)
        if (!canAct(ctx)) return
        runCatching {
            // Fast path: the blocker stops its lock task and finishes.
            ctx.sendBroadcast(Intent(ACTION_STOP).setPackage(ctx.packageName))
            // Sure path: a locked task whose package leaves the allow-list is ended and
            // cleared by the platform, blocker process or no blocker process.
            dpm.setLockTaskPackages(admin, arrayOf())
        }
    }
}
