package com.norypt.protect.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.ui.theme.NoryptColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WipeOptionsScreen(padding: PaddingValues) {
    val ctx = LocalContext.current

    var external by remember { mutableStateOf(ProtectPrefs.wipeExternalStorage(ctx)) }
    var euicc by remember { mutableStateOf(ProtectPrefs.wipeEuicc(ctx)) }
    var dryRun by remember { mutableStateOf(ProtectPrefs.dryRun(ctx)) }
    var dryRunVisible by remember { mutableStateOf(dryRun) }

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
        Text(
            "WIPE OPTIONS",
            color = NoryptColors.MutedDeep,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = { dryRunVisible = !dryRunVisible },
            ),
        )

        ToggleRow(
            title = "Erase internal storage",
            subtitle = "Always on — cannot be disabled.",
            checked = true,
            enabled = false,
            onCheckedChange = {},
        )
        ToggleRow(
            title = "Erase external storage (SD card)",
            subtitle = "Adds WIPE_EXTERNAL_STORAGE flag.",
            checked = external,
            onCheckedChange = {
                external = it
                ProtectPrefs.setWipeExternalStorage(ctx, it)
            },
        )
        ToggleRow(
            title = "Erase eSIM profiles",
            subtitle = "Adds WIPE_EUICC flag (Android 11+).",
            checked = euicc,
            onCheckedChange = {
                euicc = it
                ProtectPrefs.setWipeEuicc(ctx, it)
            },
        )

        if (dryRunVisible) {
            Spacer(Modifier.height(12.dp))
            Text(
                "DEVELOPER",
                color = NoryptColors.Red,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            ToggleRow(
                title = "Dry-run (broadcast only)",
                subtitle = "When ON, every panic broadcasts WIPED_DRYRUN instead of wiping. For QA only.",
                checked = dryRun,
                onCheckedChange = {
                    dryRun = it
                    ProtectPrefs.setDryRun(ctx, it)
                },
                accent = NoryptColors.Red,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    accent: androidx.compose.ui.graphics.Color = NoryptColors.Accent,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NoryptColors.Surface2)
            .border(1.dp, NoryptColors.Border, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = NoryptColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = NoryptColors.Muted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NoryptColors.Bg,
                checkedTrackColor = accent,
                uncheckedThumbColor = NoryptColors.Muted,
                uncheckedTrackColor = NoryptColors.Surface1,
                uncheckedBorderColor = NoryptColors.Border,
                disabledCheckedThumbColor = NoryptColors.Bg,
                disabledCheckedTrackColor = accent.copy(alpha = 0.5f),
            ),
        )
    }
}
