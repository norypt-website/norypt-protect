package com.norypt.protect.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.R
import com.norypt.protect.ui.theme.NoryptColors

@Composable
fun PinSetupScreen(onPinSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val matches = pin == confirm
    val canContinue = pin.length >= 6 && matches

    Column(
        Modifier
            .fillMaxSize()
            .background(NoryptColors.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = R.drawable.norypt_logo),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Norypt Protect",
            color = NoryptColors.Text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Set your App PIN",
            color = NoryptColors.Muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) pin = it },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            label = { Text("PIN (minimum 6 digits)") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NoryptColors.Accent,
                unfocusedBorderColor = NoryptColors.Border,
                focusedTextColor = NoryptColors.Text,
                unfocusedTextColor = NoryptColors.Text,
                focusedLabelColor = NoryptColors.Accent,
                unfocusedLabelColor = NoryptColors.Muted,
                cursorColor = NoryptColors.Accent,
            ),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) confirm = it },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            label = { Text("Confirm PIN") },
            isError = confirm.isNotEmpty() && !matches,
            supportingText = {
                if (confirm.isNotEmpty() && !matches) {
                    Text("PINs do not match", color = NoryptColors.Red, fontSize = 12.sp)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NoryptColors.Accent,
                unfocusedBorderColor = NoryptColors.Border,
                focusedTextColor = NoryptColors.Text,
                unfocusedTextColor = NoryptColors.Text,
                focusedLabelColor = NoryptColors.Accent,
                unfocusedLabelColor = NoryptColors.Muted,
                cursorColor = NoryptColors.Accent,
                errorBorderColor = NoryptColors.Red,
            ),
        )
        Spacer(Modifier.height(20.dp))

        Text(
            "There is no recovery path. A forgotten PIN means you must factory-reset the phone.",
            color = NoryptColors.MutedDeep,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        Button(
            enabled = canContinue,
            onClick = { onPinSet(pin) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NoryptColors.Accent,
                contentColor = Color.White,
                disabledContainerColor = NoryptColors.AccentDim,
                disabledContentColor = NoryptColors.Muted,
            ),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text("Continue", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
