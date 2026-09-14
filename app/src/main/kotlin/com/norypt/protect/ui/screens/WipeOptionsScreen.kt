package com.norypt.protect.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.ui.components.NoteCard
import com.norypt.protect.ui.components.ScreenHeader
import com.norypt.protect.ui.components.SectionLabel
import com.norypt.protect.ui.components.ToggleCard
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
        ScreenHeader(
            title = "Wipe",
            subtitle = "What a wipe erases when a trigger fires.",
            // Long-press the title to reveal the dry-run switch; the status card below is always shown.
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = { dryRunVisible = !dryRunVisible },
            ),
        )

        // The single most important fact on this screen: whether a trigger would really erase
        // the phone. It used to be invisible unless you knew the long-press.
        if (dryRun) {
            NoteCard(
                title = "Dry-run is on",
                text = "Every trigger only simulates a wipe. Nothing is erased until dry-run is turned off.",
                color = NoryptColors.Amber,
            )
        } else {
            NoteCard(
                title = "Dry-run is off",
                text = "Triggers erase this device for real. Internal storage is always included.",
                color = NoryptColors.Red,
            )
        }

        SectionLabel("Scope")
        ToggleCard(
            title = "Erase internal storage",
            subtitle = "Always on — cannot be disabled.",
            checked = true,
            enabled = false,
            onToggle = {},
        )
        ToggleCard(
            title = "Erase external storage (SD card)",
            subtitle = "Adds WIPE_EXTERNAL_STORAGE flag.",
            checked = external,
            enabled = true,
            onToggle = {
                external = it
                ProtectPrefs.setWipeExternalStorage(ctx, it)
            },
        )
        ToggleCard(
            title = "Erase eSIM profiles",
            subtitle = "Adds WIPE_EUICC flag (Android 11+).",
            checked = euicc,
            enabled = true,
            onToggle = {
                euicc = it
                ProtectPrefs.setWipeEuicc(ctx, it)
            },
        )

        if (dryRunVisible) {
            Spacer(Modifier.height(6.dp))
            SectionLabel("Developer")
            ToggleCard(
                title = "Dry-run (broadcast only)",
                subtitle = "When ON, every panic broadcasts WIPED_DRYRUN instead of wiping. For QA only.",
                checked = dryRun,
                enabled = true,
                accent = NoryptColors.Red,
                onToggle = {
                    dryRun = it
                    ProtectPrefs.setDryRun(ctx, it)
                },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
