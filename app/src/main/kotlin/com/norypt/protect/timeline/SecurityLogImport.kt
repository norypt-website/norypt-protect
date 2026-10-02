package com.norypt.protect.timeline

import android.app.admin.SecurityLog

/** A security-log event copied out of `SecurityLog.SecurityEvent`, which unit tests cannot build. */
data class SysEvent(val tag: Int, val timeNanos: Long, val data: Any?)

/** One Timeline entry made from the security log, dated when the system logged it. */
data class SysEntry(val epochMs: Long, val kind: TamperKind, val detail: String, val severity: Severity = kind.defaultSeverity)

/**
 * Which security-log events reach the Timeline and how they read. A fixed list keeps the ring
 * buffer readable: app starts, successful unlocks and Wi-Fi or Bluetooth connections are left out.
 * Pre-reboot payloads come from memory a power cycle can damage, so every field is read defensively.
 */
object SecurityLogImport {

    /** USB debugging activity closer together than this is one entry. */
    const val ADB_WINDOW_MS = 10 * 60_000L
    const val NANOS_PER_MS = 1_000_000L

    /** A pre-reboot time further ahead of now than this is damaged data. */
    private const val FUTURE_SLACK_NANOS = 24 * 60 * 60_000L * NANOS_PER_MS
    private const val MAX_TEXT = 80

    /** A regular batch: Android hands each one over once, so only repeats inside it are dropped. */
    fun select(events: List<SysEvent>): List<SysEvent> =
        events.distinctBy { Triple(it.tag, it.timeNanos, it.data?.let { d -> flatten(d) }) }.sortedBy { it.timeNanos }

    /**
     * Logs from before the last restart: only events newer than [watermarkNanos] (the newest already
     * imported, or the moment the owner turned the log on) and at most a day ahead of [nowNanos]. A
     * damaged time in the future would otherwise move the watermark past every event still to come.
     */
    fun selectPreReboot(events: List<SysEvent>, watermarkNanos: Long, nowNanos: Long): List<SysEvent> =
        select(events.filter { it.timeNanos > watermarkNanos && it.timeNanos <= nowNanos + FUTURE_SLACK_NANOS })

    /** Timeline entries for the imported tags, with USB debugging grouped per [ADB_WINDOW_MS]. */
    fun toEntries(events: List<SysEvent>): List<SysEntry> {
        val out = ArrayList<SysEntry>()
        val adb = ArrayList<SysEvent>()
        for (e in events) {
            if (e.tag != SecurityLog.TAG_ADB_SHELL_CMD && e.tag != SecurityLog.TAG_ADB_SHELL_INTERACTIVE) {
                single(e)?.let { out += it }
                continue
            }
            if (adb.isNotEmpty() && (e.timeNanos - adb.first().timeNanos) / NANOS_PER_MS > ADB_WINDOW_MS) {
                out += adbEntry(adb)
                adb.clear()
            }
            adb += e
        }
        if (adb.isNotEmpty()) out += adbEntry(adb)
        return out.sortedBy { it.epochMs }
    }

