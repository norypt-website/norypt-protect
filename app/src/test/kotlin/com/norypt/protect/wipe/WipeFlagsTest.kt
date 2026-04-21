package com.norypt.protect.wipe

import android.app.admin.DevicePolicyManager.WIPE_EUICC
import android.app.admin.DevicePolicyManager.WIPE_EXTERNAL_STORAGE
import org.junit.Assert.assertEquals
import org.junit.Test

class WipeFlagsTest {

    @Test
    fun `default options produce external plus euicc flags`() {
        val flags = WipeFlags.build(WipeOptions())
        assertEquals(WIPE_EXTERNAL_STORAGE or WIPE_EUICC, flags)
    }

    @Test
    fun `all options off produces zero`() {
        val flags = WipeFlags.build(WipeOptions(wipeExternalStorage = false, wipeEuicc = false))
        assertEquals(0, flags)
    }

    @Test
    fun `only external storage`() {
        val flags = WipeFlags.build(WipeOptions(wipeExternalStorage = true, wipeEuicc = false))
        assertEquals(WIPE_EXTERNAL_STORAGE, flags)
    }

    @Test
    fun `only euicc`() {
        val flags = WipeFlags.build(WipeOptions(wipeExternalStorage = false, wipeEuicc = true))
        assertEquals(WIPE_EUICC, flags)
    }
}
