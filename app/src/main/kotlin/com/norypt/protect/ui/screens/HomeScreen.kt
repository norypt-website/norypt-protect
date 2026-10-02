package com.norypt.protect.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.norypt.protect.R
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.admin.Provisioning
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.timeline.TamperLog
import com.norypt.protect.triggers.TriggerRegistry
import com.norypt.protect.ui.components.LongPressHoldButton
import com.norypt.protect.ui.components.NoryptCard
import com.norypt.protect.ui.components.NoteCard
import com.norypt.protect.ui.components.PinEntryDialog
import com.norypt.protect.ui.components.PrimaryButton
import com.norypt.protect.ui.components.SecondaryButton
import com.norypt.protect.ui.components.SectionLabel
import com.norypt.protect.ui.components.StatusCard
import com.norypt.protect.ui.components.StatusLevel
import com.norypt.protect.ui.components.TagPill
import com.norypt.protect.ui.theme.NoryptColors
import com.norypt.protect.wipe.WipeEngine

@Composable
fun HomeScreen(padding: PaddingValues, onRequestEnableAdmin: () -> Unit) {
    var showAudit by remember { mutableStateOf(false) }
    if (showAudit) {
        AppAuditSubScreen(onBack = { showAudit = false }, padding = padding)
    } else {
        HomeContent(padding = padding, onRequestEnableAdmin = onRequestEnableAdmin, onOpenAudit = { showAudit = true })
    }
}

