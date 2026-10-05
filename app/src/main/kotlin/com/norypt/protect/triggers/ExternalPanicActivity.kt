package com.norypt.protect.triggers

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.ui.components.PinEntryDialog
import com.norypt.protect.ui.theme.NoryptColors
import com.norypt.protect.ui.theme.NoryptProtectTheme
import com.norypt.protect.util.DebugTelemetry
import com.norypt.protect.util.SignerDigest

/**
 * A5 — PanicKit responder.
 *
 * This is an Activity, not a BroadcastReceiver, because the protocol's entire safety model
 * rests on knowing who sent the intent. A receiver cannot: `onReceive` carries no caller
 * identity, so the only way to constrain it was a signature-level permission — which no
 * third-party panic app can ever hold, leaving the trigger listed in the UI and permanently
 * inert. `getCallingActivity()` gives us the sender, provided they used
 * `startActivityForResult`, which is what PanicKit's own trigger helper does.
 *
 * Consequently this Activity must **not** declare `singleTask` or `singleInstance`: both
 * clear the calling identity and would silently return us to the unidentifiable case.
 *
 * Firing requires all of: A5 armed, an identifiable caller, and that caller being the one
 * app the user paired. Pairing is only offered while A5 is armed, at most once a minute, shows
 * the caller's package and signing-certificate fingerprint, and needs an explicit confirmation
 * and then the App PIN, the same bar the app already sets for a manual wipe — otherwise any
 * installed app could pair itself and then factory-reset the phone.
 */
class ExternalPanicActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val caller = callingActivity?.packageName
        val decision = ExternalPanicPolicy.decide(
            action = intent?.action,
            callingPackage = caller,
            pairedPackage = ProtectPrefs.panicTriggerPackage(this),
            triggerEnabled = ProtectPrefs.isTriggerEnabled(this, ExternalPanicTrigger.id),
            selfPackage = packageName,
            signerMatches = caller != null &&
                SignerDigest.matches(packageManager, caller, ProtectPrefs.panicTriggerCert(this)),
            pairingCooldownOver = ExternalPanicPolicy.pairingCooldownOver(
                lastPairingOfferElapsedMs,
                SystemClock.elapsedRealtime(),
            ),
        )
        DebugTelemetry.log("A5 ${intent?.action} caller=$caller -> $decision")
        DebugTelemetry.bump(this, "a5_intents_total")

        when (decision) {
            is ExternalPanicPolicy.Decision.Fire -> {
                DebugTelemetry.bump(this, "a5_fired")
                setResult(Activity.RESULT_OK)
                PanicHandler.panic(this, "external.panic")
                finish()
            }

            is ExternalPanicPolicy.Decision.Unpair -> {
                ProtectPrefs.setPanicTriggerPackage(this, null)
                ProtectPrefs.setPanicTriggerCert(this, null)
                setResult(Activity.RESULT_OK)
                finish()
            }

            is ExternalPanicPolicy.Decision.Refuse -> {
                DebugTelemetry.bump(this, "a5_refused")
                // Deliberately gives the caller no detail: a stranger probing this surface
                // learns nothing about whether a pairing exists or who it is with.
                setResult(Activity.RESULT_CANCELED)
                finish()
            }

            is ExternalPanicPolicy.Decision.OfferPairing -> confirmPairing(decision.packageName)
        }
    }

    companion object {
        /**
         * When the last pairing prompt was shown (elapsedRealtime), for the one-a-minute limit.
         * In memory: the foreground service keeps the process alive, and a restart only allows
         * one more prompt, which is still behind A5 being armed, the confirmation and the App PIN.
         */
        @Volatile
        private var lastPairingOfferElapsedMs: Long? = null

        /** Whether a trigger app is paired, for the Triggers UI to surface. */
        fun pairedTriggerPackage(context: Context): String? =
            ProtectPrefs.panicTriggerPackage(context)

        /**
         * Why A5 cannot fire although armed, or null when it can: no app paired, the paired
         * app is gone or re-signed, or the pairing predates signing-key binding.
         */
        fun pairingProblem(context: Context): String? {
            val pkg = ProtectPrefs.panicTriggerPackage(context)
                ?: return "No panic app is paired yet. Connect one from the panic app."
            return if (SignerDigest.matches(context.packageManager, pkg, ProtectPrefs.panicTriggerCert(context))) {
                null
            } else {
                "The paired app ($pkg) is missing or signed differently. Pair it again."
            }
        }
    }

    /**
     * Two steps: the owner first sees who is asking — the package name and the SHA-256 of its
     * signing certificate, which nobody can choose for another app, unlike the label — and states
     * "I am pairing this app"; only then the App PIN. A prompt that appears unasked is refused
     * at the first step without the PIN ever being typed into it.
     */
    private fun confirmPairing(callerPackage: String) {
        // Recorded now, before the owner decides: what gets paired is the app that asked.
        val cert = SignerDigest.of(packageManager, callerPackage)
        if (cert == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        lastPairingOfferElapsedMs = SystemClock.elapsedRealtime()
        // Chosen by the caller, so shown only as a secondary hint under the package name.
        val label = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(callerPackage, 0),
            ).toString()
        }.getOrNull()?.takeIf { it != callerPackage }

        setContent {
            NoryptProtectTheme {
                var step by remember { mutableStateOf(PairingStep.IDENTIFY) }
                val cancel = {
                    step = PairingStep.DONE
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
                when (step) {
                    PairingStep.IDENTIFY -> PairingIdentityDialog(
                        callerPackage = callerPackage,
                        fingerprint = SignerDigest.formatFingerprint(cert),
                        label = label,
                        onConfirm = { step = PairingStep.PIN },
                        onCancel = cancel,
                    )
                    PairingStep.PIN -> PinEntryDialog(
                        title = "Confirm pairing with your App PIN",
                        onVerified = {
                            step = PairingStep.DONE
                            ProtectPrefs.setPanicTriggerPackage(this, callerPackage)
                            ProtectPrefs.setPanicTriggerCert(this, cert)
                            DebugTelemetry.bump(this, "a5_paired")
                            setResult(Activity.RESULT_OK)
                            finish()
                        },
                        onDismiss = cancel,
                    )
                    PairingStep.DONE -> Unit
                }
            }
        }
    }

    private enum class PairingStep { IDENTIFY, PIN, DONE }
}

