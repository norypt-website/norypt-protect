package com.norypt.protect.timeline

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.triggers.ClockBoot

/**
 * Records boots, and boots that happened while nothing was watching.
 *
 * A shutdown is announced only to receivers registered at runtime (C9 records it,
 * [com.norypt.protect.triggers.ForcedRestartMonitor]); this audit does not depend on it. Two things
 * are always seen: the boot itself (its time comes from the monotonic clock, so it is exact
 * even when the entry is written hours later), and `Settings.Global.BOOT_COUNT`, which the
 * platform increments on every boot and which any app may read. The entry is written at the
 * first unlock (the Timeline needs it), so if the count moved by more than one since then, the
 * phone was started at least once without ever being unlocked afterwards, which is the
 * signature of someone starting a phone that is not theirs. On a Device Owner install the
 * triggers still protected those boots before the first unlock; nobody unlocked them.
 */
object TamperBootAudit {

    /** Boots between the last observed count and this one. 0 when nothing was recorded before. */
    internal fun unobservedBoots(previousCount: Int, currentCount: Int): Int =
        if (previousCount <= 0 || currentCount <= previousCount) 0 else currentCount - previousCount - 1

    /**
     * The wall time protection started in boot [currentBoot] while the user was still locked, from
     * the stamp [markServiceStart] left; null when the service did not start before the first
     * unlock in this boot (not a Device Owner, or the app was not running then).
     */
    internal fun startedLockedAt(startBoot: Int, startElapsed: Long, startLocked: Boolean, currentBoot: Int, bootAtMs: Long): Long? =
        if (startLocked && startBoot == currentBoot && startElapsed > 0L) bootAtMs + startElapsed else null

    internal fun bootDetail(
        bootAtMs: Long,
        nowMs: Long,
        lastAliveMs: Long,
        fromBootBroadcast: Boolean,
        protectedFromMs: Long? = null,
        userspaceRestart: Boolean = false,
    ): String {
        // system_server or zygote restarted inside one kernel boot (1.3.6): the phone never powered off, so the kernel's
        // boot time is the same as before and only Android started again.
        val sb = StringBuilder(
            if (userspaceRestart) {
                "Android restarted itself (system crash, no power-off); on since ${TimelineFormat.dateTime(bootAtMs)}"
            } else {
                "Booted ${TimelineFormat.dateTime(bootAtMs)}"
            },
        )
        val unlocked = if (fromBootBroadcast) "first unlocked" else "unlocked by"
        sb.append(
            when {
                // Nobody unlocked: the credential-encrypted storage stayed unlocked through the restart, and the
                // lock screen came back. "Before the first unlock" and "unlocked by" would both be wrong.
                userspaceRestart && protectedFromMs != null ->
                    "; protection back on ${TimelineFormat.time(protectedFromMs)} with the phone locked"
                userspaceRestart -> "; monitoring resumed ${TimelineFormat.time(nowMs)}"
                protectedFromMs != null ->
                    "; protection on from ${TimelineFormat.time(protectedFromMs)}, before the first unlock; " +
                        "$unlocked ${TimelineFormat.time(nowMs)}"
                fromBootBroadcast -> "; first unlocked ${TimelineFormat.time(nowMs)}"
                else -> "; monitoring resumed ${TimelineFormat.time(nowMs)}"
            },
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
        "$count boot(s) went by without an unlock reaching Norypt Protect (boot count $previousCount → $currentCount). " +
            "If you did not restart the phone yourself, someone else started it."

    fun bootCount(ctx: Context): Int? = runCatching {
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrNull()

    /**
     * Stamps the first service start of this boot, and whether the user was still locked then.
     * Runs before the first unlock too: it touches only device-protected settings.
     */
    fun markServiceStart(ctx: Context, locked: Boolean) {
        val current = bootCount(ctx) ?: return
        if (ProtectPrefs.protectionStartBoot(ctx) == current) return
        ProtectPrefs.setProtectionStart(ctx, current, SystemClock.elapsedRealtime(), locked)
    }

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
        val protectedFrom = startedLockedAt(
            ProtectPrefs.protectionStartBoot(ctx),
            ProtectPrefs.protectionStartElapsed(ctx),
            ProtectPrefs.protectionStartLocked(ctx),
            current,
            bootAt,
        )
        val unobserved = unobservedBoots(previous, current)
        val userspace = previous > 0 && ClockBoot.userspaceRestartSince(ctx, previous, current)
        if (unobserved > 0) {
            TamperLog.record(ctx, TamperKind.UNOBSERVED_BOOTS, unobservedDetail(unobserved, previous, current))
        }
        TamperLog.record(
            ctx,
            TamperKind.BOOT,
            bootDetail(bootAt, now, lastAlive, fromBootBroadcast, protectedFrom, userspace),
            if (unobserved > 0) Severity.Alert else Severity.Notable,
        )
    }
}
