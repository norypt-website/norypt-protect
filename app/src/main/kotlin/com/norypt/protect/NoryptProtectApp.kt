package com.norypt.protect

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.norypt.protect.dpm.EmergencySos
import com.norypt.protect.dpm.LockdownMode
import com.norypt.protect.dpm.OwnPermissions
import com.norypt.protect.dpm.PermissionPolicyGuard
import com.norypt.protect.dpm.PowerMenuGuard
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.service.ProtectForegroundService
import com.norypt.protect.shield.AccessShield
import com.norypt.protect.timeline.TamperLog
import com.norypt.protect.timeline.TamperMonitor
import com.norypt.protect.triggers.FakeMessengerMonitor
import com.norypt.protect.triggers.ForcedRestartMonitor
import com.norypt.protect.triggers.PackageInternetWatcher
import com.norypt.protect.triggers.UnlockedTimerMonitor
import com.norypt.protect.util.DebugTelemetry
import java.util.concurrent.atomic.AtomicBoolean

class NoryptProtectApp : Application() {

    private val initDone = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        // Before the first unlock after a restart, credential-encrypted storage is closed: only the
        // direct-boot-aware components run (the service with its before-unlock monitors, the admin
        // receiver, the alarm), on device-protected settings. This process lives on through the
        // unlock, and onCreate runs once per process, so the rest of the setup waits for the unlock.
        if (!ProtectPrefs.userUnlocked(this)) {
            runAtUnlock()
            return
        }
        initAfterUnlock()
    }

    /** Runs [initAfterUnlock] when ACTION_USER_UNLOCKED arrives (delivered only to runtime receivers). */
    private fun runAtUnlock() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                runCatching { unregisterReceiver(this) }
                initAfterUnlock()
            }
        }
        registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
        // The unlock may have happened between the check above and the registration.
        if (ProtectPrefs.userUnlocked(this)) {
            runCatching { unregisterReceiver(receiver) }
            initAfterUnlock()
        }
    }

    /**
     * The setup that needs encrypted settings. Runs once per process: at start when the user is
     * unlocked, otherwise at the first unlock (BootCompletedReceiver and the service call it too,
     * should ACTION_USER_UNLOCKED have been missed).
     */
    fun initAfterUnlock() {
        if (!ProtectPrefs.userUnlocked(this) || !initDone.compareAndSet(false, true)) return
        // The ticks first, and every step after them guarded: this runs once per process, so a
        // step that throws must not leave the triggers unregistered until the next restart.
        registerTicks()
        // Timeline entries recorded before the first unlock.
        runCatching { TamperLog.importLocked(this) }
        runCatching { DebugTelemetry.purgeInReleaseBuilds(this) }
        runCatching { createChannels() }
        // Undoes the device-wide permission auto-grant older versions set on promotion. Runs on
        // every process start, so the update itself (MY_PACKAGE_REPLACED) repairs existing phones.
        runCatching { PermissionPolicyGuard.enforce(this) }
        runCatching { PermissionPolicyGuard.releaseFixedGrantsOnce(this) }
        // A lockdown that was switched off but not fully released would leave a blank HOME.
        runCatching { LockdownMode.releaseLeftovers(this) }
        runCatching { OwnPermissions.releaseUnused(this) }
        runCatching { EmergencySos.disableAfterPromotion(this) }
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("service", "Norypt Protect service", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel("auth-failed", "Failed unlock attempts", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel("panic-dryrun", "Panic dry-run events", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel("alerts", "Norypt Protect alerts", NotificationManager.IMPORTANCE_HIGH)
        )
        // Maximum importance — required for setFullScreenIntent to actually take over the lockscreen.
        nm.createNotificationChannel(
            NotificationChannel(
                "deadman",
                "Dead-man countdown",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Full-screen alert when the low-battery dead-man switch trips."
                enableLights(true)
                enableVibration(true)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }

    private fun registerTicks() {
        ProtectForegroundService.registerTick { UnlockedTimerMonitor.tick(it) }
        ProtectForegroundService.registerTick { FakeMessengerMonitor.tick(it) }
        // C4 is driven by DeadmanScheduler's alarm, not this tick: the handler does not
        // run while the SoC is suspended, which is exactly when the dead-man must fire.
        ProtectForegroundService.registerTick { PackageInternetWatcher.tick(it) }
        // Retries a wipe that was attempted and denied — see PanicHandler.retryPendingWipe.
        ProtectForegroundService.registerTick { PanicHandler.retryPendingWipe(it) }
        // C9: settles a forced-restart countdown on an unlock, brings a closed screen back, wipes when overdue.
        ProtectForegroundService.registerTick { ForcedRestartMonitor.tick(it) }
        // Tamper-timeline sentinels: SIM, USB debugging, lock screen, biometrics, failed unlocks.
        ProtectForegroundService.registerTick { TamperMonitor.tick(it) }
        // Power-menu guard self-heal: a lock task held into an unlocked session, or a keyguard
        // that came up without the guard noticing, is corrected within one tick.
        ProtectForegroundService.registerTick { PowerMenuGuard.reconcile(it) }
        // Spyware shield self-heal: a permitted list that differs from the owner's is put back within one tick.
        ProtectForegroundService.registerTick { AccessShield.reconcile(it) }
    }
}
