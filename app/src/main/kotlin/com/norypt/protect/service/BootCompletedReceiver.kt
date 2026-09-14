package com.norypt.protect.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.norypt.protect.BuildConfig
import com.norypt.protect.admin.Provisioning
import com.norypt.protect.admin.Tier
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.timeline.TamperBootAudit
import com.norypt.protect.timeline.TamperKind
import com.norypt.protect.timeline.TamperLog

/**
 * Re-arms [ProtectForegroundService] after events that stop it.
 *
 * The service owns every runtime-registered receiver — screen on/off (C3), USB state, the
 * power-menu guard, and the unlock watcher that feeds A8 and the dead-man disarm window.
 * Those registrations die with the process, so anything that ends the process without
 * restarting the service silently disarms those triggers.
 *
 * Covered here:
 * - **Reboot** (`BOOT_COMPLETED`).
 * - **App update** (`MY_PACKAGE_REPLACED`). An update stops the service and nothing else
 *   restarted it, so the monitors stayed down until the user next opened the app or
 *   rebooted — a silent false negative lasting until then.
 *
 * Covered elsewhere: a low-memory kill is handled by `START_STICKY`, and opening the app
 * restarts the service from `MainActivity.onCreate`.
 *
 * Not covered: an explicit force-stop leaves the app in a stopped state where the platform
 * delivers it no broadcasts at all, by design. Recovery requires the user to open the app
 * or reboot, and that limit applies to every runtime-registered trigger this app ships.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // On a file-based-encryption device this broadcast is delivered only after the
                // first unlock of the boot — an unlock whose USER_PRESENT no receiver of ours
                // was registered for. Stamping it here keeps A8, the C4 disarm window and C6
                // measuring from a real unlock. Before this, A8 could fire on its first tick
                // after a reboot against a stale pre-reboot timestamp.
                ProtectPrefs.setLastUnlockMs(context, System.currentTimeMillis())
                TamperBootAudit.check(context, fromBootBroadcast = true)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED ->
                TamperLog.record(context, TamperKind.APP_UPDATED, "Version ${BuildConfig.VERSION_NAME} installed.")
            else -> return
        }
        if (Provisioning.current(context) != Tier.None) {
            ProtectForegroundService.start(context)
        }
    }
}
