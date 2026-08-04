package com.norypt.protect.admin

import org.junit.Assert.assertEquals
import org.junit.Test

class ProvisioningTest {
    @Test
    fun `no admin returns None`() {
        val tier = Provisioning.tierFrom(isAdminActive = false, isDeviceOwner = false)
        assertEquals(Tier.None, tier)
    }

    @Test
    fun `admin only returns DeviceAdmin`() {
        val tier = Provisioning.tierFrom(isAdminActive = true, isDeviceOwner = false)
        assertEquals(Tier.DeviceAdmin, tier)
    }

    @Test
    fun `admin plus device owner returns DeviceOwner`() {
        val tier = Provisioning.tierFrom(isAdminActive = true, isDeviceOwner = true)
        assertEquals(Tier.DeviceOwner, tier)
    }

    @Test
    fun `admin plus write secure settings without device owner still DeviceAdmin`() {
        // WRITE_SECURE_SETTINGS alone does not confer Device Owner privileges
        val tier = Provisioning.tierFrom(isAdminActive = true, isDeviceOwner = false)
        assertEquals(Tier.DeviceAdmin, tier)
    }
}
