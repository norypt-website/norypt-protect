package com.norypt.protect.service

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.UserHandle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.norypt.protect.MainActivity
import com.norypt.protect.dpm.LockdownMode
import com.norypt.protect.security.AppPin
import com.norypt.protect.security.PinLockout
import com.norypt.protect.ui.components.PinEntryDialog
import com.norypt.protect.ui.theme.NoryptColors
import com.norypt.protect.ui.theme.NoryptProtectTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The blank screen of lockdown mode. While lockdown is on this is the device's HOME activity
 * and the only thing allowed in lock-task mode, so it is all anyone holding the phone sees.
 *
 * The way in: hold a finger anywhere for [HOLD_MS], then enter the App PIN (throttled by
 * the same lockout as the launch gate). The panel behind it lets the owner open Settings,
 * switch user, open Norypt Protect, or end lockdown. Every return to the blank screen
 * requires the PIN again, and the panel hides itself after [PANEL_TIMEOUT_MS].
 *
 * Policies are re-applied on every resume ([LockdownMode.applyPolicies]), which is what
 * makes a Settings excursion or a user switch heal on the way back.
 */
class LockdownHomeActivity : ComponentActivity() {

    private val authenticated = mutableStateOf(false)

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            runCatching { stopLockTask() }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        registerReceiver(stopReceiver, IntentFilter(LockdownMode.ACTION_STOP), Context.RECEIVER_NOT_EXPORTED)

        setContent {
            NoryptProtectTheme {
                LockdownScreen(
                    authenticated = authenticated.value,
                    onAuthenticated = { authenticated.value = true },
                    onHidePanel = { authenticated.value = false },
                    onOpenSettings = {
                        authenticated.value = false
                        LockdownMode.startSettingsExcursion(this)
                    },
                    onSwitchUser = { user ->
                        authenticated.value = false
                        LockdownMode.switchUser(this, user)
                    },
                    onOpenApp = {
                        authenticated.value = false
                        startActivity(Intent(this, MainActivity::class.java))
                    },
                    onExitLockdown = {
                        authenticated.value = false
                        runCatching { stopLockTask() }
                        LockdownMode.disable(this)
                        LockdownMode.launchDefaultHome(this)
                        finish()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!LockdownMode.isEnabled(this)) {
            runCatching { stopLockTask() }
            finish()
            return
        }
        // Whatever was lifted for an excursion comes back here, then the task is pinned again.
        LockdownMode.applyPolicies(this)
        authenticated.value = false
        val am = getSystemService(ActivityManager::class.java)
        if (am?.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            runCatching { startLockTask() }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(stopReceiver) }
        super.onDestroy()
    }

    companion object {
        const val HOLD_MS = 3_000L
        const val PANEL_TIMEOUT_MS = 60_000L
    }
}

@Composable
private fun LockdownScreen(
    authenticated: Boolean,
    onAuthenticated: () -> Unit,
    onHidePanel: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchUser: (UserHandle) -> Unit,
    onOpenApp: () -> Unit,
    onExitLockdown: () -> Unit,
) {
    var showPin by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                // A press held for HOLD_MS without lifting. Nothing is drawn while holding,
                // so the screen gives no hint that anything is behind it.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val up = withTimeoutOrNull(LockdownHomeActivity.HOLD_MS) { waitForUpOrCancellation() }
                    if (up == null && !authenticated) showPin = true
                }
            },
    ) {
        if (authenticated) {
            LaunchedEffect(Unit) {
                delay(LockdownHomeActivity.PANEL_TIMEOUT_MS)
                onHidePanel()
            }
            LockdownPanel(onHidePanel, onOpenSettings, onSwitchUser, onOpenApp, onExitLockdown)
        }
    }

    if (showPin) {
        LockdownPinPrompt(
            onSuccess = {
                showPin = false
                onAuthenticated()
            },
            onDismiss = { showPin = false },
        )
    }
}

/** App PIN entry with the launch gate's lockout applied. */
@Composable
private fun LockdownPinPrompt(onSuccess: () -> Unit, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var lockedMs by remember { mutableLongStateOf(PinLockout.remainingLockoutMs(ctx)) }
    if (lockedMs > 0L) {
        val seconds = ((lockedMs + 999L) / 1000L).toInt()
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Too many attempts", color = NoryptColors.Text) },
            text = { Text("Try again in ${seconds / 60}m ${seconds % 60}s.", color = NoryptColors.Muted) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = NoryptColors.Accent) } },
            containerColor = NoryptColors.Surface1,
        )
        return
    }
    PinEntryDialog(
        title = "App PIN",
        onConfirm = { pin ->
            if (AppPin.verify(ctx, pin)) {
                PinLockout.recordSuccess(ctx)
                onSuccess()
            } else {
                PinLockout.recordFailure(ctx)
                lockedMs = PinLockout.remainingLockoutMs(ctx)
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun LockdownPanel(
    onHidePanel: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchUser: (UserHandle) -> Unit,
    onOpenApp: () -> Unit,
    onExitLockdown: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val users = remember { LockdownMode.secondaryUsers(ctx) }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NoryptColors.Surface2)
                .border(1.dp, NoryptColors.Border, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("LOCKDOWN ACTIVE", color = NoryptColors.Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                "This panel closes on its own after a minute, and whenever you leave it. " +
                    "The blank screen and the PIN hold come back each time.",
                color = NoryptColors.Muted,
                fontSize = 12.sp,
            )
        }
        PanelButton("Open Settings", NoryptColors.Accent, onOpenSettings)
        Text(
            "Settings opens outside lockdown; pressing Home or backing out re-locks the device.",
            color = NoryptColors.MutedDeep,
            fontSize = 11.sp,
        )
        Text("SWITCH USER", color = NoryptColors.MutedDeep, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        if (users.isEmpty()) {
            Text("No other users on this device.", color = NoryptColors.Muted, fontSize = 12.sp)
        } else {
            users.forEachIndexed { index, user ->
                val id = Regex("\\d+").find(user.toString())?.value ?: "?"
                PanelButton("Switch to user ${index + 1} (id $id)", NoryptColors.Accent) { onSwitchUser(user) }
            }
        }
        PanelButton("Open Norypt Protect", NoryptColors.Accent, onOpenApp)
        PanelButton("Exit lockdown", NoryptColors.Red, onExitLockdown)
        PanelButton("Back to blank screen", NoryptColors.Muted, onHidePanel)
        Text(
            "Emergency calling from the lock screen is exempt from lock-task mode on Android, " +
                "but verify it on this device before relying on it.",
            color = NoryptColors.MutedDeep,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun PanelButton(label: String, color: Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Text(label, fontSize = 14.sp)
    }
}
