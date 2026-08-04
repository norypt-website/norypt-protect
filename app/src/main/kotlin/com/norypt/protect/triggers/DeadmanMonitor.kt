package com.norypt.protect.triggers

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.SystemClock
import com.norypt.protect.admin.Tier
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.service.WipeCountdownActivity
import com.norypt.protect.util.DebugTelemetry

/**
 * C4 — Low-battery dead-man switch.
 *
 * Polled on each FGS tick. When battery level drops to or below the configured
 * threshold AND all required connectivity (BT/GSM/Wi-Fi) is absent, launches
 * [WipeCountdownActivity] over the lockscreen. The countdown activity handles
 * the actual panic call after the grace period.
 */
object DeadmanMonitor {

    /**
     * How long the duplicate-launch guard holds before it expires on its own. Must exceed
     * any plausible configured grace period; a guard that outlives the countdown costs one
     * duplicate alert, while a guard that never expires disables C4 entirely.
     */
    private const val COUNTDOWN_GUARD_MS = 30 * 60_000L

    private var countdownStartedAtMs: Long = 0L

    /**
     * True while [WipeCountdownActivity] is expected to be up, to prevent duplicate
     * launches. Backed by a timestamp rather than a plain flag: the countdown is delivered
     * through setFullScreenIntent, which silently degrades to a heads-up notification when
     * USE_FULL_SCREEN_INTENT is not held — the default on Android 14+ for an app that is
     * not a calling or alarm app. If nobody taps that notification the activity never
     * starts, so onDestroy never clears the flag, and a plain boolean would leave C4
     * permanently armed-but-inert with no user-visible signal.
     */
    var countdownActive: Boolean
        get() = countdownStartedAtMs != 0L &&
            SystemClock.elapsedRealtime() - countdownStartedAtMs < COUNTDOWN_GUARD_MS
        set(value) {
            countdownStartedAtMs = if (value) SystemClock.elapsedRealtime() else 0L
        }

    fun tick(ctx: Context) {
        debugBump(ctx, "c4_ticks_total")
        if (!ProtectPrefs.isTriggerEnabled(ctx, "C4")) {
            debugBump(ctx, "c4_skip_disabled")
            return
        }
        debugBump(ctx, "c4_ticks_when_enabled")

        val disarmMinutes = ProtectPrefs.deadmanDisarmMinutesAfterUnlock(ctx)
        if (disarmMinutes > 0) {
            val lastUnlock = ProtectPrefs.lastUnlockMs(ctx)
            if (System.currentTimeMillis() - lastUnlock < disarmMinutes * 60_000L) {
                debugBump(ctx, "c4_skip_disarm_window")
                return
            }
        }

        val level = batteryLevel(ctx)
        val threshold = ProtectPrefs.deadmanBatteryPct(ctx)
        debugStore(ctx, "c4_last_battery_level", level)
        debugStore(ctx, "c4_last_threshold", threshold)
        if (level > threshold) {
            debugBump(ctx, "c4_skip_battery_above_threshold")
            // The situation that raised the alert has resolved, so release the guard
            // rather than waiting out its expiry, and take the alert down with it.
            countdownActive = false
            clearAlert(ctx)
            return
        }

        debugBump(ctx, "c4_reached_post_battery")
        val requireBt = ProtectPrefs.deadmanRequireBt(ctx)
        val requireGsm = ProtectPrefs.deadmanRequireGsm(ctx)
        val requireWifi = ProtectPrefs.deadmanRequireWifi(ctx)
        debugBump(ctx, "c4_reached_post_prefs")
        debugStore(ctx, "c4_bt_up", if (isBluetoothUp(ctx)) 1 else 0)
        debugStore(ctx, "c4_gsm_up", if (isCellularConnected(ctx)) 1 else 0)
        debugStore(ctx, "c4_wifi_up", if (isWifiConnected(ctx)) 1 else 0)
        debugStore(ctx, "c4_require_bt", if (requireBt) 1 else 0)
        debugStore(ctx, "c4_require_gsm", if (requireGsm) 1 else 0)
        debugStore(ctx, "c4_require_wifi", if (requireWifi) 1 else 0)

        if (requireBt && isBluetoothUp(ctx)) {
            debugBump(ctx, "c4_skip_bt_up")
            return
        }
        if (requireGsm && isCellularConnected(ctx)) {
            debugBump(ctx, "c4_skip_gsm_up")
            return
        }
        if (requireWifi && isWifiConnected(ctx)) {
            debugBump(ctx, "c4_skip_wifi_up")
            return
        }

        if (countdownActive) {
            debugBump(ctx, "c4_skip_countdown_active")
            return
        }

        debugBump(ctx, "c4_countdown_launched")
        countdownActive = true
        postFullScreenAlert(ctx)
    }

