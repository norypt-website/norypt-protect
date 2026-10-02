package com.norypt.protect.dpm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyGrantReleaseTest {

    private val self = "com.norypt.protect"

    @Test
    fun `a granted permission of another app is handed back to the owner`() {
        assertTrue(PolicyGrantRelease.shouldRelease(self, "org.example.camera", granted = true))
    }

    @Test
    fun `a permission the app does not hold is left alone`() {
        // Granting first would hand it access it does not have.
        assertFalse(PolicyGrantRelease.shouldRelease(self, "org.example.camera", granted = false))
    }

    @Test
    fun `Norypt's own fixed grants are not touched here`() {
        assertFalse(PolicyGrantRelease.shouldRelease(self, self, granted = true))
    }

    @Test
    fun `only a phone that was on auto-grant gets the release`() {
        assertTrue(PolicyGrantRelease.shouldRun(autoGrantWasOn = true, earlierBuildRan = false, alreadyDone = false))
        assertTrue(PolicyGrantRelease.shouldRun(autoGrantWasOn = false, earlierBuildRan = true, alreadyDone = false))
        assertFalse(PolicyGrantRelease.shouldRun(autoGrantWasOn = false, earlierBuildRan = false, alreadyDone = false))
        assertFalse(PolicyGrantRelease.shouldRun(autoGrantWasOn = true, earlierBuildRan = true, alreadyDone = true))
    }
}
