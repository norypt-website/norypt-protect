package com.norypt.protect.debug

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.checkup.CheckId
import com.norypt.protect.checkup.CheckupReadings
import com.norypt.protect.checkup.CheckupRules
import com.norypt.protect.checkup.LocationWhileLocked
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.shield.AccessShield
import com.norypt.protect.shield.AppAudit
import com.norypt.protect.shield.AuditRules
import com.norypt.protect.shield.ShieldKind
import com.norypt.protect.timeline.SecurityLogFeature
import com.norypt.protect.util.DebugTelemetry

/**
 * Debug-only hooks for the 1.3 features. The app's screens are FLAG_SECURE, so adb UI automation
 * cannot see them; these call the same functions the screens call and log what the platform then
 * reports, for device checks driven from adb.
 */
object V13TestHooks {

    /** Returns false when [intent] carries none of these actions. */
    fun handle(ctx: Context, intent: Intent): Boolean {
        val action = intent.getStringExtra("action").orEmpty()
        return when {
            action.startsWith("shield_") || action == "audit_dump" -> shield(ctx, intent, action)
            action.startsWith("seclog_") -> securityLog(ctx, action)
            action.startsWith("checkup_") || action.startsWith("location_") -> checkup(ctx, intent, action)
            else -> false
        }
    }

    private fun shield(ctx: Context, intent: Intent, action: String): Boolean {
        val kind = runCatching { ShieldKind.valueOf(intent.getStringExtra("kind").orEmpty()) }.getOrNull()
        val pkgs = intent.getStringExtra("approve")?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()
        when (action) {
            "shield_on" -> kind?.let { log("shield.enable $it $pkgs -> ${AccessShield.enable(ctx, it, pkgs)}") }
            "shield_off" -> kind?.let { log("shield.disable $it -> ${AccessShield.disable(ctx, it)}") }
            "shield_unapprove" -> kind?.let {
                val pkg = intent.getStringExtra("pkg").orEmpty()
                log("shield.removeApproval $it $pkg -> ${AccessShield.removeApproval(ctx, it, pkg)}")
            }
            "audit_dump" -> auditDump(ctx, intent.getBooleanExtra("system", false))
            "shield_dump" -> Unit
            else -> return false
        }
        ShieldKind.entries.forEach { log(shieldState(ctx, it)) }
        return true
    }

    private fun shieldState(ctx: Context, kind: ShieldKind): String {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(ctx, ProtectAdminReceiver::class.java)
        val permitted = runCatching {
            if (kind == ShieldKind.KEYBOARD) dpm?.getPermittedInputMethods(admin) else dpm?.getPermittedAccessibilityServices(admin)
        }.getOrNull()
        return "SHIELD $kind on=${AccessShield.isOn(ctx, kind)} inForce=${AccessShield.inForce(ctx, kind)} " +
            "approved=${AccessShield.approved(ctx, kind)} platform=$permitted " +
            "enabledAll=${AccessShield.enabledAll(ctx, kind)} outside=${AccessShield.enabledOutside(ctx, kind)}"
    }

    private fun auditDump(ctx: Context, showSystem: Boolean) {
        // Hundreds of package queries: off the receiver's main thread, like the screen does.
        Thread {
            val entries = runCatching { AuditRules.entries(AppAudit.collect(ctx), showSystem, ctx.packageName) }
            entries.onFailure { log("AUDIT failed: $it") }
            entries.getOrNull()?.let { list ->
                val readable = AppAudit.notificationListeners(ctx) != null
                log("AUDIT ${list.size} entries (system=$showSystem, listenersReadable=$readable)")
                list.forEach { log("AUDIT ${it.label} [${it.packageName}] system=${it.system}: ${it.reasons.joinToString(" | ")}") }
            }
        }.start()
    }

    private fun securityLog(ctx: Context, action: String): Boolean {
        when (action) {
            "seclog_on" -> log("seclog.enable -> ${SecurityLogFeature.enable(ctx)}")
            "seclog_off" -> { SecurityLogFeature.disable(ctx); log("seclog.disable done") }
            "seclog_import" -> Thread { SecurityLogFeature.importNew(ctx); log("seclog.import done") }.start()
            "seclog_status" -> Unit
            else -> return false
        }
        log(
            "SECLOG on=${SecurityLogFeature.isOn(ctx)} unavailable=${SecurityLogFeature.unavailable(ctx)} " +
                "watermarkNs=${ProtectPrefs.securityLogWatermarkNanos(ctx)} preRebootBoot=${ProtectPrefs.securityLogPreRebootBoot(ctx)}",
        )
        return true
    }

    private fun checkup(ctx: Context, intent: Intent, action: String): Boolean {
        val id = runCatching { CheckId.valueOf(intent.getStringExtra("id").orEmpty()) }.getOrNull()
        when (action) {
            "checkup_fix" -> id?.let { log("checkup.fix $it -> ${CheckupReadings.fix(ctx, it)}") }
            "checkup_undo" -> id?.let { log("checkup.undo $it -> ${CheckupReadings.undo(ctx, it)}") }
            "checkup_confirm" -> id?.let { CheckupReadings.confirm(ctx, it); log("checkup.confirm $it done") }
            "location_lock" -> log("location.setEnabled -> ${LocationWhileLocked.setEnabled(ctx, intent.getBooleanExtra("on", false))}")
            // Raises the Home "Review app permissions" card that 1.2.1 shows after the auto-grant repair.
            "checkup_review_pending" -> { ProtectPrefs.setPermissionReviewPending(ctx, true); log("review card raised") }
            "checkup_dump", "location_dump" -> Unit
            else -> return false
        }
        val readings = CheckupReadings.read(ctx)
        log("CHECKUP readings $readings")
        CheckupRules.evaluate(readings).forEach { log("CHECKUP ${it.id} ${it.status}: ${it.detail}") }
        log("CHECKUP attention=${CheckupRules.needsAttention(CheckupRules.evaluate(readings))}")
        log(
            "LOCATION feature=${LocationWhileLocked.isEnabled(ctx)} turnedOffByUs=${ProtectPrefs.locationTurnedOffByUs(ctx)} " +
                "locationOn=${ctx.getSystemService(LocationManager::class.java)?.isLocationEnabled}",
        )
        return true
    }

    private fun log(m: String) = DebugTelemetry.log("DPMTEST $m")
}
