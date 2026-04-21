package com.norypt.protect.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

    LaunchedEffect(Unit) { tier = Provisioning.current(ctx) }

    Column(
        Modifier
            .fillMaxSize()
            .background(NoryptColors.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Brand header — logo + wordmark + tier badge
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

        // Status card
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

        // Actions
        if (tier == Tier.None) {
            Text(
                "To arm Norypt Protect, Android requires two one-time permissions. Step 2 won't show a button unless Step 1 is toggled on.",
                color = NoryptColors.Muted,
                fontSize = 13.sp,
            )
            StepCard(
                index = 1,
                title = "Allow restricted settings (Android 14+ only)",
                body = "Settings → Apps → Norypt Protect → ⋮ menu or bottom tray → tap Restricted settings → confirm.",
            )
            StepCard(
                index = 2,
                title = "Activate device admin",
                body = "Tap the button below. On the next screen, tap \"Activate this device admin app\".",
            )
            PrimaryButton(label = "Open device admin settings", onClick = onRequestEnableAdmin)
        } else {
            SectionLabel("Quick actions")
            PrimaryButton(label = "Lock now", onClick = { lockNow(ctx) })
            LongPressHoldButton(
                label = "Hold 3s to WIPE",
                onComplete = { WipeEngine.wipe(ctx, reason = "home.longpress") },
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
                    WipeEngine.wipe(ctx, reason = "home.pin")
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
        Column {
            Text(title, color = NoryptColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = NoryptColors.Muted, fontSize = 12.sp)
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
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun lockNow(ctx: Context) {
    val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admin = ComponentName(ctx, ProtectAdminReceiver::class.java)
    if (dpm.isAdminActive(admin)) dpm.lockNow()
}
