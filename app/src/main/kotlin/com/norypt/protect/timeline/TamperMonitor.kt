package com.norypt.protect.timeline

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.biometrics.BiometricManager
import android.os.SystemClock
import android.provider.Settings
import com.norypt.protect.admin.Provisioning
import com.norypt.protect.admin.Tier
import com.norypt.protect.prefs.ProtectPrefs
import kotlin.math.abs

/**
 * Feeds the tamper timeline from what Android will tell an ordinary app.
 *
 * Two sources: runtime-registered broadcasts (USB state, SIM state, clock changes — none of
 * which reach a manifest receiver on modern Android) and a set of sentinels polled on the
 * foreground-service tick, each comparing the current state against a stored baseline so the
 * timeline records changes, never states.
 *
 * Started and stopped with the foreground service, like the trigger monitors.
 */
object TamperMonitor {

    private const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
    private const val ACTION_SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED"
    private const val CLOCK_STEP_THRESHOLD_MS = 60_000L
    private const val BIOMETRIC_PROBE_INTERVAL_MS = 5 * 60_000L
    private val USB_DATA_FUNCTIONS = listOf(
        "mtp", "ptp", "adb", "midi", "accessory", "ncm", "rndis", "uvc", "audio_source",
    )

    private var receiver: BroadcastReceiver? = null

    /** "connected/data". Seeded by the sticky replay on registration, so the first delivery is never an event. */
    private var usbSignature: String? = null

    /** Wall clock minus monotonic clock; a step in it is a clock change. In-memory: a new process re-seeds. */
    private var clockOffsetMs: Long = Long.MIN_VALUE
    private var lastBiometricProbeElapsedMs = 0L

