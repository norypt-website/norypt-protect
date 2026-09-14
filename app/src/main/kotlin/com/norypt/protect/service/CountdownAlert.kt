package com.norypt.protect.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.norypt.protect.R

/**
 * Posts the high-importance notification whose fullScreenIntent brings up
 * [WipeCountdownActivity]. On a locked device this is the approved Android pattern for
 * taking over the screen; a raw startActivity() from a service is silently suppressed on
 * Android 10+.
 *
 * One notification id per [CountdownMode], so clearing one countdown's alert never takes
 * the other's down with it.
 */
object CountdownAlert {

    fun post(ctx: Context, mode: CountdownMode) {
        val activityIntent = Intent(ctx, WipeCountdownActivity::class.java)
            .putExtra(CountdownMode.EXTRA, mode.extraValue)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // Distinct request codes: with FLAG_UPDATE_CURRENT, the two modes would otherwise
        // share one PendingIntent and the last one posted would win for both.
        val fullScreenPI = PendingIntent.getActivity(
            ctx,
            mode.ordinal,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when (mode) {
            CountdownMode.DEADMAN -> "Auto-wipe countdown active — tap to respond"
            CountdownMode.UNLOCK_DEADLINE -> "Not unlocked for too long — wipe countdown active, tap to respond"
        }
        val notif = Notification.Builder(ctx, "deadman")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Norypt Protect")
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPI, true)
            .build()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(mode.notificationId, notif)
    }

    /**
     * Clears the alert. It is posted setOngoing(true), so nothing dismisses it on its own:
     * after a cancelled or aborted countdown a non-dismissible notification would otherwise
     * stay on the lockscreen telling the user a wipe was pending when it was not.
     */
    fun clear(ctx: Context, mode: CountdownMode) {
        runCatching { ctx.getSystemService(NotificationManager::class.java)?.cancel(mode.notificationId) }
    }
}
