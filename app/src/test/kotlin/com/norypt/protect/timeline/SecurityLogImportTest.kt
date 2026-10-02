package com.norypt.protect.timeline

import android.app.admin.SecurityLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityLogImportTest {

    private val t0 = 1_800_000_000_000L * 1_000_000L // nanoseconds since the epoch
    private fun ns(minutes: Long) = t0 + minutes * 60_000L * 1_000_000L
    private fun event(tag: Int, minutes: Long, data: Any? = null) = SysEvent(tag, ns(minutes), data)

    @Test
    fun `a regular batch keeps one copy of a repeated event, oldest first`() {
        val a = event(SecurityLog.TAG_WIPE_FAILURE, 2)
        val b = event(SecurityLog.TAG_WIPE_FAILURE, 1)
        val c = event(SecurityLog.TAG_WIPE_FAILURE, 0)
        assertEquals(listOf(c, b, a), SecurityLogImport.select(listOf(a, c, b, a)))
    }

    @Test
    fun `logs from before a restart skip what was imported and damaged future times`() {
        val seen = event(SecurityLog.TAG_WIPE_FAILURE, 0)
        val missed = event(SecurityLog.TAG_WIPE_FAILURE, 1)
        val damaged = event(SecurityLog.TAG_WIPE_FAILURE, 3 * 24 * 60L)
        val picked = SecurityLogImport.selectPreReboot(listOf(damaged, missed, seen), watermarkNanos = ns(0), nowNanos = ns(2))
        assertEquals(listOf(missed), picked)
    }

    @Test
    fun `a failed unlock is imported with its method, a successful one is not`() {
        val failedPin = event(SecurityLog.TAG_KEYGUARD_DISMISS_AUTH_ATTEMPT, 1, arrayOf<Any>(0, 1))
        val failedFinger = event(SecurityLog.TAG_KEYGUARD_DISMISS_AUTH_ATTEMPT, 2, arrayOf<Any>(0, 0))
        val success = event(SecurityLog.TAG_KEYGUARD_DISMISS_AUTH_ATTEMPT, 3, arrayOf<Any>(1, 1))
        val entries = SecurityLogImport.toEntries(listOf(failedPin, failedFinger, success))
        assertEquals(listOf(TamperKind.SYS_UNLOCK_FAILED, TamperKind.SYS_UNLOCK_FAILED), entries.map { it.kind })
        assertTrue(entries[0].detail.contains("PIN"))
        assertTrue(entries[1].detail.contains("fingerprint"))
    }

    @Test
    fun `an unlocked bootloader at startup is an alert, a locked one is info`() {
        val orange = event(SecurityLog.TAG_OS_STARTUP, 1, arrayOf<Any>("orange", "enforcing"))
        val green = event(SecurityLog.TAG_OS_STARTUP, 2, arrayOf<Any>("green", "enforcing"))
        // GrapheneOS starts "yellow": its own verified-boot key, bootloader locked.
        val yellow = event(SecurityLog.TAG_OS_STARTUP, 3, arrayOf<Any>("yellow", "enforcing"))
        val severities = SecurityLogImport.toEntries(listOf(orange, green, yellow)).map { it.severity }
        assertEquals(listOf(Severity.Alert, Severity.Info, Severity.Info), severities)
    }

    @Test
    fun `ADB activity within ten minutes is one entry with a count and the first command`() {
        val commands = listOf(0L, 3L, 9L).map { event(SecurityLog.TAG_ADB_SHELL_CMD, it, "pm list packages $it") }
        val later = event(SecurityLog.TAG_ADB_SHELL_INTERACTIVE, 25)
        val entries = SecurityLogImport.toEntries(commands + later)
        assertEquals(2, entries.size)
        assertTrue(entries[0].detail.startsWith("3 commands"))
        assertTrue(entries[0].detail.contains("pm list packages 0"))
        assertEquals(TamperKind.SYS_DEBUG_SHELL, entries[1].kind)
    }

    @Test
    fun `a long command is shortened`() {
        val entries = SecurityLogImport.toEntries(listOf(event(SecurityLog.TAG_ADB_SHELL_CMD, 1, "x".repeat(500))))
        assertTrue(entries.single().detail.length < 200)
    }

    @Test
    fun `an added certificate authority is an alert with its subject, a failed install is not imported`() {
        val added = event(SecurityLog.TAG_CERT_AUTHORITY_INSTALLED, 1, arrayOf<Any>(1, "CN=Interceptor", 0))
        val failed = event(SecurityLog.TAG_CERT_AUTHORITY_INSTALLED, 2, arrayOf<Any>(0, "CN=Other", 0))
        val entries = SecurityLogImport.toEntries(listOf(added, failed))
        assertEquals(1, entries.size)
        assertEquals(Severity.Alert, entries[0].severity)
        assertTrue(entries[0].detail.contains("CN=Interceptor"))
    }

    @Test
    fun `unknown tags and malformed payloads never throw`() {
        val junk = listOf(
            event(123_456, 1, "x"),
            event(SecurityLog.TAG_KEYGUARD_DISMISS_AUTH_ATTEMPT, 2, "not an array"),
            event(SecurityLog.TAG_OS_STARTUP, 3, arrayOf<Any>(42)),
            event(SecurityLog.TAG_CERT_AUTHORITY_INSTALLED, 4, null),
            event(SecurityLog.TAG_MEDIA_MOUNT, 5, arrayOf<Any>("/mnt/media_rw/x", "")),
        )
        val entries = SecurityLogImport.toEntries(junk)
        assertEquals(listOf(TamperKind.SYS_STARTUP, TamperKind.SYS_STORAGE), entries.map { it.kind })
        assertTrue(entries[0].detail.contains("unknown"))
        assertEquals("Mounted: storage.", entries[1].detail)
    }
}
