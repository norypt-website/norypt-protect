package com.norypt.protect.ui.screens

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.R
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.security.AppPin
import com.norypt.protect.security.PinLockout
import com.norypt.protect.ui.components.NoteCard
import com.norypt.protect.ui.components.PrimaryButton
import com.norypt.protect.ui.components.SecondaryButton
import com.norypt.protect.ui.components.noryptFieldColors
import com.norypt.protect.ui.theme.NoryptColors
import kotlinx.coroutines.delay

/**
 * App-launch gate. Shown before main content whenever an App-PIN is configured.
 *
 * - PIN is always required (primary factor).
 * - Biometric is an optional shortcut gated on [ProtectPrefs.launchBiometricEnabled].
 *   Uses the platform [BiometricPrompt] (API 28+) so we don't need FragmentActivity.
 *   Biometrics never replace the PIN — if authentication fails/cancels, user falls
 *   back to the PIN entry.
 */
@Composable
fun LaunchGateScreen(onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // Persisted, not composition state: a rotation recreates the Activity and force-stopping
    // clears the process, so an in-memory counter is reset by the very action the old
    // lockout message told the user to perform.
    var attempts by remember { mutableStateOf(PinLockout.attempts(ctx)) }
    var lockedRemainingMs by remember { mutableLongStateOf(PinLockout.remainingLockoutMs(ctx)) }
    val lockedOut = lockedRemainingMs > 0L

    // Ticks the lockout down so the gate re-enables itself without needing a relaunch.
    LaunchedEffect(lockedOut) {
        while (PinLockout.remainingLockoutMs(ctx) > 0L) {
            lockedRemainingMs = PinLockout.remainingLockoutMs(ctx)
            delay(1_000L)
        }
        lockedRemainingMs = 0L
    }

    val biometricEnabled = remember { ProtectPrefs.launchBiometricEnabled(ctx) }
    val canUseBiometric = remember {
        ctx.getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    val showBiometric = biometricEnabled && canUseBiometric

    LaunchedEffect(Unit) {
        if (showBiometric) {
            promptBiometric(
                ctx,
                onSuccess = onUnlocked,
                onFail = { /* user can fall back to PIN */ },
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(NoryptColors.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GateLogo()
            Text(
                "Norypt Protect",
                color = NoryptColors.TextStrong,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
            )
            Text(
                "Enter your App PIN to continue",
                color = NoryptColors.Muted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it.filter(Char::isDigit).take(12)
                    error = null
                },
                label = { Text("App PIN") },
                singleLine = true,
                enabled = !lockedOut,
                isError = error != null,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                colors = noryptFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Text(error.orEmpty(), color = NoryptColors.Red, fontSize = 12.sp)
            }
            PrimaryButton(
                label = "Unlock",
                enabled = pin.length >= 6 && !lockedOut,
                onClick = {
                    if (AppPin.verify(ctx, pin)) {
                        PinLockout.recordSuccess(ctx)
                        onUnlocked()
                    } else {
                        attempts = PinLockout.recordFailure(ctx)
                        lockedRemainingMs = PinLockout.remainingLockoutMs(ctx)
                        error = if (lockedRemainingMs > 0L) null
                                else "Incorrect PIN ($attempts/${PinLockout.MAX_ATTEMPTS})"
                        pin = ""
                    }
                },
            )
            if (showBiometric) {
                SecondaryButton(
                    label = "Use biometric",
                    onClick = {
                        promptBiometric(
                            ctx,
                            onSuccess = onUnlocked,
                            onFail = { error = "Biometric declined — enter PIN" },
                        )
                    },
                )
            }
            if (lockedOut) {
                val seconds = ((lockedRemainingMs + 999L) / 1000L).toInt()
                NoteCard(
                    text = "Too many incorrect PIN attempts. Try again in ${seconds / 60}m ${seconds % 60}s.",
                    color = NoryptColors.Red,
                )
            }
        }
    }
}

@Composable
private fun GateLogo() {
    Box(
        Modifier
            .size(84.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(NoryptColors.Surface2)
            .border(1.dp, NoryptColors.Border, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.norypt_logo),
            contentDescription = null,
            modifier = Modifier.size(52.dp),
        )
    }
}

private fun promptBiometric(
    ctx: Context,
    onSuccess: () -> Unit,
    onFail: () -> Unit,
) {
    val activity = ctx as? Activity ?: run { onFail(); return }
    val cancellation = CancellationSignal()
    val executor = ctx.mainExecutor
    val prompt = BiometricPrompt.Builder(activity)
        .setTitle("Unlock Norypt Protect")
        .setSubtitle("Fingerprint or face to open the app")
        .setNegativeButton("Use PIN", executor) { _, _ -> onFail() }
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()
    prompt.authenticate(
        cancellation,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFail()
            }
            override fun onAuthenticationFailed() {
                // keep prompt open so user can retry
            }
        },
    )
}
