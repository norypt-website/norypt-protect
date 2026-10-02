package com.norypt.protect.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditRulesTest {

    private val self = "com.norypt.protect"

    private fun facts(
        pkg: String = "org.example",
        system: Boolean = false,
        keyboard: Boolean = false,
        installer: String? = "com.android.vending",
        inLauncher: Boolean = true,
        sensitive: Set<String> = emptySet(),
    ) = AppFacts(
        packageName = pkg, label = pkg, system = system, deviceAdmin = false, accessibility = false,
        keyboard = keyboard, notificationAccess = false, alwaysOnVpn = false, installer = installer,
        inLauncher = inLauncher, sensitive = sensitive,
    )

    @Test
    fun `a store app with no powerful access is not listed`() {
        assertTrue(AuditRules.reasons(facts()).isEmpty())
    }

    @Test
    fun `an app from outside an app store is listed with how it came`() {
        val unknown = AuditRules.reasons(facts(installer = null))
        val file = AuditRules.reasons(facts(installer = "com.android.packageinstaller"))
        val other = AuditRules.reasons(facts(installer = "org.other.updater"))
        assertEquals(listOf("Not installed from an app store (ADB or an unknown installer)"), unknown)
        assertEquals(listOf("Installed from an APK file, not an app store"), file)
        assertEquals(listOf("Installed by org.other.updater, not a known app store"), other)
    }

    @Test
    fun `system apps are not judged by their installer`() {
        assertTrue(AuditRules.reasons(facts(system = true, installer = null)).isEmpty())
    }

    @Test
    fun `a keyboard is listed, and a listed app without an icon says so`() {
        val reasons = AuditRules.reasons(facts(keyboard = true, inLauncher = false))
        assertEquals(listOf("Keyboard: sees everything you type", "No icon in the app drawer"), reasons)
    }

    @Test
    fun `held sensitive permissions are named in plain words, once each`() {
        val held = setOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS")
        assertEquals(listOf("Holds: camera, microphone, SMS"), AuditRules.reasons(facts(sensitive = held)))
    }

    @Test
    fun `system apps are hidden unless asked, Norypt is never listed, most reasons come first`() {
        val list = listOf(
            facts(pkg = "a", installer = null),
            facts(pkg = "b", installer = null, keyboard = true),
            facts(pkg = "sys", system = true, keyboard = true),
            facts(pkg = self, installer = null),
        )
        assertEquals(listOf("b", "a"), AuditRules.entries(list, showSystem = false, selfPackage = self).map { it.packageName })
        assertEquals(listOf("b", "a", "sys"), AuditRules.entries(list, showSystem = true, selfPackage = self).map { it.packageName })
    }

    @Test
    fun `notification access is read as package names, and an empty setting means none`() {
        assertEquals(setOf("org.a", "org.b"), AppAudit.listenerPackages("org.a/.Listener:org.b/org.b.Service"))
        assertTrue(AppAudit.listenerPackages("").isEmpty())
    }
}