    fun start(ctx: Context) {
        if (receiver != null) return
        usbSignature = null
        clockOffsetMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    ACTION_USB_STATE -> onUsbState(c, i)
                    ACTION_SIM_STATE_CHANGED -> checkSim(c)
                    Intent.ACTION_TIME_CHANGED -> onTimeChanged(c)
                }
            }
        }
        receiver = r
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_STATE)
            addAction(ACTION_SIM_STATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        ctx.applicationContext.registerReceiver(r, filter)
    }

    fun stop(ctx: Context) {
        receiver?.let { runCatching { ctx.applicationContext.unregisterReceiver(it) } }
        receiver = null
    }

    /** Service tick. Cheap when recording is off: one preference read. */
    fun tick(ctx: Context) {
        if (!TamperLog.isEnabled(ctx)) return
        TamperLog.heartbeat(ctx)
        checkSim(ctx)
        checkAdb(ctx)
        val secure = checkLockScreen(ctx)
        checkBiometrics(ctx, secure)
        checkFailedAttempts(ctx)
    }

    /** Stores every baseline silently, so turning recording on never reports existing state as a change. */
    fun reseed(ctx: Context) {
        val sim = SimSnapshot.current(ctx)
        if (!sim.transient) ProtectPrefs.setTimelineSimSnapshot(ctx, SimSnapshot.encode(sim))
        ProtectPrefs.setTimelineAdbEnabled(ctx, adbEnabled(ctx))
        val secure = isDeviceSecure(ctx)
        ProtectPrefs.setTimelineDeviceSecure(ctx, if (secure) 1 else 0)
        val status = biometricStatus(ctx)
        ProtectPrefs.setTimelineBiometricStatus(ctx, status)
        setSentinelArmed(ctx, secure && status == BiometricManager.BIOMETRIC_SUCCESS)
        ProtectPrefs.setTimelineFailedAttemptsSeen(ctx, failedAttempts(ctx) ?: 0)
        TamperBootAudit.bootCount(ctx)?.let { ProtectPrefs.setTimelineLastBootCount(ctx, it) }
        ProtectPrefs.setTimelineLastAliveMs(ctx, System.currentTimeMillis())
    }

    // --- USB ---

    private fun onUsbState(ctx: Context, intent: Intent) {
        val connected = intent.getBooleanExtra("connected", false)
        val functions = USB_DATA_FUNCTIONS.filter { intent.getBooleanExtra(it, false) }
        val data = connected && (functions.isNotEmpty() || intent.getBooleanExtra("data_unlocked", false))
        val signature = "$connected/$data"
        val previous = usbSignature
        // Tracked even while recording is off, so switching it on mid-session has a baseline.
        usbSignature = signature
        if (previous == null || previous == signature || !TamperLog.isEnabled(ctx)) return

        val locked = isLocked(ctx)
        val previousHadData = previous.endsWith("/true")
        when {
            !connected -> TamperLog.record(
                ctx,
                TamperKind.USB_DISCONNECTED,
                if (previousHadData) "Data link ended." else "",
            )
            data -> {
                val link = functions.ifEmpty { listOf("data unlocked") }.joinToString(", ")
                TamperLog.record(
                    ctx,
                    TamperKind.USB_CONNECTED,
                    "Data link negotiated ($link)" + if (locked) " while the device was LOCKED." else ".",
                    if (locked) Severity.Alert else Severity.Notable,
                )
            }
            previousHadData -> TamperLog.record(ctx, TamperKind.USB_DISCONNECTED, "Data link ended; still connected.")
            else -> TamperLog.record(
                ctx,
                TamperKind.USB_CONNECTED,
                "Charge-only, no data function negotiated" + if (locked) " (device locked)." else ".",
            )
        }
    }

    // --- SIM ---

    private fun checkSim(ctx: Context) {
        if (!TamperLog.isEnabled(ctx)) return
        val current = SimSnapshot.current(ctx)
        // A slot still initialising says nothing about the card; wait for the next look.
        if (current.transient) return
        val encoded = SimSnapshot.encode(current)
        val storedRaw = ProtectPrefs.timelineSimSnapshot(ctx)
        if (storedRaw == encoded) return
        ProtectPrefs.setTimelineSimSnapshot(ctx, encoded)
        if (storedRaw == null) return
        SimSnapshot.describeChange(SimSnapshot.decode(storedRaw), current)?.let {
            TamperLog.record(ctx, TamperKind.SIM_CHANGED, it)
        }
    }

    // --- USB debugging ---

    private fun checkAdb(ctx: Context) {
        val current = adbEnabled(ctx)
        val previous = ProtectPrefs.timelineAdbEnabled(ctx)
        if (previous == current) return
        ProtectPrefs.setTimelineAdbEnabled(ctx, current)
        if (previous == -1) return
        if (current == 1) {
            TamperLog.record(
                ctx,
                TamperKind.USB_DEBUGGING,
                "USB debugging was turned ON. An authorised computer can now run commands on this phone.",
                Severity.Alert,
            )
        } else {
            TamperLog.record(ctx, TamperKind.USB_DEBUGGING, "USB debugging was turned off.", Severity.Info)
        }
    }

    // --- Lock screen ---

    /** Returns whether a secure lock screen is set. */
    private fun checkLockScreen(ctx: Context): Boolean {
        val secure = isDeviceSecure(ctx)
        val current = if (secure) 1 else 0
        val previous = ProtectPrefs.timelineDeviceSecure(ctx)
        if (previous != current) {
            ProtectPrefs.setTimelineDeviceSecure(ctx, current)
            if (previous != -1) {
                TamperLog.record(
                    ctx,
                    if (secure) TamperKind.SCREEN_LOCK_SET else TamperKind.SCREEN_LOCK_REMOVED,
                    if (secure) "A PIN, pattern or password is set again." else "The device no longer requires a credential to unlock.",
                )
            }
        }
        return secure
    }

    // --- Biometrics ---

    private fun checkBiometrics(ctx: Context, secure: Boolean) {
        val status = biometricStatus(ctx)
        val previous = ProtectPrefs.timelineBiometricStatus(ctx)
        val wantSentinel = secure && status == BiometricManager.BIOMETRIC_SUCCESS
        if (previous == Int.MIN_VALUE) {
            ProtectPrefs.setTimelineBiometricStatus(ctx, status)
            setSentinelArmed(ctx, wantSentinel)
            return
        }
        if (status != previous) {
            ProtectPrefs.setTimelineBiometricStatus(ctx, status)
            describeStatusChange(previous, status)?.let { TamperLog.record(ctx, TamperKind.BIOMETRIC_CHANGED, it) }
            setSentinelArmed(ctx, wantSentinel)
            return
        }
        if (!wantSentinel) {
            setSentinelArmed(ctx, false)
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (lastBiometricProbeElapsedMs != 0L && now - lastBiometricProbeElapsedMs < BIOMETRIC_PROBE_INTERVAL_MS) return
        lastBiometricProbeElapsedMs = now
        when (BiometricSentinel.probe()) {
            BiometricSentinel.Probe.INVALIDATED -> {
                TamperLog.record(
                    ctx,
                    TamperKind.BIOMETRIC_CHANGED,
                    "A fingerprint or face was added or removed (keystore sentinel invalidated).",
                )
                ProtectPrefs.setTimelineSentinelArmed(ctx, false)
                setSentinelArmed(ctx, true)
            }
            BiometricSentinel.Probe.MISSING -> {
                ProtectPrefs.setTimelineSentinelArmed(ctx, false)
                setSentinelArmed(ctx, true)
            }
            BiometricSentinel.Probe.OK, BiometricSentinel.Probe.UNAVAILABLE -> Unit
        }
    }

    private fun describeStatusChange(previous: Int, current: Int): String? = when {
        previous == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED && current == BiometricManager.BIOMETRIC_SUCCESS ->
            "A fingerprint or face was enrolled; none was enrolled before."
        previous == BiometricManager.BIOMETRIC_SUCCESS && current == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            "All fingerprints and faces were removed."
        else -> null
    }

    private fun setSentinelArmed(ctx: Context, wanted: Boolean) {
        val armed = ProtectPrefs.timelineSentinelArmed(ctx)
        if (wanted && !armed) {
            ProtectPrefs.setTimelineSentinelArmed(ctx, BiometricSentinel.arm())
        } else if (!wanted && armed) {
            BiometricSentinel.disarm()
            ProtectPrefs.setTimelineSentinelArmed(ctx, false)
        }
    }

    // --- Failed unlocks (Device Admin tier only; Device Owner gets the exact callback) ---

    private fun checkFailedAttempts(ctx: Context) {
        if (Provisioning.current(ctx) != Tier.DeviceAdmin) return
        val current = failedAttempts(ctx) ?: return
        val seen = ProtectPrefs.timelineFailedAttemptsSeen(ctx)
        if (current > seen) {
            TamperLog.record(
                ctx,
                TamperKind.UNLOCK_FAILED,
                "${current - seen} failed attempt(s) since the last check ($current in a row). " +
                    "Detected by polling, so the time is approximate.",
            )
        }
        if (current != seen) ProtectPrefs.setTimelineFailedAttemptsSeen(ctx, current)
    }

    // --- Clock ---

    private fun onTimeChanged(ctx: Context) {
        val offset = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val previous = clockOffsetMs
        clockOffsetMs = offset
        if (previous == Long.MIN_VALUE) return
        val delta = offset - previous
        // Network time corrections are seconds; only a deliberate change moves the clock by minutes.
        if (abs(delta) < CLOCK_STEP_THRESHOLD_MS) return
        TamperLog.record(
            ctx,
            TamperKind.CLOCK_CHANGED,
            "Clock moved ${if (delta > 0) "forward" else "back"} by ${TimelineFormat.duration(abs(delta))}. " +
                "Entries before this one carry the old clock's time.",
        )
    }

    // --- Platform reads ---

    private fun adbEnabled(ctx: Context): Int = runCatching {
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0)
    }.getOrDefault(0).coerceIn(0, 1)

    private fun isDeviceSecure(ctx: Context): Boolean =
        ctx.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    private fun isLocked(ctx: Context): Boolean =
        ctx.getSystemService(KeyguardManager::class.java)?.let { it.isKeyguardLocked || it.isDeviceLocked } == true

    private fun biometricStatus(ctx: Context): Int = runCatching {
        ctx.getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            ?: BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
    }.getOrDefault(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)

    private fun failedAttempts(ctx: Context): Int? = runCatching {
        (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).currentFailedPasswordAttempts
    }.getOrNull()
}