    private fun single(e: SysEvent): SysEntry? {
        val ms = e.timeNanos / NANOS_PER_MS
        return when (e.tag) {
            SecurityLog.TAG_KEYGUARD_DISMISS_AUTH_ATTEMPT -> unlockAttempt(ms, e.data)
            SecurityLog.TAG_OS_STARTUP -> startup(ms, e.data)
            SecurityLog.TAG_CERT_AUTHORITY_INSTALLED -> certAuthority(ms, e.data, added = true)
            SecurityLog.TAG_CERT_AUTHORITY_REMOVED -> certAuthority(ms, e.data, added = false)
            SecurityLog.TAG_MEDIA_MOUNT -> SysEntry(ms, TamperKind.SYS_STORAGE, "Mounted: ${text(e.data, 1) ?: "storage"}.")
            SecurityLog.TAG_MEDIA_UNMOUNT -> SysEntry(ms, TamperKind.SYS_STORAGE, "Unmounted: ${text(e.data, 1) ?: "storage"}.")
            SecurityLog.TAG_WIPE_FAILURE -> SysEntry(ms, TamperKind.SYS_WIPE_FAILURE, "A factory reset failed.")
            SecurityLog.TAG_LOGGING_STOPPED -> SysEntry(ms, TamperKind.SYS_LOG_STATUS, "Android stopped its security log.")
            SecurityLog.TAG_LOG_BUFFER_SIZE_CRITICAL ->
                SysEntry(ms, TamperKind.SYS_LOG_STATUS, "The system log was nearly full; older events may be lost.")
            SecurityLog.TAG_KEY_INTEGRITY_VIOLATION ->
                SysEntry(ms, TamperKind.SYS_INTEGRITY, "Key integrity check failed: ${text(e.data, 0) ?: "unknown key"}.")
            SecurityLog.TAG_CERT_VALIDATION_FAILURE ->
                SysEntry(ms, TamperKind.SYS_INTEGRITY, "Certificate validation failed: ${text(e.data, 0) ?: "no reason given"}.")
            else -> null
        }
    }

    private fun unlockAttempt(ms: Long, data: Any?): SysEntry? {
        // Payload: [0] 1 = success, [1] 1 = PIN, pattern or password. Successes and unreadable ones are skipped.
        if (number(data, 0) != 0) return null
        val method = if (number(data, 1) == 1) "PIN, pattern or password" else "fingerprint or face"
        return SysEntry(ms, TamperKind.SYS_UNLOCK_FAILED, "Failed unlock by $method.")
    }

    private fun startup(ms: Long, data: Any?): SysEntry {
        val state = text(data, 0) ?: "unknown"
        val verity = text(data, 1) ?: "unknown"
        val note = when (state) {
            "orange" -> " The bootloader is unlocked."
            "red" -> " Verification failed."
            else -> ""
        }
        val severity = if (note.isEmpty()) Severity.Info else Severity.Alert
        return SysEntry(ms, TamperKind.SYS_STARTUP, "Verified boot: $state, dm-verity: $verity.$note", severity)
    }

    private fun certAuthority(ms: Long, data: Any?, added: Boolean): SysEntry? {
        if (number(data, 0) != 1) return null // the install or removal itself failed
        val subject = text(data, 1) ?: "unknown subject"
        return if (added) {
            SysEntry(ms, TamperKind.SYS_CA_CERT, "Certificate authority added: $subject. Traffic can be intercepted.")
        } else {
            SysEntry(ms, TamperKind.SYS_CA_CERT, "Certificate authority removed: $subject.", Severity.Notable)
        }
    }

    private fun adbEntry(group: List<SysEvent>): SysEntry {
        val commands = group.filter { it.tag == SecurityLog.TAG_ADB_SHELL_CMD }
        val first = commands.firstOrNull()?.let { text(it.data, 0) } ?: "unreadable"
        val detail = when (commands.size) {
            0 -> "Interactive ADB shell opened."
            1 -> "1 command over USB debugging: $first"
            else -> "${commands.size} commands over USB debugging; first: $first"
        }
        return SysEntry(group.first().timeNanos / NANOS_PER_MS, TamperKind.SYS_DEBUG_SHELL, detail)
    }

    private fun field(data: Any?, index: Int): Any? = when (data) {
        is Array<*> -> data.getOrNull(index)
        else -> if (index == 0) data else null
    }

    private fun number(data: Any?, index: Int): Int? = (field(data, index) as? Number)?.toInt()

    private fun text(data: Any?, index: Int): String? =
        (field(data, index) as? String)?.takeIf { it.isNotBlank() }?.let { if (it.length <= MAX_TEXT) it else it.take(MAX_TEXT) + "…" }

    private fun flatten(data: Any): String = if (data is Array<*>) data.joinToString("\u001f") else data.toString()
}
