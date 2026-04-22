package com.norypt.protect.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs

/**
 * C5 — Fire panic on device shutdown attempt.
 *
 * Android does not let a third-party app block the power-off menu — the
 * system UI dialog that offers "Power off" is owned by SystemUI and cannot
 * be intercepted even by Device Owner apps. Instead this trigger turns
 * shutdown into a destructive trap: when the system fires
 * Intent.ACTION_SHUTDOWN we immediately call PanicHandler.panic, which
 * issues DevicePolicyManager.wipeData(). wipeData() is asynchronous, so
 * the wipe may not complete before the OS finishes shutting down — but
 * the wipe state is recorded and Android resumes the factory-reset on
 * next boot. Combined with Anti-tamper (DISALLOW_FACTORY_RESET +
 * setUninstallBlocked) an adversary cannot cancel the wipe from recovery.
 *
 * ACTION_SHUTDOWN is a protected system-only broadcast; the receiver is
 * declared in the manifest, not registered at runtime.
 */
class ShutdownReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SHUTDOWN) return
        if (!ProtectPrefs.isTriggerEnabled(context, "C5")) return
        PanicHandler.panic(context, "shutdown.attempt")
    }
}

object ShutdownTrigger : Trigger {
    override val id = "C5"
    override val label = "Wipe on shutdown attempt"
    override val description =
        "Android does not expose an API to block the power-off menu. When this trigger is " +
        "ON, pressing Power → Power off immediately starts a factory-reset. Combined with " +
        "Anti-tamper (which blocks factory-reset from recovery), an adversary who forces a " +
        "shutdown cannot recover the device. Firmware-level 30-second hold still works but " +
        "leaves the phone wiped on next boot."
    override val requiredTier = Tier.DeviceOwner

    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, id, true)
    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, id, false)
}
