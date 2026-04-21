package com.norypt.protect.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.norypt.protect.ui.theme.NoryptColors

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
        title = { Text(title, color = NoryptColors.Text) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 12 && it.all(Char::isDigit)) {
                            pin = it
                            error = false
                        }
                    },
                    isError = error,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error) {
                    Spacer(Modifier.height(4.dp))
                    Text("Incorrect PIN", color = NoryptColors.Red)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length >= 6,
                onClick = {
                    onConfirm(pin)
                    error = true // caller can override by dismissing
                }
            ) { Text("Confirm", color = NoryptColors.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = NoryptColors.Muted) }
        },
        containerColor = NoryptColors.Surface,
    )
}
