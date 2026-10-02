package com.norypt.protect.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.security.AppPin
import com.norypt.protect.security.PinCheck
import com.norypt.protect.security.PinLockout
import com.norypt.protect.ui.theme.NoryptColors

/** Text-field colours shared by every PIN and number entry in the app. */
@Composable
fun noryptFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NoryptColors.Accent,
    unfocusedBorderColor = NoryptColors.BorderStrong,
    errorBorderColor = NoryptColors.Red,
    focusedTextColor = NoryptColors.TextStrong,
    unfocusedTextColor = NoryptColors.Text,
    focusedLabelColor = NoryptColors.Accent,
    unfocusedLabelColor = NoryptColors.Muted,
    errorLabelColor = NoryptColors.Red,
    cursorColor = NoryptColors.Accent,
    focusedContainerColor = NoryptColors.Surface1,
    unfocusedContainerColor = NoryptColors.Surface1,
    errorContainerColor = NoryptColors.Surface1,
)

/**
 * The app's one PIN prompt. It checks the PIN itself through [AppPin.check], so the lockout
 * applies wherever a PIN is asked for, and callers only learn that the owner was verified.
 */
@Composable
fun PinEntryDialog(
    title: String,
    onVerified: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var lockedMs by remember { mutableLongStateOf(PinLockout.remainingLockoutMs(ctx)) }
    var message by remember { mutableStateOf(if (lockedMs > 0L) lockoutText(lockedMs) else null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = NoryptColors.TextStrong, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (AppPin.isPinInput(it)) {
                            pin = it
                            if (lockedMs == 0L) message = null
                        }
                    },
                    label = { Text("App PIN") },
                    isError = message != null,
                    enabled = lockedMs == 0L,
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = noryptFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    message ?: "${AppPin.MIN_LENGTH} to ${AppPin.MAX_LENGTH} digits",
                    color = if (message != null) NoryptColors.Red else NoryptColors.MutedDeep,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length >= AppPin.MIN_LENGTH && lockedMs == 0L,
                onClick = {
                    when (val result = AppPin.check(ctx, pin)) {
                        PinCheck.Ok -> onVerified()
                        is PinCheck.Wrong ->
                            message = "Incorrect PIN (${result.attempts}/${PinLockout.MAX_ATTEMPTS})"
                        is PinCheck.LockedOut -> {
                            lockedMs = result.remainingMs
                            message = lockoutText(result.remainingMs)
                        }
                    }
                    pin = ""
                },
            ) { Text("Confirm", color = NoryptColors.Accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = NoryptColors.Muted) }
        },
        containerColor = NoryptColors.Surface2,
        shape = RoundedCornerShape(16.dp),
    )
}

/** "Too many attempts" text shared by every PIN entry. */
fun lockoutText(remainingMs: Long): String {
    val seconds = ((remainingMs + 999L) / 1000L).toInt()
    return "Too many attempts. Try again in ${seconds / 60}m ${seconds % 60}s."
}
