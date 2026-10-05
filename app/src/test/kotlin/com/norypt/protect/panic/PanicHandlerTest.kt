package com.norypt.protect.panic

import com.norypt.protect.prefs.KvStore
import com.norypt.protect.prefs.ProtectPrefsKeys
import com.norypt.protect.wipe.WipeError
import com.norypt.protect.wipe.WipeOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Minimal in-memory KvStore for PanicHandler tests. */
private class FakeKvStore : KvStore {
    private val map = HashMap<String, Any?>()
    override fun getString(key: String, default: String?): String? =
        if (map.containsKey(key)) map[key] as String? else default
    override fun getInt(key: String, default: Int): Int =
        if (map.containsKey(key)) map[key] as Int else default
    override fun getBoolean(key: String, default: Boolean): Boolean =
        if (map.containsKey(key)) map[key] as Boolean else default
    override fun getLong(key: String, default: Long): Long =
        if (map.containsKey(key)) map[key] as Long else default
    override fun putString(key: String, value: String?) { map[key] = value }
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun putLong(key: String, value: Long) { map[key] = value }
}

private data class WipeCall(
    val reason: String,
    val options: WipeOptions,
    val dryRun: Boolean,
)

class PanicHandlerTest {

    private lateinit var store: FakeKvStore

    @Before
    fun setUp() {
        store = FakeKvStore()
    }

    private fun buildOptions(): WipeOptions = WipeOptions(
        wipeExternalStorage = ProtectPrefsKeys.wipeExternalStorage(store),
        wipeEuicc = ProtectPrefsKeys.wipeEuicc(store),
    )

    @Test
    fun `dry-run true routes wipeFn with dryRun=true`() {
        var capturedCall: WipeCall? = null
        val fakeFn: (String, WipeOptions, Boolean) -> Unit = { r, o, d ->
            capturedCall = WipeCall(r, o, d)
        }

        PanicHandler.panicWith("test_reason", dryRun = true, options = buildOptions(), wipeFn = fakeFn)

        assertNotNull(capturedCall)
        assertTrue("Expected dryRun=true", capturedCall!!.dryRun)
        assertEquals("test_reason", capturedCall!!.reason)
    }

    @Test
    fun `dry-run false routes wipeFn with dryRun=false`() {
        var capturedCall: WipeCall? = null
        val fakeFn: (String, WipeOptions, Boolean) -> Unit = { r, o, d ->
            capturedCall = WipeCall(r, o, d)
        }

        PanicHandler.panicWith("real_wipe", dryRun = false, options = buildOptions(), wipeFn = fakeFn)

        assertNotNull(capturedCall)
        assertFalse("Expected dryRun=false", capturedCall!!.dryRun)
        assertEquals("real_wipe", capturedCall!!.reason)
    }

    @Test
    fun `wipe options flags propagate correctly`() {
        ProtectPrefsKeys.setWipeExternalStorage(store, false)
        ProtectPrefsKeys.setWipeEuicc(store, false)

        var capturedCall: WipeCall? = null
        val fakeFn: (String, WipeOptions, Boolean) -> Unit = { r, o, d ->
            capturedCall = WipeCall(r, o, d)
        }

        PanicHandler.panicWith("opts_test", dryRun = true, options = buildOptions(), wipeFn = fakeFn)

        assertNotNull(capturedCall)
        assertFalse("Expected wipeExternalStorage=false", capturedCall!!.options.wipeExternalStorage)
        assertFalse("Expected wipeEuicc=false", capturedCall!!.options.wipeEuicc)

        // Flip flags on and verify again
        ProtectPrefsKeys.setWipeExternalStorage(store, true)
        ProtectPrefsKeys.setWipeEuicc(store, true)

        PanicHandler.panicWith("opts_test2", dryRun = false, options = buildOptions(), wipeFn = fakeFn)

        assertTrue("Expected wipeExternalStorage=true", capturedCall!!.options.wipeExternalStorage)
        assertTrue("Expected wipeEuicc=true", capturedCall!!.options.wipeEuicc)
    }

    // --- A wipe that did not happen must never be treated as one that did. ---

    @Test
    fun `a successful attempt leaves nothing pending and raises no alert`() {
        val outcome = PanicHandler.outcomeOf("deadman", error = null)

        assertNull(outcome.pendingReason)
        assertFalse(outcome.alertUser)
    }

    @Test
    fun `a denied wipe stays pending and alerts the user`() {
        val outcome = PanicHandler.outcomeOf("deadman", WipeError.SecurityDenied("not device owner"))

        assertEquals("deadman", outcome.pendingReason)
        assertTrue(outcome.alertUser)
    }

    @Test
    fun `every transient WipeError keeps the wipe outstanding`() {
        val errors = listOf(
            WipeError.SecurityDenied("denied"),
            WipeError.IllegalState("bad state"),
            WipeError.Unknown("DeadObjectException: null"),
            WipeError.ReturnedWithoutWiping,
        )

        errors.forEach { error ->
            val outcome = PanicHandler.outcomeOf("duress.threshold", error)
            assertEquals(
                "${error::class.simpleName} must stay pending",
                "duress.threshold",
                outcome.pendingReason,
            )
            assertTrue("${error::class.simpleName} must alert the user", outcome.alertUser)
        }
    }

