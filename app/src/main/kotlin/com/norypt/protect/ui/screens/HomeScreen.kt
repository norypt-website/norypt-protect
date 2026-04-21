package com.norypt.protect.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.norypt.protect.R
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.admin.Provisioning
import com.norypt.protect.admin.Tier
import com.norypt.protect.security.AppPin
import com.norypt.protect.ui.components.LongPressHoldButton
import com.norypt.protect.ui.components.PinEntryDialog
import com.norypt.protect.ui.components.StatusCard
import com.norypt.protect.ui.components.StatusLevel
import com.norypt.protect.ui.theme.NoryptColors
import com.norypt.protect.wipe.WipeEngine

@Composable
fun HomeScreen(onRequestEnableAdmin: () -> Unit) {
    val ctx = LocalContext.current
    var tier by remember { mutableStateOf(Provisioning.current(ctx)) }
    var showPinForWipe by remember { mutableStateOf(false) }

    // Re-check tier on every recomposition triggered by resume
    LaunchedEffect(Unit) { tier = Provisioning.current(ctx) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_shield),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color.Unspecified,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Norypt Protect", color = NoryptColors.Text)
                Text(
                    when (tier) {
                        Tier.None -> "Not enrolled"
                        Tier.DeviceAdmin -> "Device Admin"
                        Tier.DeviceOwner -> "Device Owner"
                    },
                    color = NoryptColors.Muted,
                )
            }
        }

        StatusCard(
            level = when (tier) {
                Tier.None -> StatusLevel.Disabled
                Tier.DeviceAdmin -> StatusLevel.Partial
                Tier.DeviceOwner -> StatusLevel.Armed
            },
            title = when (tier) {
                Tier.None -> "Disabled"
                Tier.DeviceAdmin -> "Armed (Device Admin tier)"
                Tier.DeviceOwner -> "Fully armed"
            },
            subtitle = when (tier) {
                Tier.None -> "Tap Enable to grant device admin."
                Tier.DeviceAdmin -> "Lock + Wipe available. Upgrade to Device Owner via ADB for full feature set."
                Tier.DeviceOwner -> "All protections active."
            },
        )

        if (tier == Tier.None) {
            Button(onClick = onRequestEnableAdmin, modifier = Modifier.fillMaxWidth()) {
                Text("Enable device admin")
            }
        } else {
            Button(onClick = { lockNow(ctx) }, modifier = Modifier.fillMaxWidth()) {
                Text("Lock now")
            }
            LongPressHoldButton(
                label = "Hold 3s to WIPE",
                onComplete = {
                    WipeEngine.wipe(ctx, reason = "home.longpress")
                },
            )
            OutlinedButton(onClick = { showPinForWipe = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Wipe with PIN instead")
            }
        }
    }

    if (showPinForWipe) {
        PinEntryDialog(
            title = "Enter App PIN to wipe",
            onConfirm = { pin ->
                if (AppPin.verify(ctx, pin)) {
                    showPinForWipe = false
                    WipeEngine.wipe(ctx, reason = "home.pin")
                }
            },
            onDismiss = { showPinForWipe = false },
        )
    }
}

private fun lockNow(ctx: Context) {
    val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admin = ComponentName(ctx, ProtectAdminReceiver::class.java)
    if (dpm.isAdminActive(admin)) dpm.lockNow()
}
