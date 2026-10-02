package com.norypt.protect.dpm

import android.app.admin.DevicePolicyManager.PERMISSION_POLICY_AUTO_DENY
import android.app.admin.DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT
import android.app.admin.DevicePolicyManager.PERMISSION_POLICY_PROMPT
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPolicyGuardTest {

    @Test
    fun `auto-grant left by an older version is reset on a Device Owner`() {
        assertTrue(PermissionPolicyGuard.needsReset(isDeviceOwner = true, policy = PERMISSION_POLICY_AUTO_GRANT))
    }

    @Test
    fun `prompt is already correct`() {
        assertFalse(PermissionPolicyGuard.needsReset(isDeviceOwner = true, policy = PERMISSION_POLICY_PROMPT))
    }

    @Test
    fun `auto-deny is stricter than prompt and is left alone`() {
        assertFalse(PermissionPolicyGuard.needsReset(isDeviceOwner = true, policy = PERMISSION_POLICY_AUTO_DENY))
    }

    @Test
    fun `without Device Owner there is no policy to change`() {
        assertFalse(PermissionPolicyGuard.needsReset(isDeviceOwner = false, policy = PERMISSION_POLICY_AUTO_GRANT))
    }
}
