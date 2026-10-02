package com.norypt.protect.timeline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityLogFeatureTest {

    @Test
    fun `the app records failed unlocks itself unless the system log delivers them`() {
        assertTrue(SecurityLogFeature.recordsOwnFailedUnlock(logOn = false, unavailable = false))
        assertFalse(SecurityLogFeature.recordsOwnFailedUnlock(logOn = true, unavailable = false))
        // Android pauses the log while another, unaffiliated user exists: the app's own entries must go on.
        assertTrue(SecurityLogFeature.recordsOwnFailedUnlock(logOn = true, unavailable = true))
    }
}