    @Test
    fun `a wipe this phone can never perform is reported once, not retried for an hour`() {
        val outcome = PanicHandler.outcomeOf("home.pin", WipeError.NotPermitted)
        assertNull(outcome.pendingReason)
        assertTrue(outcome.alertUser)
    }

    @Test
    fun `a platform call that returns without wiping is a failure, not a success`() {
        // The pre-fix engine returned null here, which the contract defines as "wiped".
        val outcome = PanicHandler.outcomeOf("power.gesture", WipeError.ReturnedWithoutWiping)

        assertNotNull(outcome.pendingReason)
        assertTrue(outcome.alertUser)
    }

    // --- A denied wipe must not lie in wait indefinitely ---

    @Test
    fun `a freshly queued wipe is retried`() {
        val t0 = 1_000_000L
        assertTrue(PanicHandler.shouldRetry(t0, t0))
        assertTrue(PanicHandler.shouldRetry(t0, t0 + 60_000))
        assertTrue(PanicHandler.shouldRetry(t0, t0 + PanicHandler.RETRY_WINDOW_MS))
    }

    @Test
    fun `a stale queued wipe is abandoned rather than fired later`() {
        val t0 = 1_000_000L
        // The scenario: a wipe denied because the app was not yet Device Owner. Without a
        // bound it would fire the moment it became one, days after the actual trigger.
        assertFalse(PanicHandler.shouldRetry(t0, t0 + PanicHandler.RETRY_WINDOW_MS + 1))
        assertFalse(PanicHandler.shouldRetry(t0, t0 + 7L * 24 * 60 * 60 * 1000))
    }

    @Test
    fun `an unstamped entry is treated as fresh`() {
        assertTrue(PanicHandler.shouldRetry(0L, 1_000_000L))
    }

    @Test
    fun `a backwards clock step cannot resurrect an expired entry`() {
        val t0 = 1_000_000L
        assertFalse(PanicHandler.shouldRetry(t0, t0 - 60_000))
    }

    // --- A queued wipe keeps the dry-run setting it was triggered with ---

    private val t0 = 1_000_000L
    private val denied = WipeError.SecurityDenied("denied")

    @Test
    fun `nothing queued, nothing to retry`() {
        assertEquals(PanicHandler.Retry.None, PanicHandler.retryAction(store, t0))
    }

    @Test
    fun `a real wipe queued stays real when dry-run is turned on during the retry window`() {
        PanicHandler.recordOutcome(store, PanicHandler.outcomeOf("deadman", denied), dryRun = false, nowMs = t0)
        ProtectPrefsKeys.setDryRun(store, true)
        assertEquals(PanicHandler.Retry.Run("deadman", dryRun = false), PanicHandler.retryAction(store, t0 + 30_000))
    }

    @Test
    fun `a dry-run queued stays a dry-run when dry-run is turned off during the retry window`() {
        PanicHandler.recordOutcome(store, PanicHandler.outcomeOf("deadman", denied), dryRun = true, nowMs = t0)
        ProtectPrefsKeys.setDryRun(store, false)
        assertEquals(PanicHandler.Retry.Run("deadman", dryRun = true), PanicHandler.retryAction(store, t0 + 30_000))
    }

    @Test
    fun `an expired queued wipe is abandoned`() {
        PanicHandler.recordOutcome(store, PanicHandler.outcomeOf("deadman", denied), dryRun = false, nowMs = t0)
        assertEquals(PanicHandler.Retry.Abandon, PanicHandler.retryAction(store, t0 + PanicHandler.RETRY_WINDOW_MS + 1))
    }

    @Test
    fun `a failed retry keeps the original queue time`() {
        PanicHandler.recordOutcome(store, PanicHandler.outcomeOf("deadman", denied), dryRun = false, nowMs = t0)
        PanicHandler.recordOutcome(store, PanicHandler.outcomeOf("deadman", denied), dryRun = false, nowMs = t0 + 60_000)
        assertEquals(t0, ProtectPrefsKeys.pendingWipeAtMs(store))
    }

    // The WIPE FAILED alert says "retrying automatically"; once nothing is queued it must go.
    @Test
    fun `a successful attempt clears the queue and asks for the failure alert to be removed`() {
        PanicHandler.recordOutcome(store, PanicHandler.outcomeOf("deadman", denied), dryRun = true, nowMs = t0)
        val cleared = PanicHandler.recordOutcome(store, PanicHandler.outcomeOf("deadman", null), dryRun = true, nowMs = t0 + 30_000)
        assertTrue(cleared)
        assertNull(ProtectPrefsKeys.pendingWipeReason(store))
        assertEquals(0L, ProtectPrefsKeys.pendingWipeAtMs(store))
        assertFalse(ProtectPrefsKeys.pendingWipeDryRun(store))
        assertEquals(PanicHandler.Retry.None, PanicHandler.retryAction(store, t0 + 60_000))
    }

