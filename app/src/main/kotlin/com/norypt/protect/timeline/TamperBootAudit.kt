package com.norypt.protect.timeline

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.norypt.protect.prefs.ProtectPrefs

/**
 * Records boots, and boots that happened while nothing was watching.
 *
 * Android 14+ does not deliver ACTION_SHUTDOWN to user apps, so a shutdown cannot be observed.
 * Two things can: the boot itself (its time comes from the monotonic clock, so it is exact
 * even when the entry is written hours later), and `Settings.Global.BOOT_COUNT`, which the
 * platform increments on every boot and which any app may read. If the count moved by more
 * than one since this app last ran, the phone was started at least once without ever being
 * unlocked afterwards — BOOT_COMPLETED is only delivered after the first unlock — which is
 * the signature of someone starting a phone that is not theirs.
 */
object TamperBootAudit {

    /** Boots between the last observed count and this one. 0 when nothing was recorded before. */
    internal fun unobservedBoots(previousCount: Int, currentCount: Int): Int =
        if (previousCount <= 0 || currentCount <= previousCount) 0 else currentCount - previousCount - 1

    internal fun bootDetail(bootAtMs: Long, nowMs: Long, lastAliveMs: Long, fromBootBroadcast: Boolean): String {
        val sb = StringBuilder("Booted ${TimelineFormat.dateTime(bootAtMs)}")
        sb.append(
            if (fromBootBroadcast) "; first unlocked ${TimelineFormat.time(nowMs)}"
            else "; monitoring resumed ${TimelineFormat.time(nowMs)}",
        )
        if (lastAliveMs > 0L && lastAliveMs < bootAtMs) {
            sb.append(
                ". Previous session last seen ${TimelineFormat.dateTime(lastAliveMs)}, " +
                    "${TimelineFormat.duration(bootAtMs - lastAliveMs)} before the boot",
            )
        }
        sb.append('.')
        return sb.toString()
    }

    internal fun unobservedDetail(count: Int, previousCount: Int, currentCount: Int): String =
        "$count boot(s) happened while monitoring was not running (boot count $previousCount → $currentCount). " +
            "If you did not restart the phone yourself, someone else started it."

    fun bootCount(ctx: Context): Int? = runCatching {
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrNull()

    /**
     * Logs the current boot once. Safe to call from both the boot broadcast and service start:
     * the stored boot count makes it idempotent per boot, and the service-start path covers a
     * phone whose boot broadcast never reached the app because it had been force-stopped.
     */
    fun check(ctx: Context, fromBootBroadcast: Boolean) {
        if (!TamperLog.isEnabled(ctx)) return
        val current = bootCount(ctx) ?: return
        val previous = ProtectPrefs.timelineLastBootCount(ctx)
        if (previous == current) return
        ProtectPrefs.setTimelineLastBootCount(ctx, current)

        val now = System.currentTimeMillis()
        val bootAt = now - SystemClock.elapsedRealtime()
        val lastAlive = ProtectPrefs.timelineLastAliveMs(ctx)
        val unobserved = unobservedBoots(previous, current)
        if (unobserved > 0) {
            TamperLog.record(ctx, TamperKind.UNOBSERVED_BOOTS, unobservedDetail(unobserved, previous, current))
        }
        TamperLog.record(
            ctx,
            TamperKind.BOOT,
            bootDetail(bootAt, now, lastAlive, fromBootBroadcast),
            if (unobserved > 0) Severity.Alert else Severity.Notable,
        )
    }
}
