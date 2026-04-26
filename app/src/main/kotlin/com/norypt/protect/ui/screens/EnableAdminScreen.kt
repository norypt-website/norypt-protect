package com.norypt.protect.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.ui.theme.NoryptColors
import com.norypt.protect.util.GrapheneDetect

@Composable
fun EnableAdminScreen(onRequestEnableAdmin: () -> Unit) {
    val ctx = LocalContext.current
    val isGraphene = remember { GrapheneDetect.isGrapheneOS() }

    Column(
        // This composable is embedded inside HomeScreen's verticalScroll. Having
        // our own verticalScroll here would create nested scrollables with
        // infinite height constraints — Compose throws IllegalStateException at
        // measure time.
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Activate Norypt Protect",
            color = NoryptColors.Text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (isGraphene)
                "GrapheneOS detected — the stock activation flow is replaced by an ADB command (one-time)."
            else
                "Two one-time permissions on stock Android. Step 2's button is greyed out until Step 1 is granted.",
            color = NoryptColors.Muted,
            fontSize = 13.sp,
        )

        if (isGraphene) {
            val cmd = GrapheneDetect.adbCommand(ctx.packageName)
            GrapheneAdbCard(command = cmd, onCopy = { copyToClipboard(ctx, "norypt-adb", cmd) })
        } else {
            StepCard(
                index = 1,
                title = "Allow restricted settings (Android 14+)",
                body = "Settings → Apps → See all apps → Norypt Protect → ⋮ menu → tap Restricted settings → confirm.",
            )
            StepCard(
                index = 2,
                title = "Activate device admin",
                body = "Tap Open device admin settings below. On the next screen tap \"Activate this device admin app\".",
            )
            PrimaryButton(label = "Open device admin settings", onClick = onRequestEnableAdmin)
        }
    }
}

@Composable
private fun GrapheneAdbCard(command: String, onCopy: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NoryptColors.Surface2)
            .border(1.dp, NoryptColors.Border, RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        Text(
            "ONE-TIME ADB ACTIVATION",
            color = NoryptColors.Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Run this once from a computer with adb installed and the phone connected over USB or Wi-Fi:",
            color = NoryptColors.Muted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(NoryptColors.Bg)
                .padding(10.dp),
        ) {
            Text(
                command,
                color = NoryptColors.Text,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onCopy,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NoryptColors.Accent),
            border = androidx.compose.foundation.BorderStroke(1.dp, NoryptColors.Border),
            shape = RoundedCornerShape(8.dp),
        ) { Text("Copy command") }
    }
}

@Composable
private fun StepCard(index: Int, title: String, body: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NoryptColors.Surface2)
            .border(1.dp, NoryptColors.Border, RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NoryptColors.AccentDim),
            contentAlignment = Alignment.Center,
        ) {
            Text(index.toString(), color = NoryptColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = NoryptColors.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                color = NoryptColors.Muted,
                fontSize = 12.sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NoryptColors.Accent,
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(10.dp),
    ) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}

private fun copyToClipboard(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
}