/** The first pairing step: who is asking, and the owner's explicit "I am pairing this app". */
@Composable
private fun PairingIdentityDialog(
    callerPackage: String,
    fingerprint: String,
    label: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Pair a panic app?", color = NoryptColors.TextStrong, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This app asks to be able to wipe this phone:",
                    color = NoryptColors.Muted,
                    fontSize = 13.sp,
                )
                Text(callerPackage, color = NoryptColors.TextStrong, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                Text("Signing certificate SHA-256", color = NoryptColors.MutedDeep, fontSize = 11.sp)
                Text(fingerprint, color = NoryptColors.Text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp)
                if (label != null) {
                    Text("Calls itself \"$label\"", color = NoryptColors.MutedDeep, fontSize = 11.sp)
                }
                Text(
                    "Continue only if you started this pairing from your panic app just now and the " +
                        "package and fingerprint are the ones you expect.",
                    color = NoryptColors.Muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("I am pairing this app", color = NoryptColors.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel", color = NoryptColors.Muted) }
        },
        containerColor = NoryptColors.Surface2,
        shape = RoundedCornerShape(16.dp),
    )
}

object ExternalPanicTrigger : Trigger {
    override val id = "A5"
    override val label = "External panic interop (PanicKit)"
    override val description =
        "Let one paired panic app (Ripple, Panic Button, or any PanicKit trigger) fire a wipe. " +
        "Arm this first, then connect the app to Norypt Protect; you check its package and key " +
        "fingerprint and confirm the pairing with your App PIN — only that one app can trigger, and only while this is armed. " +
        "Requires Device Owner — the wipe call is denied for non-DO admins on Android 13+."
    override val requiredTier = Tier.DeviceOwner

    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, id, true)

    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, id, false)

    override fun problem(context: Context): String? = ExternalPanicActivity.pairingProblem(context)
}
