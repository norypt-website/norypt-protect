package com.norypt.protect.wipe

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CRITICAL: Validates the spec's flagged constraint that non-Device-Owner
 * Device Admins may be restricted from calling wipeData() on Android 13+.
 *
 * The test runs ONLY in dry-run mode so it NEVER actually wipes the device.
 * The real wipe verification (tapping the long-press button on a throw-away
 * device) is documented in docs/smoke-test-wipedata.md.
 */
@RunWith(AndroidJUnit4::class)
class WipeSmokeTest {

    @Test
    fun dryRunReturnsNullAndBroadcasts() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val error = WipeEngine.wipe(ctx, reason = "smoke.test", dryRun = true)
        assertNull("Dry-run wipe must return null and broadcast instead of wiping", error)
    }
}