    // --- Private helpers ---

    /**
     * Reads battery percentage from the ACTION_BATTERY_CHANGED sticky broadcast.
     * BATTERY_PROPERTY_CAPACITY reads the live hardware directly and ignores
     * `dumpsys battery set level`, which makes the trigger untestable without
     * physically draining the battery. The sticky-broadcast path reflects
     * both real readings and `dumpsys battery` fakes.
     */
    private fun batteryLevel(ctx: Context): Int {
        val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return 100
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return 100
        return (level * 100) / scale
    }

    /**
     * "Bluetooth is up" = adapter exists AND is currently enabled.
     * The previous implementation checked `bondedDevices.isNotEmpty()`, which
     * returns true for any historically-paired peripheral even when BT is
     * disabled via airplane mode — causing the dead-man check to never fire.
     */
    private fun isBluetoothUp(ctx: Context): Boolean = runCatching {
        val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        val adapter: BluetoothAdapter? = bm?.adapter
        adapter != null && adapter.isEnabled
    }.getOrDefault(false)

    private fun isCellularConnected(ctx: Context): Boolean =
        hasTransport(ctx, NetworkCapabilities.TRANSPORT_CELLULAR)

    private fun isWifiConnected(ctx: Context): Boolean =
        hasTransport(ctx, NetworkCapabilities.TRANSPORT_WIFI)

    private fun hasTransport(ctx: Context, transport: Int): Boolean = runCatching {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(network) ?: return@runCatching false
        caps.hasTransport(transport)
    }.getOrDefault(false)

    /**
     * Posts a high-importance notification whose fullScreenIntent points to
     * [WipeCountdownActivity]. On locked devices this is the approved Android
     * pattern for taking over the screen — a raw startActivity() from an FGS
     * is silently suppressed on Android 10+.
     */
    private fun postFullScreenAlert(ctx: Context) {
        val activityIntent = Intent(ctx, WipeCountdownActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val fullScreenPI = android.app.PendingIntent.getActivity(
            ctx,
            0,
            activityIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = android.app.Notification.Builder(ctx, "deadman")
            .setSmallIcon(com.norypt.protect.R.mipmap.ic_launcher)
            .setContentTitle("Norypt Protect")
            .setContentText("Auto-wipe countdown active — tap to respond")
            .setCategory(android.app.Notification.CATEGORY_ALARM)
            .setPriority(android.app.Notification.PRIORITY_MAX)
            .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPI, true)
            .build()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID_DEADMAN, notif)
    }

    const val NOTIF_ID_DEADMAN = 5001

    /**
     * Clears the full-screen alert. It is posted setOngoing(true), so nothing dismisses it
     * on its own: after a cancelled or aborted countdown a non-dismissible "Auto-wipe
     * countdown active" notification stayed on the lockscreen indefinitely, telling the user
     * a wipe was pending when it was not.
     */
    fun clearAlert(ctx: Context) {
        runCatching {
            ctx.getSystemService(android.app.NotificationManager::class.java)
                ?.cancel(NOTIF_ID_DEADMAN)
        }
    }

    private fun debugBump(ctx: Context, key: String) = DebugTelemetry.bump(ctx, key)

    private fun debugStore(ctx: Context, key: String, value: Int) =
        DebugTelemetry.put(ctx, key, value)
}

object DeadmanTrigger : Trigger {
    override val id = "C4"
    override val label = "Low-battery dead-man switch"
    override val description =
        "Starts a wipe countdown when battery is low and all monitored connections are lost. " +
        "Requires Device Owner — the wipe call is denied for non-DO admins on Android 13+."
    override val requiredTier = Tier.DeviceOwner

    override fun arm(context: Context) {
        ProtectPrefs.setTriggerEnabled(context, id, true)
        DeadmanScheduler.schedule(context)
    }

    override fun disarm(context: Context) {
        ProtectPrefs.setTriggerEnabled(context, id, false)
        DeadmanScheduler.cancel(context)
    }
}
