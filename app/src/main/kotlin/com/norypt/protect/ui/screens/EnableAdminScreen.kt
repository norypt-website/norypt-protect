package com.norypt.protect.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.ui.components.NoryptCard
import com.norypt.protect.ui.components.PrimaryButton
import com.norypt.protect.ui.components.SecondaryButton
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Activate Norypt Protect",
            color = NoryptColors.TextStrong,
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
            lineHeight = 18.sp,
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
    NoryptCard {
        Text(
            "ONE-TIME ADB ACTIVATION",
            color = NoryptColors.Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Run this once from a computer with adb installed and the phone connected over USB or Wi-Fi:",
            color = NoryptColors.Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
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
        SecondaryButton(label = "Copy command", onClick = onCopy)
    }
}

@Composable
private fun StepCard(index: Int, title: String, body: String) {
    NoryptCard(contentPadding = PaddingValues(14.dp)) {
        Row {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
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
                    lineHeight = 17.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun copyToClipboard(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
}
