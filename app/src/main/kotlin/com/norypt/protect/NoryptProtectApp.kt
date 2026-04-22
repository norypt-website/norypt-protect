package com.norypt.protect

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.norypt.protect.service.ProtectForegroundService
import com.norypt.protect.triggers.DeadmanMonitor
import com.norypt.protect.triggers.FakeMessengerMonitor
import com.norypt.protect.triggers.PackageInternetWatcher
import com.norypt.protect.triggers.UnlockedTimerMonitor

class NoryptProtectApp : Application() {
    override fun onCreate() {
        super.onCreate()
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
        ProtectForegroundService.registerTick { UnlockedTimerMonitor.tick(it) }
        ProtectForegroundService.registerTick { FakeMessengerMonitor.tick(it) }
        ProtectForegroundService.registerTick { DeadmanMonitor.tick(it) }
        ProtectForegroundService.registerTick { PackageInternetWatcher.tick(it) }
    }
}
