package com.norypt.protect.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.norypt.protect.admin.Provisioning
import com.norypt.protect.admin.Tier

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Provisioning.current(context) != Tier.None) {
            ProtectForegroundService.start(context)
        }
    }
}
