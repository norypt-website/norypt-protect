package com.norypt.protect.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun PinEntryDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = NoryptColors.TextStrong, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 12 && it.all(Char::isDigit)) {
                            pin = it
                            error = false
                        }
                    },
                    label = { Text("App PIN") },
                    isError = error,
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = noryptFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (error) "Incorrect PIN" else "6 to 12 digits",
                    color = if (error) NoryptColors.Red else NoryptColors.MutedDeep,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length >= 6,
                onClick = {
                    onConfirm(pin)
                    error = true // caller can override by dismissing
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
