package com.norypt.protect.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.security.AppPin
import com.norypt.protect.ui.theme.NoryptColors

/** Switch colours shared by every toggle in the app. */
@Composable
fun noryptSwitchColors(accent: Color = NoryptColors.Accent): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = accent,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = NoryptColors.Muted,
    uncheckedTrackColor = NoryptColors.Surface1,
    uncheckedBorderColor = NoryptColors.BorderStrong,
    disabledCheckedThumbColor = NoryptColors.Bg,
    disabledCheckedTrackColor = accent.copy(alpha = 0.45f),
    disabledUncheckedThumbColor = NoryptColors.MutedDeep,
    disabledUncheckedTrackColor = NoryptColors.Surface1,
    disabledUncheckedBorderColor = NoryptColors.Border,
)

/**
 * A settings row: title, explanation, switch. The card grows an accent bar while the
 * setting is on. [extra] renders below the text (per-feature options such as a sensitivity
 * picker) and slides in and out with the setting.
 */
@Composable
fun ToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    requiresDeviceOwner: Boolean = false,
    accent: Color = NoryptColors.Accent,
    extra: (@Composable () -> Unit)? = null,
) {
    NoryptCard(
        accent = if (checked && enabled) accent else null,
        contentPadding = PaddingValues(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        color = if (enabled) NoryptColors.Text else NoryptColors.Muted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (requiresDeviceOwner) {
                        Spacer(Modifier.width(8.dp))
                        // For Device-Owner-only cards, `enabled` mirrors the tier.
                        TagPill("DEVICE OWNER", if (enabled) NoryptColors.MutedDeep else NoryptColors.Amber)
                    }
                }
                Text(
                    subtitle,
                    color = NoryptColors.Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                enabled = enabled,
                colors = noryptSwitchColors(accent),
            )
        }
        AnimatedVisibility(
            visible = extra != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                extra?.invoke()
            }
        }
    }
}

/**
 * A [ToggleCard] whose changes need the App PIN: an optional warning dialog and then the
 * PIN to turn on, the PIN alone to turn off. [apply] receives the wanted state after a
 * correct PIN and returns whether the platform accepted it; [onChanged] runs afterwards so
 * the caller re-reads the real state rather than trusting the switch.
 */
@Composable
fun PinGuardedToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    apply: (Boolean) -> Boolean,
    onChanged: () -> Unit,
    requiresDeviceOwner: Boolean = false,
    warningTitle: String? = null,
    warningText: String? = null,
) {
    val ctx = LocalContext.current
    var pendingValue by remember { mutableStateOf<Boolean?>(null) }
    var showWarning by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }

    ToggleCard(
        title = title,
        subtitle = subtitle,
        checked = checked,
        enabled = enabled,
        requiresDeviceOwner = requiresDeviceOwner,
        onToggle = { wanted ->
            pendingValue = wanted
            if (wanted && warningText != null) showWarning = true else showPin = true
        },
    )

    if (showWarning) {
        AlertDialog(
            onDismissRequest = {
                showWarning = false
                pendingValue = null
            },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = NoryptColors.Amber) },
            title = { Text(warningTitle ?: title, color = NoryptColors.TextStrong) },
            text = { Text(warningText.orEmpty(), color = NoryptColors.Muted, fontSize = 13.sp, lineHeight = 19.sp) },
            confirmButton = {
                TextButton(onClick = {
                    showWarning = false
                    showPin = true
                }) { Text("Continue", color = NoryptColors.Accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showWarning = false
                    pendingValue = null
                }) { Text("Cancel", color = NoryptColors.Muted) }
            },
            containerColor = NoryptColors.Surface2,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        )
    }

    if (showPin) {
        PinEntryDialog(
            title = if (pendingValue == true) "Enter App PIN to enable" else "Enter App PIN to disable",
            onConfirm = { pin ->
                if (AppPin.verify(ctx, pin)) {
                    showPin = false
                    pendingValue?.let { apply(it) }
                    pendingValue = null
                    onChanged()
                }
            },
            onDismiss = {
                showPin = false
                pendingValue = null
            },
        )
    }
}
