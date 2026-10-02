package com.norypt.protect.triggers

import android.app.KeyguardManager
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
import com.norypt.protect.service.CountdownAlert
import com.norypt.protect.service.CountdownMode
import com.norypt.protect.timeline.TamperBootAudit
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

    /** How long C4 stays quiet after the owner cancelled a countdown with the credential. */
    const val SNOOZE_AFTER_CANCEL_MS = 30 * 60_000L

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

        // C4 is for a locked phone left to die. The owner using it, a charger, or a recent
        // cancel with the credential all mean that is not what is happening.
        val ownerUsingIt = ctx.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false
        if (ownerUsingIt || isCharging(ctx) || isSnoozed(ctx)) {
            debugBump(ctx, "c4_skip_owner_or_charging")
            countdownActive = false
            CountdownAlert.forgetDeadline(ctx, CountdownMode.DEADMAN)
            return
        }

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
            CountdownAlert.forgetDeadline(ctx, CountdownMode.DEADMAN)
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

        val connected = (requireBt && isBluetoothUp(ctx)) ||
            (requireGsm && isCellularConnected(ctx)) ||
            (requireWifi && isWifiConnected(ctx))
        if (connected) {
            debugBump(ctx, "c4_skip_connection_up")
            CountdownAlert.forgetDeadline(ctx, CountdownMode.DEADMAN)
            return
        }

        if (countdownActive || UnlockDeadlineMonitor.countdownActive || UnlockedTimerMonitor.countdownActive) {
            debugBump(ctx, "c4_skip_countdown_active")
            return
        }

        debugBump(ctx, "c4_countdown_launched")
        countdownActive = true
        CountdownAlert.post(ctx, CountdownMode.DEADMAN)
    }

    /** Plugged into any power source, from the sticky battery broadcast. */
    fun isCharging(ctx: Context): Boolean {
        val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        return intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    }

    private fun isSnoozed(ctx: Context): Boolean {
        val until = ProtectPrefs.deadmanSnoozeUntil(ctx)
        if (until <= 0L) return false
        // elapsedRealtime restarts at boot, so a snooze from an earlier boot no longer applies.
        if (ProtectPrefs.deadmanSnoozeBoot(ctx) != (TamperBootAudit.bootCount(ctx) ?: 0)) return false
        return SystemClock.elapsedRealtime() < until
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

    val NOTIF_ID_DEADMAN: Int = CountdownMode.DEADMAN.notificationId

    /** Takes the C4 alert down; see [CountdownAlert.clear] for why this has to be explicit. */
    fun clearAlert(ctx: Context) = CountdownAlert.clear(ctx, CountdownMode.DEADMAN)

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
        // Re-evaluates rather than cancels outright: C6 shares the alarm chain.
        DeadmanScheduler.schedule(context)
    }
}
