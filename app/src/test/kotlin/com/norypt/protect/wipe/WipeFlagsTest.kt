package com.norypt.protect.wipe

import android.app.admin.DevicePolicyManager.WIPE_EUICC
import android.app.admin.DevicePolicyManager.WIPE_EXTERNAL_STORAGE
import android.app.admin.DevicePolicyManager.WIPE_SILENTLY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WIPE_SILENTLY is always set — it suppresses the confirmation dialog that
 * newer Android otherwise requires, which would otherwise block the factory
 * reset if no user is around to tap OK. All expected flag bitmasks here OR in
 * WIPE_SILENTLY as a base.
 */
class WipeFlagsTest {

    @Test
    fun `WIPE_SILENTLY is always on`() {
        val opts = listOf(
            WipeOptions(),
            WipeOptions(wipeExternalStorage = false, wipeEuicc = false),
            WipeOptions(wipeExternalStorage = true, wipeEuicc = false),
            WipeOptions(wipeExternalStorage = false, wipeEuicc = true),
        )
        opts.forEach { o ->
            assertTrue(
                "WIPE_SILENTLY missing for options=$o",
                (WipeFlags.build(o) and WIPE_SILENTLY) == WIPE_SILENTLY,
            )
        }
    }

    @Test
    fun `default options produce silently plus external plus euicc`() {
        val flags = WipeFlags.build(WipeOptions())
        assertEquals(WIPE_SILENTLY or WIPE_EXTERNAL_STORAGE or WIPE_EUICC, flags)
    }

    @Test
    fun `all options off still includes silently`() {
        val flags = WipeFlags.build(WipeOptions(wipeExternalStorage = false, wipeEuicc = false))
        assertEquals(WIPE_SILENTLY, flags)
    }

    @Test
    fun `only external storage`() {
        val flags = WipeFlags.build(WipeOptions(wipeExternalStorage = true, wipeEuicc = false))
        assertEquals(WIPE_SILENTLY or WIPE_EXTERNAL_STORAGE, flags)
    }

    @Test
    fun `only euicc`() {
        val flags = WipeFlags.build(WipeOptions(wipeExternalStorage = false, wipeEuicc = true))
        assertEquals(WIPE_SILENTLY or WIPE_EUICC, flags)
    }
}
