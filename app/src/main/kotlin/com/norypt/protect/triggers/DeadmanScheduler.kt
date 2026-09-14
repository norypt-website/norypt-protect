package com.norypt.protect.triggers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.util.DebugTelemetry

/**
 * Drives [DeadmanMonitor] (C4) and [UnlockDeadlineMonitor] (C6) from [AlarmManager] instead
 * of the foreground service's `Handler.postDelayed` tick.
 *
 * The handler tick is `uptimeMillis`-based and does not run while the SoC is suspended, so
 * the one trigger whose entire premise is an untouched, sleeping phone was the one that
 * stalled. A phone left draining in a bag could cross the battery threshold and never be
 * checked until something else happened to wake the device — or run flat first. That is a
 * false negative on the app's headline protection.
 *
 * Alarms are re-armed by each firing, by [com.norypt.protect.service.ProtectForegroundService]
 * on start, and after reboot and app update via `BootCompletedReceiver`.
 */
object DeadmanScheduler {

    /**
     * Requested gap between checks. While the device is in Doze the platform enforces its own
     * minimum (roughly ten minutes per app) regardless of what is asked for, so this is a
     * ceiling on responsiveness while awake rather than a guarantee while asleep.
     */
    const val INTERVAL_MS = 60_000L

    private const val REQUEST_CODE = 4001

    /** Whether any trigger that rides this alarm chain is armed. */
    fun anyArmed(context: Context): Boolean =
        ProtectPrefs.isTriggerEnabled(context, DeadmanTrigger.id) ||
            ProtectPrefs.isTriggerEnabled(context, UnlockDeadlineTrigger.id)

    fun schedule(context: Context) {
        if (!anyArmed(context)) {
            cancel(context)
            return
        }
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
        val pi = pendingIntent(context)

        // Both variants pierce Doze. setExactAndAllowWhileIdle needs SCHEDULE_EXACT_ALARM,
        // which the user can revoke; degrading to the inexact form keeps the trigger alive
        // with looser timing rather than silently disarming it.
        val exact = canScheduleExact(context)
        runCatching {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
        }.onFailure {
            // SecurityException if the permission was revoked between the check and the call.
            runCatching {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
        }
        DebugTelemetry.put(context, "c4_alarm_exact", if (exact) 1 else 0)
        DebugTelemetry.bump(context, "c4_alarm_scheduled")
        DebugTelemetry.log("c4 alarm scheduled in ${INTERVAL_MS}ms exact=$exact")
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.cancel(pendingIntent(context)) }
        DebugTelemetry.log("c4 alarm cancelled")
    }

    /** Whether the platform will honour an exact alarm for this app right now. */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DeadmanAlarmReceiver::class.java)
            .setAction(ACTION_DEADMAN_TICK)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    const val ACTION_DEADMAN_TICK = "com.norypt.protect.action.DEADMAN_TICK"
}

/**
 * Runs one dead-man check for each trigger and immediately re-arms the next alarm.
 *
 * Rescheduling happens before the checks so a throw inside either tick cannot end the chain
 * and silently disarm C4 and C6 for the rest of the device's uptime.
 */
class DeadmanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DeadmanScheduler.ACTION_DEADMAN_TICK) return
        DebugTelemetry.bump(context, "c4_alarm_fired")
        DebugTelemetry.log("c4 alarm FIRED (elapsedRealtime=${SystemClock.elapsedRealtime()})")
        DeadmanScheduler.schedule(context)
        runCatching { DeadmanMonitor.tick(context) }
            .onFailure { DebugTelemetry.log("c4 tick threw: ${it.javaClass.simpleName}: ${it.message}") }
        runCatching { UnlockDeadlineMonitor.tick(context) }
            .onFailure { DebugTelemetry.log("c6 tick threw: ${it.javaClass.simpleName}: ${it.message}") }
    }
}
