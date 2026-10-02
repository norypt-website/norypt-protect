package com.norypt.protect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.admin.Provisioning
import com.norypt.protect.ui.components.NoteCard
import com.norypt.protect.ui.components.PinGuardedToggleCard
import com.norypt.protect.ui.components.ScreenHeader
import com.norypt.protect.ui.components.SectionLabel
import com.norypt.protect.ui.components.ToggleCard
import com.norypt.protect.ui.theme.NoryptColors
import com.norypt.protect.wipe.WipeEngine

@Composable
fun WipeOptionsScreen(padding: PaddingValues) {
    val ctx = LocalContext.current

    var external by remember { mutableStateOf(ProtectPrefs.wipeExternalStorage(ctx)) }
    var euicc by remember { mutableStateOf(ProtectPrefs.wipeEuicc(ctx)) }
    var dryRun by remember { mutableStateOf(ProtectPrefs.dryRun(ctx)) }

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
        )

        // The single most important fact on this screen: whether a trigger would really erase
        // the phone. It used to be invisible unless you knew the long-press.
        if (dryRun) {
            NoteCard(
                title = "Dry-run is on",
                text = "Every trigger only simulates a wipe. Nothing is erased until dry-run is turned off.",
                color = NoryptColors.Amber,
            )
        } else if (!WipeEngine.canFactoryReset(Provisioning.current(ctx))) {
            NoteCard(
                title = "This phone cannot be wiped by the app",
                text = "On Android 14 and later only a Device Owner may factory-reset. Lock still works.",
                color = NoryptColors.Amber,
            )
        } else {
            NoteCard(
                title = "Dry-run is off",
                text = "Triggers erase this device for real. Internal storage is always included.",
                color = NoryptColors.Red,
            )
        }

        // Visible and PIN-guarded both ways: turning it on silently disarms every wipe, and
        // it used to be reachable only through a hidden long-press on the title.
        PinGuardedToggleCard(
            title = "Dry-run",
            subtitle = "When on, triggers only simulate a wipe. Use it to test triggers safely.",
            checked = dryRun,
            enabled = true,
            apply = {
                ProtectPrefs.setDryRun(ctx, it)
                true
            },
            onChanged = { dryRun = ProtectPrefs.dryRun(ctx) },
        )

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

        Spacer(Modifier.height(8.dp))
    }
}
