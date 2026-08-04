package com.norypt.protect.debug

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.admin.Provisioning
import com.norypt.protect.dpm.AntiTamper
import com.norypt.protect.dpm.EmergencySos
import com.norypt.protect.dpm.LauncherAlias
import com.norypt.protect.dpm.PowerMenuGuard
import com.norypt.protect.dpm.SafeBootLockdown
import com.norypt.protect.triggers.DeadmanMonitor
import com.norypt.protect.dpm.UsbLockdown
import com.norypt.protect.util.DebugTelemetry

/**
 * Debug-only hook for exercising the Device-Owner lockdown surface from adb and reading the
 * result back from the platform rather than from our own preferences.
 *
 * Reporting what the platform actually enforces is the point: several of these features
 * cache their own "on" flag, so a mismatch between our state and DevicePolicyManager is
 * exactly the failure this is meant to catch.
 */
class DpmTestReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val admin = ComponentName(ctx, ProtectAdminReceiver::class.java)
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)

        when (intent.getStringExtra("action")) {
            "usb_on" -> log("usb.enable -> ${UsbLockdown.enable(ctx)}")
            "usb_off" -> log("usb.disable -> ${UsbLockdown.disable(ctx)}")
            "safeboot_on" -> log("safeboot.enable -> ${SafeBootLockdown.enable(ctx)}")
            "safeboot_off" -> log("safeboot.disable -> ${SafeBootLockdown.disable(ctx)}")
            "antitamper_on" -> log("antitamper.apply -> ${AntiTamper.apply(ctx)}")
            "antitamper_off" -> log("antitamper.release -> ${AntiTamper.release(ctx)}")
            "launcher_hide" -> log("launcher.hide -> ${LauncherAlias.hide(ctx)}")
            "launcher_show" -> log("launcher.show -> ${LauncherAlias.show(ctx)}")
            "deadman_alert" -> {
                DeadmanMonitor.countdownActive = false
                log("deadman.clearAlert")
                DeadmanMonitor.clearAlert(ctx)
            }
            "powermenu_on" -> { PowerMenuGuard.enable(ctx); log("powermenu.enable done") }
            "powermenu_off" -> { PowerMenuGuard.disable(ctx); log("powermenu.disable done") }
            "sos_off" -> log("sos.disableIfPossible -> ${EmergencySos.disableIfPossible(ctx)}")
            "sos_on" -> log("sos.enableIfPossible -> ${EmergencySos.enableIfPossible(ctx)}")
        }

        // App-reported state
        log(
            "APP  tier=${Provisioning.current(ctx)} usb=${UsbLockdown.isOn(ctx)} " +
                "safeboot=${SafeBootLockdown.isOn(ctx)} antitamper=${AntiTamper.isApplied(ctx)} " +
                "powermenu=${PowerMenuGuard.isEnabled(ctx)} launcherHidden=${LauncherAlias.isHidden(ctx)} " +
                "sos=${EmergencySos.currentValue(ctx)}",
        )

        // What the platform actually enforces, read independently of our own flags.
        val um = ctx.getSystemService(UserManager::class.java)
        val restrictions = listOf(
            UserManager.DISALLOW_USB_FILE_TRANSFER,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_DEBUGGING_FEATURES,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_CONFIG_TETHERING,
            UserManager.DISALLOW_UNINSTALL_APPS,
        )
        val enforced = restrictions.filter { runCatching { um.hasUserRestriction(it) }.getOrDefault(false) }
        log("PLATFORM restrictions=$enforced")
        log("PLATFORM isDeviceOwner=${runCatching { dpm.isDeviceOwnerApp(ctx.packageName) }.getOrNull()} " +
            "lockTaskPkgs=${runCatching { dpm.getLockTaskPackages(admin).size }.getOrNull()}")
    }

    private fun log(m: String) = DebugTelemetry.log("DPMTEST $m")
}
