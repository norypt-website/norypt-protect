package com.norypt.protect.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.norypt.protect.service.ProtectForegroundService

class ProtectAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        ProtectForegroundService.start(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // v0.1.0: admin deactivated by user is allowed; aggressive response is Plan 3 (DO tier).
        super.onDisabled(context, intent)
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        // Wired in Plan 2 (B1 max-failed-attempts trigger).
        super.onPasswordFailed(context, intent)
    }
}
