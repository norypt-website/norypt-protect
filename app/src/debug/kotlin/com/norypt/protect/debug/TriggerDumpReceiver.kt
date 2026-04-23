package com.norypt.protect.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.norypt.protect.dpm.AntiTamper
import com.norypt.protect.dpm.EmergencySos
import com.norypt.protect.dpm.LauncherAlias
import com.norypt.protect.dpm.PowerMenuGuard
import com.norypt.protect.dpm.SafeBootLockdown
import com.norypt.protect.dpm.UsbLockdown
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.triggers.TriggerRegistry

class TriggerDumpReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.getBooleanExtra("disarm_all", false)) {
            TriggerRegistry.all.forEach { it.disarm(ctx) }
        }
        val sp = ctx.getSharedPreferences("norypt_admin_debug", Context.MODE_PRIVATE)
        val ed = sp.edit()
        TriggerRegistry.all.forEach { t ->
            ed.putBoolean("dump_trigger_${t.id}", ProtectPrefs.isTriggerEnabled(ctx, t.id))
        }
        ed.putBoolean("dump_dry_run", ProtectPrefs.dryRun(ctx))
        ed.putBoolean("dump_anti_tamper_pref", ProtectPrefs.antiTamperEnabled(ctx))
        ed.putBoolean("dump_anti_tamper_applied", AntiTamper.isApplied(ctx))
        ed.putBoolean("dump_launcher_hidden", LauncherAlias.isHidden(ctx))
        ed.putBoolean("dump_usb_lockdown", UsbLockdown.isOn(ctx))
        ed.putBoolean("dump_safe_boot_block", SafeBootLockdown.isOn(ctx))
        ed.putBoolean("dump_power_menu_block", PowerMenuGuard.isEnabled(ctx))
        ed.putInt("dump_sos_value", EmergencySos.currentValue(ctx))
        ed.putBoolean("dump_sos_disabled_on_promotion", ProtectPrefs.sosDisabledOnPromotion(ctx))
        ed.apply()
    }
}
