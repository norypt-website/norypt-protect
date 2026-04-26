package com.norypt.protect.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.norypt.protect.R
import com.norypt.protect.dpm.PowerMenuGuard
import com.norypt.protect.triggers.PowerGestureMonitor
import com.norypt.protect.triggers.UsbLockedMonitor

class ProtectForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            val sp = getSharedPreferences("norypt_admin_debug", Context.MODE_PRIVATE)
            sp.edit()
                .putInt("fgs_ticks_total", sp.getInt("fgs_ticks_total", 0) + 1)
                .putInt("fgs_listeners_size", tickListeners.size)
                .apply()
            tickListeners.forEach { fn ->
                runCatching { fn(this@ProtectForegroundService) }
                    .onFailure { t ->
                        sp.edit()
                            .putInt("fgs_tick_errors", sp.getInt("fgs_tick_errors", 0) + 1)
                            .putString("fgs_tick_last_error", "${t.javaClass.simpleName}: ${t.message}")
                            .apply()
                    }
            }
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Norypt Protect is armed")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        PowerGestureMonitor.start(this)
        UsbLockedMonitor.start(this)
        PowerMenuGuard.start(this)
        handler.postDelayed(tickRunnable, TICK_INTERVAL_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        PowerGestureMonitor.stop(this)
        UsbLockedMonitor.stop(this)
        PowerMenuGuard.stop(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "service"
        const val NOTIFICATION_ID = 1001
        const val TICK_INTERVAL_MS = 30_000L

        private val tickListeners = mutableListOf<(Context) -> Unit>()

        fun registerTick(fn: (Context) -> Unit) {
            tickListeners.add(fn)
        }

        fun start(ctx: Context) {
            val intent = Intent(ctx, ProtectForegroundService::class.java)
            ctx.startForegroundService(intent)
        }

        fun stop(ctx: Context) {
            val intent = Intent(ctx, ProtectForegroundService::class.java)
            ctx.stopService(intent)
        }
    }
}
