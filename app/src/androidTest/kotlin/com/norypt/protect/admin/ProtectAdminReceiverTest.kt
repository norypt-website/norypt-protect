package com.norypt.protect.admin

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtectAdminReceiverTest {

    @Test
    fun receiverClassIsResolvable() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val component = ComponentName(ctx, ProtectAdminReceiver::class.java)
        val info = ctx.packageManager.getReceiverInfo(component, 0)
        assertNotNull(info)
    }

    @Test
    fun provisioningReturnsValidTier() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val tier = Provisioning.current(ctx)
        // Any of the three is valid depending on how the device is provisioned at test time.
        assert(tier == Tier.None || tier == Tier.DeviceAdmin || tier == Tier.DeviceOwner)
    }
}