@Composable
private fun HomeContent(padding: PaddingValues, onRequestEnableAdmin: () -> Unit, onOpenAudit: () -> Unit) {
    val ctx = LocalContext.current
    var tier by remember { mutableStateOf(Provisioning.current(ctx)) }
    var armedCount by remember { mutableIntStateOf(countArmed(ctx)) }
    var brokenCount by remember { mutableIntStateOf(countBroken(ctx)) }
    var reviewPending by remember { mutableStateOf(ProtectPrefs.permissionReviewPending(ctx)) }
    var dryRun by remember { mutableStateOf(ProtectPrefs.dryRun(ctx)) }
    var timelineOn by remember { mutableStateOf(TamperLog.isEnabled(ctx)) }
    var showPinForWipe by remember { mutableStateOf(false) }
    val canWipe = WipeEngine.canFactoryReset(tier)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                tier = Provisioning.current(ctx)
                armedCount = countArmed(ctx)
                brokenCount = countBroken(ctx)
                reviewPending = ProtectPrefs.permissionReviewPending(ctx)
                dryRun = ProtectPrefs.dryRun(ctx)
                timelineOn = TamperLog.isEnabled(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(NoryptColors.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeHeader(tier)

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
            subtitle = when {
                tier == Tier.None -> "Grant device admin to arm Lock + Wipe."
                dryRun -> "Dry-run is on: triggers only simulate a wipe. Turn it off in the Wipe tab."
                tier == Tier.DeviceAdmin && !canWipe ->
                    "Lock works. Wiping needs Device Owner on Android 14 and later."
                tier == Tier.DeviceAdmin -> "Upgrade to Device Owner via ADB for the full feature set."
                else -> "Every feature is available. Arm the triggers you need."
            },
        )

        if (tier == Tier.None) {
            EnableAdminScreen(onRequestEnableAdmin = onRequestEnableAdmin)
        } else {
            SummaryRow(armedCount = armedCount, total = TriggerRegistry.all.size, dryRun = dryRun, timelineOn = timelineOn)
            if (reviewPending) {
                PermissionReviewCard(
                    onOpenAudit = onOpenAudit,
                    onDone = {
                        ProtectPrefs.setPermissionReviewPending(ctx, false)
                        reviewPending = false
                    },
                )
            }
            if (brokenCount > 0) {
                NoteCard(
                    text = "$brokenCount armed trigger${if (brokenCount == 1) "" else "s"} cannot fire. " +
                        "Open Triggers to see why.",
                    color = NoryptColors.Red,
                )
            }
            SectionLabel("Quick actions")
            PrimaryButton(label = "Lock now", onClick = { lockNow(ctx) })
            // Only where a wipe can actually happen: on Android 14+ a plain Device Admin is
            // refused by the platform, and a button that cannot wipe must not look like one.
            if (canWipe) {
                LongPressHoldButton(
                    label = "Hold to wipe",
                    onComplete = { PanicHandler.panic(ctx, reason = "home.longpress") },
                )
                SecondaryButton(
                    label = "Wipe with App PIN instead",
                    onClick = { showPinForWipe = true },
                    color = NoryptColors.Muted,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
    }

    if (showPinForWipe) {
        PinEntryDialog(
            title = "Enter App PIN to wipe",
            onVerified = {
                showPinForWipe = false
                PanicHandler.panic(ctx, reason = "home.pin")
            },
            onDismiss = { showPinForWipe = false },
        )
    }
}

/**
 * Until the owner taps Done: earlier versions let apps receive permissions without asking, and
 * those grants were locked as set by the admin. 1.2.1 unlocked them; this is how the owner finds
 * out that there is something to undo.
 */
@Composable
private fun PermissionReviewCard(onOpenAudit: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    NoteCard(
        title = "Review app permissions",
        text = "Earlier versions let apps receive permissions without asking you, and kept them locked " +
            "as set by your admin. They are unlocked now. Open Permission manager and remove what an " +
            "app should not have, starting with camera, microphone and location.",
        color = NoryptColors.Amber,
    )
    SecondaryButton(
        label = "Open privacy settings",
        onClick = {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_PRIVACY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
    )
    SecondaryButton(label = "Open App audit", onClick = onOpenAudit)
    SecondaryButton(label = "Done, I have reviewed them", onClick = onDone, color = NoryptColors.Muted)
}

@Composable
private fun HomeHeader(tier: Tier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NoryptColors.Surface2)
                .border(1.dp, NoryptColors.Border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.norypt_logo),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Norypt Protect",
                color = NoryptColors.TextStrong,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Local-only security — no server, no telemetry",
                color = NoryptColors.Muted,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        when (tier) {
            Tier.None -> TagPill("NOT ENROLLED", NoryptColors.Red)
            Tier.DeviceAdmin -> TagPill("DEVICE ADMIN", NoryptColors.Amber)
            Tier.DeviceOwner -> TagPill("DEVICE OWNER", NoryptColors.Green)
        }
    }
}

/** Three at-a-glance numbers: what is armed, whether a wipe is real, whether the timeline records. */
@Composable
private fun SummaryRow(armedCount: Int, total: Int, dryRun: Boolean, timelineOn: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(
            modifier = Modifier.weight(1f),
            label = "Triggers",
            value = "$armedCount/$total",
            caption = "armed",
            color = if (armedCount > 0) NoryptColors.Accent else NoryptColors.Muted,
        )
        StatTile(
            modifier = Modifier.weight(1f),
            label = "Wipe",
            value = if (dryRun) "Dry-run" else "Live",
            caption = if (dryRun) "simulated" else "erases data",
            color = if (dryRun) NoryptColors.Amber else NoryptColors.Red,
        )
        StatTile(
            modifier = Modifier.weight(1f),
            label = "Timeline",
            value = if (timelineOn) "On" else "Off",
            caption = if (timelineOn) "recording" else "not recording",
            color = if (timelineOn) NoryptColors.Green else NoryptColors.Muted,
        )
    }
}

@Composable
private fun StatTile(modifier: Modifier, label: String, value: String, caption: String, color: androidx.compose.ui.graphics.Color) {
    NoryptCard(modifier = modifier, contentPadding = PaddingValues(12.dp)) {
        Text(label.uppercase(), color = NoryptColors.MutedDeep, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(caption, color = NoryptColors.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Triggers that are armed and able to fire; one with a [com.norypt.protect.triggers.Trigger.problem] does not count. */
private fun countArmed(ctx: Context): Int =
    TriggerRegistry.all.count { ProtectPrefs.isTriggerEnabled(ctx, it.id) && it.problem(ctx) == null }

private fun countBroken(ctx: Context): Int =
    TriggerRegistry.all.count { ProtectPrefs.isTriggerEnabled(ctx, it.id) && it.problem(ctx) != null }

private fun lockNow(ctx: Context) {
    val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admin = ComponentName(ctx, ProtectAdminReceiver::class.java)
    if (dpm.isAdminActive(admin)) dpm.lockNow()
}
