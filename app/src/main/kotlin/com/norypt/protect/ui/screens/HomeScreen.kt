package com.norypt.protect.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.R
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.admin.Provisioning
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.security.AppPin
import com.norypt.protect.ui.components.LongPressHoldButton
import com.norypt.protect.ui.components.PinEntryDialog
import com.norypt.protect.ui.components.StatusCard
import com.norypt.protect.ui.components.StatusLevel
import com.norypt.protect.ui.theme.NoryptColors

@Composable
fun HomeScreen(padding: PaddingValues, onRequestEnableAdmin: () -> Unit) {
    val ctx = LocalContext.current
    var tier by remember { mutableStateOf(Provisioning.current(ctx)) }
    var showPinForWipe by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { tier = Provisioning.current(ctx) }

    Column(
        Modifier
            .fillMaxSize()
            .background(NoryptColors.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.norypt_logo),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Norypt Protect",
                    color = NoryptColors.Text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Local-only security — no logs, no server",
                    color = NoryptColors.Muted,
                    fontSize = 12.sp,
                )
            }
            TierBadge(tier)
        }

        StatusCard(
            level = when (tier) {
                Tier.None -> StatusLevel.Disabled
                Tier.DeviceAdmin -> StatusLevel.Partial
                Tier.DeviceOwner -> StatusLevel.Armed
            },
            title = when (tier) {
                Tier.None -> "Disabled"
                Tier.DeviceAdmin -> "Armed — Device Admin tier"
                Tier.DeviceOwner -> "Fully armed — Device Owner"
            },
            subtitle = when (tier) {
                Tier.None -> "Grant device admin to arm Lock + Wipe."
                Tier.DeviceAdmin -> "Upgrade to Device Owner via ADB for the full feature set."
                Tier.DeviceOwner -> "All protections active."
            },
        )

        if (tier == Tier.None) {
            EnableAdminScreen(onRequestEnableAdmin = onRequestEnableAdmin)
        } else {
            SectionLabel("Quick actions")
            PrimaryButton(label = "Lock now", onClick = { lockNow(ctx) })
            LongPressHoldButton(
                label = "Hold 3s to WIPE",
                onComplete = { PanicHandler.panic(ctx, reason = "home.longpress") },
            )
            OutlinedButton(
                onClick = { showPinForWipe = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NoryptColors.Accent),
                border = androidx.compose.foundation.BorderStroke(1.dp, NoryptColors.Border),
            ) {
                Text("Wipe with App PIN instead")
            }
        }

        Spacer(Modifier.height(4.dp))
    }

    if (showPinForWipe) {
        PinEntryDialog(
            title = "Enter App PIN to wipe",
            onConfirm = { pin ->
                if (AppPin.verify(ctx, pin)) {
                    showPinForWipe = false
                    PanicHandler.panic(ctx, reason = "home.pin")
                }
            },
            onDismiss = { showPinForWipe = false },
        )
    }
}

@Composable
private fun TierBadge(tier: Tier) {
    val (text, fg, bg) = when (tier) {
        Tier.None -> Triple("NOT ENROLLED", NoryptColors.Red, NoryptColors.Red.copy(alpha = 0.12f))
        Tier.DeviceAdmin -> Triple("DEVICE ADMIN", NoryptColors.Amber, NoryptColors.Amber.copy(alpha = 0.12f))
        Tier.DeviceOwner -> Triple("DEVICE OWNER", NoryptColors.Green, NoryptColors.Green.copy(alpha = 0.14f))
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = NoryptColors.MutedDeep,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
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
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun lockNow(ctx: Context) {
    val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admin = ComponentName(ctx, ProtectAdminReceiver::class.java)
    if (dpm.isAdminActive(admin)) dpm.lockNow()
}
