package com.norypt.protect.wipe

import com.norypt.protect.admin.Tier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WipeCapabilityTest {

    @Test
    fun `a Device Owner can factory-reset on every supported version`() {
        assertTrue(WipeEngine.canFactoryReset(Tier.DeviceOwner, sdk = 33))
        assertTrue(WipeEngine.canFactoryReset(Tier.DeviceOwner, sdk = 37))
    }

    @Test
    fun `a Device Admin can factory-reset only before Android 14`() {
        assertTrue(WipeEngine.canFactoryReset(Tier.DeviceAdmin, sdk = 33))
        assertFalse(WipeEngine.canFactoryReset(Tier.DeviceAdmin, sdk = 34))
    }

    @Test
    fun `without any admin there is no wipe`() {
        assertFalse(WipeEngine.canFactoryReset(Tier.None, sdk = 33))
    }
}
