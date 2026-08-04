package com.norypt.protect.service

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.triggers.DeadmanMonitor
import com.norypt.protect.ui.theme.NoryptColors
import com.norypt.protect.ui.theme.NoryptProtectTheme
import com.norypt.protect.util.SuspendableCountdown
import kotlinx.coroutines.delay

/**
 * C4 — Full-screen countdown activity launched over the lockscreen by [DeadmanMonitor].
 *
 * - Shows a countdown timer (grace period from prefs).
 * - Cancel button launches keyguard credential intent; RESULT_OK aborts wipe.
 * - Re-checks conditions on every tick; if connectivity restored → silent abort.
 * - On countdown reaches 0 → [PanicHandler.panic].
 */
class WipeCountdownActivity : ComponentActivity() {

    /**
     * Suspends the countdown while the system credential prompt is in front of us. The
     * prompt only stops this activity, it does not tear down the composition, so without
     * this the timer keeps ticking behind it and can wipe the device part-way through the
     * authentication the user started in order to cancel the wipe.
     */
    private val authInProgress = mutableStateOf(false)

    private lateinit var countdown: SuspendableCountdown

    private val cancelLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            finish()
        } else {
            authInProgress.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lockscreen
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val km = getSystemService(KeyguardManager::class.java)
        km.requestDismissKeyguard(this, null)

        val graceMs = ProtectPrefs.deadmanGraceSeconds(this) * 1_000L
        val now = SystemClock.elapsedRealtime()
        // Restored from saved state so a configuration change continues the countdown
        // instead of restarting the grace period — otherwise repeatedly rotating the
        // phone postpones the wipe indefinitely.
        countdown = SuspendableCountdown(
            startElapsedMs = now,
            graceMs = graceMs,
            maxPauseMs = MAX_PAUSE_MS,
            pauseUsedMs = savedInstanceState?.getLong(STATE_PAUSE_USED) ?: 0L,
            deadlineElapsedMs = savedInstanceState?.getLong(STATE_DEADLINE) ?: (now + graceMs),
        )

        setContent {
            NoryptProtectTheme {
                CountdownScreen(
                    countdown = countdown,
                    paused = authInProgress.value,
                    onCancel = {
                        authInProgress.value = true
                        launchKeyguardCancel()
                    },
                    onConditionsCleared = { finish() },
                    onTimerExpired = {
                        if (!isFinishing) {
                            PanicHandler.panic(this@WipeCountdownActivity, "deadman")
                        }
                        finish()
                    },
                    conditionChecker = { areConditionsCleared() },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_DEADLINE, countdown.deadlineElapsedMs)
        outState.putLong(STATE_PAUSE_USED, countdown.pauseUsedMs)
    }

    /**
     * Resume-on-abandon. The credential prompt can go away without delivering a result —
     * dismissed by the system, killed under memory pressure, or backed out of in a way the
     * launcher does not report. Being resumed proves it is no longer in front of us, so the
     * countdown must not stay suspended on the strength of a callback that never arrives.
     */
    override fun onResume() {
        super.onResume()
        authInProgress.value = false
    }

    override fun onDestroy() {
        DeadmanMonitor.countdownActive = false
        // Whether the user cancelled, the conditions cleared, or the wipe was requested,
        // the ongoing alert has done its job and must not outlive this screen.
        DeadmanMonitor.clearAlert(this)
        super.onDestroy()
    }

    private fun launchKeyguardCancel() {
        val km = getSystemService(KeyguardManager::class.java)
        val intent = km.createConfirmDeviceCredentialIntent(
            "Authorise cancel",
            "Wipe countdown will abort on success. Wipe stays armed otherwise.",
        )
        if (intent != null) {
            cancelLauncher.launch(intent)
        } else {
            // No lock screen set — treat as authorised
            finish()
        }
    }

    /** Returns true if the dead-man conditions are no longer met (battery recovered or connectivity restored). */
    private fun areConditionsCleared(): Boolean {
        val level = batteryLevel(this)
        val threshold = ProtectPrefs.deadmanBatteryPct(this)
        if (level > threshold) return true

        val requireBt = ProtectPrefs.deadmanRequireBt(this)
        val requireGsm = ProtectPrefs.deadmanRequireGsm(this)
        val requireWifi = ProtectPrefs.deadmanRequireWifi(this)

        if (requireBt && isBluetoothConnected(this)) return true
        if (requireGsm && isCellularConnected(this)) return true
        if (requireWifi && isWifiConnected(this)) return true
        return false
    }

    // Reads from ACTION_BATTERY_CHANGED sticky broadcast (matches DeadmanMonitor).
    // BATTERY_PROPERTY_CAPACITY would read hardware directly and ignore
    // `dumpsys battery set level`, which makes the cancel-check race against
    // the tick-side check and dismiss the countdown before it can wipe.
    private fun batteryLevel(ctx: Context): Int {
        val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return 100
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return 100
        return (level * 100) / scale
    }

    // "Bluetooth up" = adapter enabled now (not just historically paired).
    // `bondedDevices.isNotEmpty()` returns true for any past pairing even with
    // BT off — causing the cancel check to prematurely abort the countdown.
    private fun isBluetoothConnected(ctx: Context): Boolean = runCatching {
        val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager?
        bm?.adapter?.isEnabled == true
    }.getOrDefault(false)

    private fun isCellularConnected(ctx: Context): Boolean = hasTransport(ctx, NetworkCapabilities.TRANSPORT_CELLULAR)
    private fun isWifiConnected(ctx: Context): Boolean = hasTransport(ctx, NetworkCapabilities.TRANSPORT_WIFI)

    private fun hasTransport(ctx: Context, transport: Int): Boolean = runCatching {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(network) ?: return@runCatching false
        caps.hasTransport(transport)
    }.getOrDefault(false)
}

@Composable
private fun CountdownScreen(
    countdown: SuspendableCountdown,
    paused: Boolean,
    onCancel: () -> Unit,
    onConditionsCleared: () -> Unit,
    onTimerExpired: () -> Unit,
    conditionChecker: () -> Boolean,
) {
    var secondsLeft by remember {
        mutableIntStateOf(millisToSeconds(countdown.remainingMs(SystemClock.elapsedRealtime())))
    }

    // A single always-running ticker drives the deadline; `paused` is passed into advance()
    // rather than keying the effect, so suspension is bounded by the pause budget instead of
    // by whether this coroutine happens to be alive.
    LaunchedEffect(Unit) {
        var lastConditionCheckSecond = -1
        while (true) {
            delay(TICK_MS)
            val now = SystemClock.elapsedRealtime()
            val remaining = countdown.advance(now, paused)
            secondsLeft = millisToSeconds(remaining)

            // Conditions are polled once a second, not every tick — each check is several
            // binder round-trips to battery, connectivity and Bluetooth.
            //
            // They are not polled at all while the user is authenticating: the credential
            // prompt lighting the screen can bring a radio up briefly, and reading that as
            // "conditions cleared" would silently abort a legitimate wipe.
            if (!paused && secondsLeft != lastConditionCheckSecond) {
                lastConditionCheckSecond = secondsLeft
                if (conditionChecker()) {
                    onConditionsCleared()
                    return@LaunchedEffect
                }
            }
            if (remaining == 0L) {
                onTimerExpired()
                return@LaunchedEffect
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NoryptColors.Red),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = "NORYPT PROTECT",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )

            Text(
                text = "AUTO-WIPE IN",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
            )

            Text(
                text = secondsLeft.toString(),
                color = Color.White,
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold,
            )

            Button(
                onClick = onCancel,
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = NoryptColors.Red,
                ),
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Ticked four times a second so the displayed seconds track the deadline closely. */
private const val TICK_MS = 250L

/**
 * Total time the countdown may spend suspended for credential prompts, across the whole
 * grace period. Bounds the "tap Cancel and walk away" disable.
 */
private const val MAX_PAUSE_MS = 60_000L

private const val STATE_DEADLINE = "countdown_deadline_elapsed_ms"
private const val STATE_PAUSE_USED = "countdown_pause_used_ms"

private fun millisToSeconds(ms: Long): Int = ((ms + 999L) / 1000L).toInt()
