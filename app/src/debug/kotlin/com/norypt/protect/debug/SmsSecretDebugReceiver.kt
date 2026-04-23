package com.norypt.protect.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs

class SmsSecretDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ProtectPrefs.isTriggerEnabled(context, "A6")) return
        val code = ProtectPrefs.smsSecretCode(context) ?: return
        if (code.isEmpty()) return
        val body = intent.getStringExtra("body") ?: return
        if (body.contains(code, ignoreCase = false)) {
            PanicHandler.panic(context, "sms.secret.debug")
        }
    }
}
