package com.norypt.protect.util

import org.junit.Assert.assertEquals
import org.junit.Test

class GrapheneDetectTest {

    @Test
    fun `adb command for production package`() {
        val cmd = GrapheneDetect.adbCommand("com.norypt.protect")
        assertEquals(
            "adb shell dpm set-active-admin --user 0 com.norypt.protect/com.norypt.protect.admin.ProtectAdminReceiver",
            cmd,
        )
    }

    @Test
    fun `adb command for debug package`() {
        val cmd = GrapheneDetect.adbCommand("com.norypt.protect.debug")
        assertEquals(
            "adb shell dpm set-active-admin --user 0 com.norypt.protect.debug/com.norypt.protect.admin.ProtectAdminReceiver",
            cmd,
        )
    }
}
