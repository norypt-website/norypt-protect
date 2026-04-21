package com.norypt.protect

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.norypt.protect.service.ProtectForegroundService
import com.norypt.protect.triggers.FakeMessengerMonitor
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
        ProtectForegroundService.registerTick { UnlockedTimerMonitor.tick(it) }
        ProtectForegroundService.registerTick { FakeMessengerMonitor.tick(it) }
    }
}
