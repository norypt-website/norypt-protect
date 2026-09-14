package com.norypt.protect.timeline

import android.content.Context
import android.os.SystemClock
import com.norypt.protect.prefs.ProtectPrefs

/**
 * The tamper timeline: an owner-controlled, local, encrypted record of events that show whether
 * a phone was handled while out of its owner's hands.
 *
 * Off by default. Every entry point is a no-op until the owner turns recording on from the
 * Timeline tab, so the app's "no logs" default holds for anyone who never opts in.
 *
 * Entries live in their own encrypted file, separate from the configuration every trigger
 * reads on each tick, so a full buffer never slows the rest of the app down.
 */
object TamperLog {

    private const val FILE = "norypt_tamper_log"

    /** How often the monitoring service stamps "still alive"; bounds the previous-session time on a boot entry. */
    private const val HEARTBEAT_INTERVAL_MS = 5 * 60_000L

    @Volatile private var cached: TamperLogStore? = null
    private var lastHeartbeatElapsedMs = 0L

    private fun store(ctx: Context): TamperLogStore {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: TamperLogStore(ProtectPrefs.openStore(ctx, FILE)).also { cached = it }
        }
    }

    fun isEnabled(ctx: Context): Boolean = ProtectPrefs.timelineEnabled(ctx)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        if (enabled == isEnabled(ctx)) return
        if (enabled) {
            ProtectPrefs.setTimelineEnabled(ctx, true)
            // Baselines first, so the state the phone is already in is not reported as change.
            TamperMonitor.reseed(ctx)
            record(ctx, TamperKind.TIMELINE, "Recording started.")
        } else {
            record(ctx, TamperKind.TIMELINE, "Recording stopped.")
            ProtectPrefs.setTimelineEnabled(ctx, false)
        }
    }

    fun record(ctx: Context, kind: TamperKind, detail: String = "", severity: Severity = kind.defaultSeverity) {
        if (!isEnabled(ctx)) return
        val event = TamperEvent(
            epochMs = System.currentTimeMillis(),
            elapsedMs = SystemClock.elapsedRealtime(),
            kind = kind,
            severity = severity,
            detail = detail,
        )
        synchronized(this) { store(ctx).append(event) }
        ProtectPrefs.setTimelineLastAliveMs(ctx, event.epochMs)
    }

    /** Newest first. */
    fun all(ctx: Context): List<TamperEvent> = synchronized(this) { store(ctx).all() }

    fun size(ctx: Context): Int = synchronized(this) { store(ctx).size() }

    /** Empties the buffer and leaves a single entry saying so, dated now. */
    fun clear(ctx: Context) {
        synchronized(this) { store(ctx).clear() }
        record(ctx, TamperKind.TIMELINE, "Timeline cleared.")
    }

    /** Throttled "still running" stamp from the service tick. */
    fun heartbeat(ctx: Context) {
        if (!isEnabled(ctx)) return
        val now = SystemClock.elapsedRealtime()
        if (lastHeartbeatElapsedMs != 0L && now - lastHeartbeatElapsedMs < HEARTBEAT_INTERVAL_MS) return
        lastHeartbeatElapsedMs = now
        ProtectPrefs.setTimelineLastAliveMs(ctx, System.currentTimeMillis())
    }
}
