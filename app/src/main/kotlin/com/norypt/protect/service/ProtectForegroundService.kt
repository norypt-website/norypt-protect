package com.norypt.protect.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.norypt.protect.MainActivity
import com.norypt.protect.NoryptProtectApp
import com.norypt.protect.R
import com.norypt.protect.checkup.LocationWhileLocked
import com.norypt.protect.dpm.PowerMenuGuard
import com.norypt.protect.motion.MotionLockMonitor
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.timeline.TamperBootAudit
import com.norypt.protect.timeline.TamperKind
import com.norypt.protect.timeline.TamperLog
import com.norypt.protect.timeline.TamperMonitor
import com.norypt.protect.triggers.DeadmanScheduler
import com.norypt.protect.triggers.PowerGestureMonitor
import com.norypt.protect.triggers.UnlockedTimer
import com.norypt.protect.triggers.UsbLockedMonitor
import com.norypt.protect.triggers.UserPresentMonitor
import com.norypt.protect.util.DebugTelemetry

class ProtectForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    /** Set while waiting for the first unlock after a restart; see [startAfterUnlock]. */
    private var unlockReceiver: BroadcastReceiver? = null
    private var afterUnlockStarted = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            DebugTelemetry.bump(this@ProtectForegroundService, "fgs_ticks_total")
            DebugTelemetry.put(this@ProtectForegroundService, "fgs_listeners_size", tickListeners.size)
            // A settings move from 1.3.1 that failed is retried here (a no-op once done).
            runCatching { ProtectPrefs.retryMigration(this@ProtectForegroundService) }
            // A missed USER_UNLOCKED must not keep this service on the before-unlock list for good.
            if (!afterUnlockStarted && ProtectPrefs.userUnlocked(this@ProtectForegroundService)) {
                startAfterUnlock()
            }
            // Before the first unlock the app's ticks are not registered yet (NoryptProtectApp
            // registers them at the unlock); these run on device-protected settings alone.
            val ticks = if (afterUnlockStarted) tickListeners else BEFORE_UNLOCK_TICKS
            ticks.forEach { fn ->
                runCatching { fn(this@ProtectForegroundService) }
                    .onFailure { t ->
                        DebugTelemetry.bump(this@ProtectForegroundService, "fgs_tick_errors")
                        DebugTelemetry.put(
                            this@ProtectForegroundService,
                            "fgs_tick_last_error",
                            "${t.javaClass.simpleName}: ${t.message}",
                        )
                    }
            }
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Secret on the lock screen: whoever holds a locked phone must not learn that it is
        // armed. Tapping it opens the app (PIN-gated), also the way in when the icon is hidden.
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Norypt Protect is armed")
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(NotificationIds.SERVICE, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        // Each on its own: one monitor throwing must not leave every later one, and the
        // dead-man alarm, unstarted. These read only device-protected settings, so they also run
        // before the first unlock after a restart (this service is direct-boot aware).
        listOf<() -> Unit>(
            { PowerGestureMonitor.start(this) },
            { UsbLockedMonitor.start(this) },
            { DeadmanScheduler.schedule(this) },
        ).forEach { start -> runCatching { start() } }
        val unlocked = ProtectPrefs.userUnlocked(this)
        runCatching { TamperBootAudit.markServiceStart(this, locked = !unlocked) }
        DebugTelemetry.log("service start unlocked=$unlocked")
        if (unlocked) startAfterUnlock() else waitForUnlock()
        handler.postDelayed(tickRunnable, TICK_INTERVAL_MS)
    }

    /** ACTION_USER_UNLOCKED reaches only runtime receivers; the rest starts at the first unlock. */
    private fun waitForUnlock() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = startAfterUnlock()
        }
        unlockReceiver = receiver
        registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
        // The unlock may have happened between the check and the registration.
        if (ProtectPrefs.userUnlocked(this)) startAfterUnlock()
    }

    /** The monitors that need encrypted settings. Once per service instance. */
    private fun startAfterUnlock() {
        if (afterUnlockStarted) return
        afterUnlockStarted = true
        unlockReceiver?.let { runCatching { unregisterReceiver(it) } }
        unlockReceiver = null
        // The ticks the app registers at the unlock, in case this ran first.
        runCatching { (applicationContext as? NoryptProtectApp)?.initAfterUnlock() }
        // The unlock receiver only lives while this service does. If the service was down,
        // the stored unlock may be hours old while the owner has been using the phone; A8
        // would read that as unlocked for hours. Unlocked right now is a fresh baseline.
        runCatching {
            if (getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false) UnlockedTimer.stamp(this)
        }
        listOf<() -> Unit>(
            { PowerMenuGuard.start(this) },
            { UserPresentMonitor.start(this) },
            { TamperMonitor.start(this) },
            { MotionLockMonitor.start(this) },
            { LocationWhileLocked.start(this) },
            // Covers a boot whose broadcast never reached a force-stopped app; a no-op otherwise.
            { TamperBootAudit.check(this, fromBootBroadcast = false) },
            { TamperLog.record(this, TamperKind.MONITOR_STARTED) },
        ).forEach { start -> runCatching { start() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        unlockReceiver?.let { runCatching { unregisterReceiver(it) } }
        unlockReceiver = null
        PowerGestureMonitor.stop(this)
        UsbLockedMonitor.stop(this)
        PowerMenuGuard.stop(this)
        UserPresentMonitor.stop(this)
        TamperMonitor.stop(this)
        MotionLockMonitor.stop(this)
        LocationWhileLocked.stop(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "service"
        const val TICK_INTERVAL_MS = 30_000L

        private val tickListeners = mutableListOf<(Context) -> Unit>()

        /**
         * What the tick runs before the first unlock after a restart: the queued-wipe retry, which
         * touches only device-protected settings. After the unlock the app's own list
         * ([registerTick]) covers it.
         */
        private val BEFORE_UNLOCK_TICKS: List<(Context) -> Unit> = listOf(
            { PanicHandler.retryPendingWipe(it) },
        )

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
