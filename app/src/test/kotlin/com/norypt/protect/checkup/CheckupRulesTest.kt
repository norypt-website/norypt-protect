package com.norypt.protect.checkup

import android.app.admin.DevicePolicyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CheckupRulesTest {

    private val today = LocalDate.of(2026, 10, 2)
    private val hideContent = DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS

    private fun readings(
        deviceName: String? = "Pixel 9a",
        bluetoothName: String? = "Pixel 9a",
        bluetoothAllowed: Boolean = true,
        adb: Boolean? = false,
        complexity: Int? = DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH,
        patch: LocalDate? = today.minusDays(10),
        vpn: String? = null,
        vpnReadable: Boolean = true,
        lockdown: Boolean = false,
        dns: Int? = DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME,
        keyguard: Int = 0,
        privateNotifications: Boolean? = false,
        twoG: Boolean? = true,
        graphene: Boolean = false,
        confirmed: Set<CheckId> = emptySet(),
    ) = Readings(
        model = "Pixel 9a", deviceName = deviceName, bluetoothName = bluetoothName, bluetoothAllowed = bluetoothAllowed,
        adbEnabled = adb, passwordComplexity = complexity, securityPatch = patch, today = today, alwaysOnVpn = vpn,
        vpnReadable = vpnReadable, vpnLockdown = lockdown, privateDnsMode = dns, keyguardDisabledFeatures = keyguard,
        privateNotificationsAllowed = privateNotifications, twoGBlocked = twoG, grapheneOs = graphene, confirmed = confirmed,
    )

    private fun status(r: Readings, id: CheckId) = CheckupRules.evaluate(r).single { it.id == id }.status

    @Test
    fun `a personal device name needs attention, the model name is fine, unreadable is info`() {
        assertEquals(CheckStatus.ATTENTION, status(readings(deviceName = "Alex's phone"), CheckId.DEVICE_NAME))
        assertEquals(CheckStatus.OK, status(readings(), CheckId.DEVICE_NAME))
        assertEquals(CheckStatus.INFO, status(readings(deviceName = null), CheckId.DEVICE_NAME))
    }

    @Test
    fun `a bluetooth name that cannot be read is never reported as fine`() {
        assertEquals(CheckStatus.INFO, status(readings(bluetoothName = null), CheckId.BLUETOOTH_NAME))
        assertEquals(CheckStatus.INFO, status(readings(bluetoothAllowed = false, bluetoothName = null), CheckId.BLUETOOTH_NAME))
        assertEquals(CheckStatus.ATTENTION, status(readings(bluetoothName = "Alex's phone"), CheckId.BLUETOOTH_NAME))
    }

    @Test
    fun `usb debugging on needs attention`() {
        assertEquals(CheckStatus.ATTENTION, status(readings(adb = true), CheckId.USB_DEBUGGING))
        assertEquals(CheckStatus.OK, status(readings(adb = false), CheckId.USB_DEBUGGING))
    }

    @Test
    fun `a weak or missing screen lock needs attention, a medium one is info`() {
        val low = readings(complexity = DevicePolicyManager.PASSWORD_COMPLEXITY_LOW)
        val none = readings(complexity = DevicePolicyManager.PASSWORD_COMPLEXITY_NONE)
        val medium = readings(complexity = DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM)
        assertEquals(CheckStatus.ATTENTION, status(low, CheckId.SCREEN_LOCK))
        assertEquals(CheckStatus.ATTENTION, status(none, CheckId.SCREEN_LOCK))
        assertEquals(CheckStatus.INFO, status(medium, CheckId.SCREEN_LOCK))
        assertEquals(CheckStatus.OK, status(readings(), CheckId.SCREEN_LOCK))
    }

    @Test
    fun `a security patch older than sixty days needs attention`() {
        assertEquals(CheckStatus.ATTENTION, status(readings(patch = today.minusDays(61)), CheckId.SECURITY_PATCH))
        assertEquals(CheckStatus.OK, status(readings(patch = today.minusDays(60)), CheckId.SECURITY_PATCH))
        assertEquals(CheckStatus.INFO, status(readings(patch = null), CheckId.SECURITY_PATCH))
    }

    @Test
    fun `a vpn that lets traffic out without it needs attention, no vpn is only info`() {
        assertEquals(CheckStatus.ATTENTION, status(readings(vpn = "org.vpn", lockdown = false), CheckId.VPN))
        assertEquals(CheckStatus.OK, status(readings(vpn = "org.vpn", lockdown = true), CheckId.VPN))
        assertEquals(CheckStatus.INFO, status(readings(vpn = null), CheckId.VPN))
    }

    @Test
    fun `private dns off needs attention unless an always-on vpn carries lookups`() {
        val off = DevicePolicyManager.PRIVATE_DNS_MODE_OFF
        assertEquals(CheckStatus.ATTENTION, status(readings(dns = off), CheckId.PRIVATE_DNS))
        assertEquals(CheckStatus.INFO, status(readings(dns = off, vpn = "org.vpn"), CheckId.PRIVATE_DNS))
        assertEquals(CheckStatus.INFO, status(readings(dns = DevicePolicyManager.PRIVATE_DNS_MODE_OPPORTUNISTIC), CheckId.PRIVATE_DNS))
        assertEquals(CheckStatus.OK, status(readings(), CheckId.PRIVATE_DNS))
    }

    @Test
    fun `lock-screen content is fine when hidden by Norypt or by the owner`() {
        val id = CheckId.LOCKSCREEN_NOTIFICATIONS
        assertEquals(CheckStatus.OK, status(readings(keyguard = hideContent, privateNotifications = true), id))
        assertEquals(CheckStatus.OK, status(readings(privateNotifications = false), id))
        assertEquals(CheckStatus.ATTENTION, status(readings(privateNotifications = true), id))
        assertEquals(CheckStatus.INFO, status(readings(privateNotifications = null), id))
    }

    @Test
    fun `2G cannot be checked below Android 14 and is only info on GrapheneOS`() {
        assertEquals(CheckStatus.INFO, status(readings(twoG = null), CheckId.CELLULAR_2G))
        assertEquals(CheckStatus.ATTENTION, status(readings(twoG = false), CheckId.CELLULAR_2G))
        assertEquals(CheckStatus.INFO, status(readings(twoG = false, graphene = true), CheckId.CELLULAR_2G))
        assertEquals(CheckStatus.OK, status(readings(twoG = true), CheckId.CELLULAR_2G))
    }

    @Test
    fun `GrapheneOS items appear only on GrapheneOS and turn OK once confirmed`() {
        assertFalse(CheckupRules.evaluate(readings()).any { it.id == CheckId.GOS_AUTO_REBOOT })
        assertEquals(CheckStatus.CONFIRM, status(readings(graphene = true), CheckId.GOS_AUTO_REBOOT))
        val confirmed = readings(graphene = true, confirmed = setOf(CheckId.GOS_AUTO_REBOOT))
        assertEquals(CheckStatus.OK, status(confirmed, CheckId.GOS_AUTO_REBOOT))
        assertEquals(CheckStatus.OK, status(readings(graphene = true), CheckId.TRUST_AGENTS))
    }

    @Test
    fun `the home count covers attention and unconfirmed items`() {
        // USB debugging plus the four GrapheneOS confirmations.
        assertEquals(5, CheckupRules.needsAttention(CheckupRules.evaluate(readings(adb = true, graphene = true))))
        // All clear on stock Android takes Smart Lock blocked; everything else is fine or only informational.
        val allClear = readings(keyguard = DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS)
        assertEquals(0, CheckupRules.needsAttention(CheckupRules.evaluate(allClear)))
    }

    @Test
    fun `a vpn that cannot be read below Device Owner is never reported as missing`() {
        val result = CheckupRules.evaluate(readings(vpn = null, vpnReadable = false)).single { it.id == CheckId.VPN }
        assertEquals(CheckStatus.INFO, result.status)
        assertTrue(result.detail.startsWith("Could not be read"))
    }
}
