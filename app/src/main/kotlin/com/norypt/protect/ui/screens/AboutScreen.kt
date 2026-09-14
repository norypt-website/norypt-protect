package com.norypt.protect.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.BuildConfig
import com.norypt.protect.ui.components.NoryptCard
import com.norypt.protect.ui.components.SectionLabel
import com.norypt.protect.ui.theme.NoryptColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

@Composable
fun AboutScreen(padding: PaddingValues) {
    val ctx = LocalContext.current
    var apkSha by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        apkSha = withContext(Dispatchers.IO) { runCatching { computeApkSha256(ctx) }.getOrNull() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(NoryptColors.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NoryptCard {
            Text("Norypt Protect", color = NoryptColors.TextStrong, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Local-only Android security — lock, wipe, harden. No network, no telemetry. " +
                    "The optional tamper timeline is the only record kept, and it stays on this device.",
                color = NoryptColors.Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        InfoRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        InfoRow("App ID", ctx.packageName)
        InfoRow("License", "GPL-3.0-or-later")
        InfoRow("Source", "github.com/norypt-website/norypt-protect", clickable = true) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/norypt-website/norypt-protect"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        }

        SectionLabel("Integrity")
        NoryptCard(contentPadding = PaddingValues(14.dp)) {
            Text("APK SHA-256", color = NoryptColors.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                apkSha ?: "computing…",
                color = NoryptColors.Text,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }

        Text(
            "Norypt Protect ships via F-Droid with Reproducible Builds — F-Droid rebuilds from the " +
                "GitHub-tagged source and byte-compares against the Norypt-signed APK. The verified-build " +
                "badge appears on the F-Droid listing once the F-Droid pipeline is wired in v1.0.0.",
            color = NoryptColors.Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, clickable: Boolean = false, onClick: () -> Unit = {}) {
    NoryptCard(contentPadding = PaddingValues(14.dp), onClick = if (clickable) onClick else null) {
        Row {
            Text(label, color = NoryptColors.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(value, color = if (clickable) NoryptColors.Accent else NoryptColors.Text, fontSize = 13.sp)
        }
    }
}

private fun computeApkSha256(ctx: android.content.Context): String {
    val apkPath = ctx.applicationInfo.sourceDir
    val md = MessageDigest.getInstance("SHA-256")
    File(apkPath).inputStream().use { input ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}
