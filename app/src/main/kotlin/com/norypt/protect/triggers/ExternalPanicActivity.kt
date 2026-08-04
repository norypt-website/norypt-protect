package com.norypt.protect.triggers

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.security.AppPin
import com.norypt.protect.ui.components.PinEntryDialog
import com.norypt.protect.ui.theme.NoryptProtectTheme
import com.norypt.protect.util.DebugTelemetry

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
 * app the user paired. Pairing itself is confirmed with the App PIN, the same bar the app
 * already sets for a manual wipe — otherwise any installed app could pair itself and then
 * factory-reset the phone.
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
        /** Whether a trigger app is paired, for the Triggers UI to surface. */
        fun pairedTriggerPackage(context: Context): String? =
            ProtectPrefs.panicTriggerPackage(context)
    }

    private fun confirmPairing(packageName: String) {
        val label = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0),
            ).toString()
        }.getOrDefault(packageName)

        setContent {
            NoryptProtectTheme {
                var show by remember { mutableStateOf(true) }
                if (show) {
                    PinEntryDialog(
                        title = "Allow \"$label\" to wipe this device?",
                        onConfirm = { pin ->
                            show = false
                            if (AppPin.verify(this, pin)) {
                                ProtectPrefs.setPanicTriggerPackage(this, packageName)
                                DebugTelemetry.bump(this, "a5_paired")
                                setResult(Activity.RESULT_OK)
                            } else {
                                setResult(Activity.RESULT_CANCELED)
                            }
                            finish()
                        },
                        onDismiss = {
                            show = false
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        },
                    )
                }
            }
        }
    }
}

object ExternalPanicTrigger : Trigger {
    override val id = "A5"
    override val label = "External panic interop (PanicKit)"
    override val description =
        "Let one paired panic app (Ripple, Panic Button, or any PanicKit trigger) fire a wipe. " +
        "The app must connect to Norypt Protect first, and you confirm the pairing with your " +
        "App PIN — only that one app can trigger, and only while this is armed. " +
        "Requires Device Owner — the wipe call is denied for non-DO admins on Android 13+."
    override val requiredTier = Tier.DeviceOwner

    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, id, true)

    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, id, false)
}
