package com.norypt.protect.dpm

import android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
import android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
import android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyGrantReleaseTest {

    private val self = "com.norypt.protect"

    @Test
    fun `a permission another app got under the old auto-grant is handed back to the owner`() {
        assertTrue(PolicyGrantRelease.shouldRelease(self, "org.example.camera", PERMISSION_GRANT_STATE_GRANTED))
    }

    @Test
    fun `a permission denied by policy is handed back too`() {
        assertTrue(PolicyGrantRelease.shouldRelease(self, "org.example.camera", PERMISSION_GRANT_STATE_DENIED))
    }

    @Test
    fun `a permission the owner already controls is left alone`() {
        assertFalse(PolicyGrantRelease.shouldRelease(self, "org.example.camera", PERMISSION_GRANT_STATE_DEFAULT))
    }

    @Test
    fun `Norypt's own fixed grants are not touched here`() {
        assertFalse(PolicyGrantRelease.shouldRelease(self, self, PERMISSION_GRANT_STATE_GRANTED))
    }
}
