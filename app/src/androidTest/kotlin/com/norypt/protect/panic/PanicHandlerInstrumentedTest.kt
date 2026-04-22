package com.norypt.protect.panic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.wipe.WipeEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Verifies that PanicHandler.panic, with dry-run enabled, sends the
 * WipeEngine.ACTION_DRY_RUN local broadcast carrying the supplied reason.
 */
@RunWith(AndroidJUnit4::class)
class PanicHandlerInstrumentedTest {

    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun enableDryRun() {
        ProtectPrefs.setDryRun(ctx, true)
    }

    @After
    fun disableDryRun() {
        ProtectPrefs.setDryRun(ctx, false)
    }

    @Test
    fun dryRunPanicBroadcastsReason() {
        val latch = CountDownLatch(1)
        val captured = AtomicReference<String?>(null)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                captured.set(i.getStringExtra(WipeEngine.EXTRA_REASON))
                latch.countDown()
            }
        }
        ctx.registerReceiver(
            receiver,
            IntentFilter(WipeEngine.ACTION_DRY_RUN),
            Context.RECEIVER_NOT_EXPORTED,
        )
        try {
            PanicHandler.panic(ctx, reason = "instrumented.smoke")
            assertTrue(
                "Dry-run broadcast did not arrive within 2 seconds",
                latch.await(2, TimeUnit.SECONDS),
            )
            assertEquals("instrumented.smoke", captured.get())
        } finally {
            ctx.unregisterReceiver(receiver)
        }
    }
}
