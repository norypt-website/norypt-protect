package com.norypt.protect.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs

class SmsSecretReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ProtectPrefs.isTriggerEnabled(context, "A6")) return
        val code = ProtectPrefs.smsSecretCode(context) ?: return
        if (code.isEmpty()) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val triggered = messages.any { msg ->
            msg.messageBody?.contains(code, ignoreCase = false) == true
        }
        if (triggered) {
            PanicHandler.panic(context, "sms.secret")
        }
    }
}

object SmsSecretTrigger : Trigger {
    override val id = "A6"
    override val label = "Secret SMS"
    override val description = "Wipe device when an SMS containing your secret code is received. " +
        "Requires Device Owner — the wipe call is denied for non-DO admins on Android 13+."
    override val requiredTier = Tier.DeviceOwner
    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A6", true)
    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A6", false)
}
