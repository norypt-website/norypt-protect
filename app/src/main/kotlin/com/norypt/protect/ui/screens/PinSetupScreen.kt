package com.norypt.protect.ui.screens

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.R
import com.norypt.protect.ui.components.NoteCard
import com.norypt.protect.ui.components.PrimaryButton
import com.norypt.protect.ui.components.noryptFieldColors
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
        Spacer(Modifier.height(20.dp))
        Text(
            "Norypt Protect",
            color = NoryptColors.TextStrong,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
        )
        Spacer(Modifier.height(4.dp))
        Text("Set your App PIN", color = NoryptColors.Muted, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) pin = it },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            label = { Text("PIN (minimum 6 digits)") },
            modifier = Modifier.fillMaxWidth(),
            colors = noryptFieldColors(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) confirm = it },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
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
            colors = noryptFieldColors(),
        )
        Spacer(Modifier.height(20.dp))

        NoteCard(
            text = "There is no recovery path. A forgotten PIN means you must factory-reset the phone.",
            color = NoryptColors.Amber,
        )
        Spacer(Modifier.height(24.dp))

        PrimaryButton(label = "Continue", onClick = { onPinSet(pin) }, enabled = canContinue)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your PIN never leaves this device.",
            color = NoryptColors.MutedDeep,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
