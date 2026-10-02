package com.norypt.protect.timeline

import android.app.admin.DevicePolicyManager
import android.app.admin.SecurityLog
import android.content.ComponentName
import android.content.Context
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.prefs.ProtectPrefs

/**
 * Android's own security log in the Timeline: off unless the owner turns it on under the Timeline
 * switch, Device Owner only, and only what happens after that is imported.
 */
object SecurityLogFeature {

    private fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)
    private fun admin(ctx: Context) = ComponentName(ctx, ProtectAdminReceiver::class.java)
    private fun nowNanos() = System.currentTimeMillis() * SecurityLogImport.NANOS_PER_MS

    fun isOn(ctx: Context): Boolean = runCatching { dpm(ctx)?.isSecurityLoggingEnabled(admin(ctx)) == true }.getOrDefault(false)

    /** True when Android last refused the log because another, unaffiliated user exists. */
    fun unavailable(ctx: Context): Boolean = ProtectPrefs.securityLogUnavailable(ctx)

    fun enable(ctx: Context): Boolean {
        val dpm = dpm(ctx) ?: return false
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return false
        runCatching { dpm.setSecurityLoggingEnabled(admin(ctx), true) }
        ProtectPrefs.setSecurityLogUnavailable(ctx, false)
        // Nothing from before this moment: the logs from before the current boot are skipped too.
        ProtectPrefs.setSecurityLogWatermarkNanos(ctx, nowNanos())
        ProtectPrefs.setSecurityLogPreRebootBoot(ctx, TamperBootAudit.bootCount(ctx) ?: -1)
        return isOn(ctx)
    }

    fun disable(ctx: Context) {
        runCatching { dpm(ctx)?.setSecurityLoggingEnabled(admin(ctx), false) }
    }

    /**
     * Imports what is new. The logs from before the last restart are read first, once per boot, so
     * a batch from this boot can never move the watermark past them. [fromBoot] reads only those:
     * Android releases a regular batch only after announcing it. Never throws: receivers call it.
     */
    fun importNew(ctx: Context, fromBoot: Boolean = false) {
        synchronized(this) {
            runCatching {
                if (!TamperLog.isEnabled(ctx) || !isOn(ctx)) return
                importPreRebootOnce(ctx)
                if (!fromBoot) {
                    retrieve(ctx) { it.retrieveSecurityLogs(admin(ctx)) }?.let { record(ctx, SecurityLogImport.select(it)) }
                }
            }
        }
    }

    private fun importPreRebootOnce(ctx: Context) {
        val boot = TamperBootAudit.bootCount(ctx) ?: return
        if (ProtectPrefs.securityLogPreRebootBoot(ctx) == boot) return
        // Marked first: damaged data that breaks the parser must not be read again on every batch.
        ProtectPrefs.setSecurityLogPreRebootBoot(ctx, boot)
        val events = retrieve(ctx) { it.retrievePreRebootSecurityLogs(admin(ctx)) } ?: return
        record(ctx, SecurityLogImport.selectPreReboot(events, ProtectPrefs.securityLogWatermarkNanos(ctx), nowNanos()))
    }

    /** A batch copied out of the platform type, or null: nothing ready, not supported, or refused. */
    private fun retrieve(ctx: Context, fetch: (DevicePolicyManager) -> List<SecurityLog.SecurityEvent>?): List<SysEvent>? {
        val dpm = dpm(ctx) ?: return null
        val batch = try {
            fetch(dpm)
        } catch (e: SecurityException) {
            // Another, unaffiliated user exists; the Timeline switch says so.
            ProtectPrefs.setSecurityLogUnavailable(ctx, true)
            return null
        }
        if (unavailable(ctx)) ProtectPrefs.setSecurityLogUnavailable(ctx, false)
        // An entry too damaged to copy is skipped, not fatal.
        return batch?.mapNotNull { e -> runCatching { SysEvent(e.tag, e.timeNanos, e.data) }.getOrNull() }
    }

    private fun record(ctx: Context, events: List<SysEvent>) {
        if (events.isEmpty()) return
        SecurityLogImport.toEntries(events).forEach { TamperLog.recordAt(ctx, it.kind, it.detail, it.severity, it.epochMs) }
        val newest = events.maxOf { it.timeNanos }
        if (newest > ProtectPrefs.securityLogWatermarkNanos(ctx)) ProtectPrefs.setSecurityLogWatermarkNanos(ctx, newest)
    }
}
