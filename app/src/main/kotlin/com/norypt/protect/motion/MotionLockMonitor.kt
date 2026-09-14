package com.norypt.protect.motion

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.admin.Provisioning
import com.norypt.protect.admin.Tier
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.timeline.TamperKind
import com.norypt.protect.timeline.TamperLog
import com.norypt.protect.util.DebugTelemetry
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Anti-snatch: locks the screen the moment the phone is yanked or dropped.
 *
 * The accelerometer is read only while the screen is on and the keyguard is not showing —
 * the only state in which locking changes anything — so the cost is a few hundred
 * microamps during active use and nothing at all in the pocket or on the desk.
 *
 * Started and stopped with the foreground service; the receiver tracks screen and unlock
 * transitions and (un)registers the sensor accordingly.
 */
object MotionLockMonitor : SensorEventListener {

    private var receiver: BroadcastReceiver? = null
    private var appContext: Context? = null
    private var listening = false
    private var detector: MotionDetector? = null
    private var hasLinearSensor = false

    fun start(ctx: Context) {
        if (receiver != null) return
        appContext = ctx.applicationContext
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> stopListening(c)
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> refresh(c)
                }
            }
        }
        receiver = r
        ctx.applicationContext.registerReceiver(
            r,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
        refresh(ctx)
    }

    fun stop(ctx: Context) {
        receiver?.let { runCatching { ctx.applicationContext.unregisterReceiver(it) } }
        receiver = null
        stopListening(ctx)
    }

    fun isEnabled(ctx: Context): Boolean = ProtectPrefs.motionLockEnabled(ctx)

    fun enable(ctx: Context) {
        ProtectPrefs.setMotionLockEnabled(ctx, true)
        refresh(ctx)
    }

    fun disable(ctx: Context) {
        ProtectPrefs.setMotionLockEnabled(ctx, false)
        refresh(ctx)
    }

    fun sensitivity(ctx: Context): Int = ProtectPrefs.motionLockSensitivity(ctx)

    fun setSensitivity(ctx: Context, level: Int) {
        ProtectPrefs.setMotionLockSensitivity(ctx, level)
        if (listening) {
            stopListening(ctx)
            refresh(ctx)
        }
    }

    /** Whether this device can run the feature at all. */
    fun hasSensor(ctx: Context): Boolean {
        val sm = ctx.getSystemService(SensorManager::class.java) ?: return false
        return sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }

    /** Registers or releases the sensor to match the current screen, keyguard and preference state. */
    fun refresh(ctx: Context) {
        val wanted = isEnabled(ctx) && Provisioning.current(ctx) != Tier.None
        val interactive = ctx.getSystemService(PowerManager::class.java)?.isInteractive == true
        val keyguardShowing = ctx.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != false
        if (wanted && interactive && !keyguardShowing) startListening(ctx) else stopListening(ctx)
    }

    private fun startListening(ctx: Context) {
        if (listening) return
        val sm = ctx.getSystemService(SensorManager::class.java) ?: return
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val linear = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        hasLinearSensor = linear != null
        detector = MotionDetector(MotionDetector.jerkThresholdFor(sensitivity(ctx)))
        // Warm-up starts at the first sample: sensor timestamps are elapsedRealtimeNanos.
        sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        linear?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        listening = true
        DebugTelemetry.bump(ctx, "motion_listen_started")
    }

    private fun stopListening(ctx: Context) {
        if (!listening) return
        ctx.getSystemService(SensorManager::class.java)?.unregisterListener(this)
        listening = false
        detector = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val d = detector ?: return
        val nowMs = event.timestamp / NANOS_PER_MS
        val v = event.values
        val magnitude = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        val result = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                d.onAcceleration(nowMs, magnitude)
                    // Without a dedicated linear sensor, approximate it from the same reading.
                    ?: if (!hasLinearSensor) d.onLinearAcceleration(nowMs, abs(magnitude - SensorManager.GRAVITY_EARTH)) else null
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> d.onLinearAcceleration(nowMs, magnitude)
            else -> null
        }
        result?.let { onMotion(it) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun onMotion(event: MotionDetector.Event) {
        val ctx = appContext ?: return
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(ctx, ProtectAdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) return
        DebugTelemetry.bump(ctx, "motion_lock_fired")
        val locked = runCatching { dpm.lockNow(); true }.getOrDefault(false)
        TamperLog.record(
            ctx,
            TamperKind.MOTION_LOCK,
            when (event) {
                MotionDetector.Event.SNATCH -> "Sudden pull detected"
                MotionDetector.Event.DROP -> "Free fall detected"
            } + if (locked) "; screen locked." else "; lock call failed.",
        )
        // The screen is locked now; SCREEN_OFF and the next USER_PRESENT re-evaluate.
        stopListening(ctx)
    }

    private const val NANOS_PER_MS = 1_000_000L
}