    @Test
    fun `a failed attempt does not remove the failure alert`() {
        assertFalse(PanicHandler.recordOutcome(store, PanicHandler.outcomeOf("deadman", denied), dryRun = false, nowMs = t0))
    }

    @Test
    fun `clearing the queue removes the reason, the time and the dry-run snapshot`() {
        PanicHandler.recordOutcome(store, PanicHandler.outcomeOf("deadman", denied), dryRun = true, nowMs = t0)
        PanicHandler.clearPending(store)
        assertNull(ProtectPrefsKeys.pendingWipeReason(store))
        assertEquals(0L, ProtectPrefsKeys.pendingWipeAtMs(store))
        assertFalse(ProtectPrefsKeys.pendingWipeDryRun(store))
    }

    // A test broadcast from a later trigger is not the wipe that is still owed.
    @Test
    fun `a later dry-run trigger does not settle a queued real wipe`() {
        PanicHandler.recordOutcome(store, PanicHandler.outcomeOf("deadman", denied), dryRun = false, nowMs = t0)
        val cleared = PanicHandler.recordOutcome(store, PanicHandler.outcomeOf("qs.tile", null), dryRun = true, nowMs = t0 + 10_000)
        assertFalse(cleared)
        assertEquals(PanicHandler.Retry.Run("deadman", dryRun = false), PanicHandler.retryAction(store, t0 + 30_000))
    }

    // Real wins: a queued real wipe is never downgraded or replaced by a later failed attempt.
    @Test
    fun `a failed dry-run attempt keeps a queued real wipe real, with its reason and time`() {
        PanicHandler.recordOutcome(store, PanicHandler.outcomeOf("deadman", denied), dryRun = false, nowMs = t0)
        val cleared = PanicHandler.recordOutcome(store, PanicHandler.outcomeOf("qs.tile", denied), dryRun = true, nowMs = t0 + 10_000)
        assertFalse(cleared)
        assertEquals("deadman", ProtectPrefsKeys.pendingWipeReason(store))
        assertEquals(t0, ProtectPrefsKeys.pendingWipeAtMs(store))
        assertFalse(ProtectPrefsKeys.pendingWipeDryRun(store))
        assertEquals(PanicHandler.Retry.Run("deadman", dryRun = false), PanicHandler.retryAction(store, t0 + 30_000))
    }

    @Test
    fun `a second failed real trigger keeps the first reason and time`() {
        PanicHandler.recordOutcome(store, PanicHandler.outcomeOf("deadman", denied), dryRun = false, nowMs = t0)
        PanicHandler.recordOutcome(store, PanicHandler.outcomeOf("usb", denied), dryRun = false, nowMs = t0 + 10_000)
        assertEquals("deadman", ProtectPrefsKeys.pendingWipeReason(store))
        assertEquals(t0, ProtectPrefsKeys.pendingWipeAtMs(store))
        assertFalse(ProtectPrefsKeys.pendingWipeDryRun(store))
    }

    @Test
    fun `a failed real trigger replaces a queued dry-run`() {
        PanicHandler.recordOutcome(store, PanicHandler.outcomeOf("qs.tile", denied), dryRun = true, nowMs = t0)
        PanicHandler.recordOutcome(store, PanicHandler.outcomeOf("deadman", denied), dryRun = false, nowMs = t0 + 10_000)
        assertEquals(PanicHandler.Retry.Run("deadman", dryRun = false), PanicHandler.retryAction(store, t0 + 30_000))
        assertEquals(t0 + 10_000, ProtectPrefsKeys.pendingWipeAtMs(store))
    }

    @Test
    fun `a wipe Android refuses clears the queue`() {
        PanicHandler.recordOutcome(store, PanicHandler.outcomeOf("deadman", denied), dryRun = false, nowMs = t0)
        val cleared = PanicHandler.recordOutcome(
            store, PanicHandler.outcomeOf("deadman", WipeError.NotPermitted), dryRun = false, nowMs = t0 + 30_000,
        )
        assertTrue(cleared)
        assertEquals(PanicHandler.Retry.None, PanicHandler.retryAction(store, t0 + 60_000))
    }

    @Test
    fun `the alert follows the queue`() {
        assertEquals(PanicHandler.WipeAlert.REFUSED, PanicHandler.alertAfter(WipeError.NotPermitted, cleared = true))
        assertEquals(PanicHandler.WipeAlert.NONE, PanicHandler.alertAfter(WipeError.NotPermitted, cleared = false))
        assertEquals(PanicHandler.WipeAlert.RETRYING, PanicHandler.alertAfter(denied, cleared = false))
        assertEquals(PanicHandler.WipeAlert.CANCEL, PanicHandler.alertAfter(null, cleared = true))
        assertEquals(PanicHandler.WipeAlert.NONE, PanicHandler.alertAfter(null, cleared = false))
    }
}
