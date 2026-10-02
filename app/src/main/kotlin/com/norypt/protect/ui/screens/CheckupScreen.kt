package com.norypt.protect.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.norypt.protect.admin.Provisioning
import com.norypt.protect.admin.Tier
import com.norypt.protect.checkup.CheckResult
import com.norypt.protect.checkup.CheckStatus
import com.norypt.protect.checkup.CheckupReadings
import com.norypt.protect.checkup.CheckupRules
import com.norypt.protect.checkup.FixResult
import com.norypt.protect.checkup.LocationWhileLocked
import com.norypt.protect.ui.components.NoryptCard
import com.norypt.protect.ui.components.NoteCard
import com.norypt.protect.ui.components.SecondaryButton
import com.norypt.protect.ui.components.SectionLabel
import com.norypt.protect.ui.components.SubScreenScaffold
import com.norypt.protect.ui.components.TagPill
import com.norypt.protect.ui.components.ToggleCard
import com.norypt.protect.ui.theme.NoryptColors

/** Privacy checkup: each item with its status, and a fix or a Settings link where one exists. */
@Composable
fun CheckupSubScreen(onBack: () -> Unit, onOpenAudit: () -> Unit, padding: PaddingValues) {
    val ctx = LocalContext.current
    var readings by remember { mutableStateOf(CheckupReadings.read(ctx)) }
    var message by remember { mutableStateOf<String?>(null) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        readings = CheckupReadings.read(ctx)
        // Asked twice and refused, Android stops showing the prompt: App info is the only way left.
        val activity = ctx as? Activity
        if (!granted && activity?.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT) == false) {
            message = "Nearby devices is not allowed. Allow it under App info › Permissions."
            runCatching {
                val info = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                ctx.startActivity(info.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
    // Re-read on return from a Settings page: a status always comes from the platform.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) readings = CheckupReadings.read(ctx)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SubScreenScaffold(title = "Privacy checkup", onBack = onBack, padding = padding) {
        message?.let { NoteCard(text = it, color = NoryptColors.Red) }
        CheckupRules.evaluate(readings).forEach { result ->
            CheckItem(
                result = result,
                fixLabel = if (CheckupRules.offersFix(result.id, readings)) CheckupReadings.fixLabel(result.id, readings) else null,
                canUndo = CheckupReadings.canUndo(readings, result.id),
                onFix = {
                    val permission = CheckupReadings.permissionFor(result.id, readings)
                    if (permission != null) {
                        askPermission.launch(permission)
                    } else {
                        message = (CheckupReadings.fix(ctx, result.id) as? FixResult.Failed)?.reason
                        readings = CheckupReadings.read(ctx)
                    }
                },
                onUndo = {
                    message = (CheckupReadings.undo(ctx, result.id) as? FixResult.Failed)?.reason
                    readings = CheckupReadings.read(ctx)
                },
                onConfirm = {
                    CheckupReadings.confirm(ctx, result.id)
                    readings = CheckupReadings.read(ctx)
                },
            )
        }
        LocationWhileLockedCard()
        SectionLabel("Apps")
        SecondaryButton(label = "Open App audit", onClick = onOpenAudit)
    }
}

@Composable
private fun CheckItem(
    result: CheckResult,
    fixLabel: String?,
    canUndo: Boolean,
    onFix: () -> Unit,
    onUndo: () -> Unit,
    onConfirm: () -> Unit,
) {
    NoryptCard {
        TagPill(result.status.name, result.status.color())
        Text(result.id.title, color = NoryptColors.TextStrong, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(result.detail, color = NoryptColors.Muted, fontSize = 12.sp)
        when {
            canUndo -> SecondaryButton(label = "Undo", onClick = onUndo, color = NoryptColors.Muted)
            result.status == CheckStatus.CONFIRM -> {
                SecondaryButton(label = "Open Settings", onClick = onFix)
                SecondaryButton(label = "I've set this", onClick = onConfirm, color = NoryptColors.Muted)
            }
            result.status != CheckStatus.OK && fixLabel != null -> SecondaryButton(label = fixLabel, onClick = onFix)
        }
    }
}

private fun CheckStatus.color(): Color = when (this) {
    CheckStatus.OK -> NoryptColors.Green
    CheckStatus.ATTENTION -> NoryptColors.Red
    CheckStatus.INFO -> NoryptColors.MutedDeep
    CheckStatus.CONFIRM -> NoryptColors.Amber
}

@Composable
private fun LocationWhileLockedCard() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(LocationWhileLocked.isEnabled(ctx)) }
    val isOwner = Provisioning.current(ctx) == Tier.DeviceOwner
    SectionLabel("Location")
    ToggleCard(
        title = "Location off while locked",
        subtitle = if (isOwner) {
            "Location turns off when the screen goes off and back on when you unlock, if it was on. While the " +
                "screen is off nothing can use location: navigation and location sharing pause, and Find My " +
                "Device cannot locate the phone."
        } else {
            "Requires Device Owner."
        },
        checked = on,
        enabled = isOwner,
        requiresDeviceOwner = true,
        onToggle = { wanted ->
            LocationWhileLocked.setEnabled(ctx, wanted)
            on = LocationWhileLocked.isEnabled(ctx)
        },
    )
}
