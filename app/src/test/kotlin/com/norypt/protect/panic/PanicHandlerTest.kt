package com.norypt.protect.panic

import com.norypt.protect.prefs.KvStore
import com.norypt.protect.prefs.ProtectPrefsKeys
import com.norypt.protect.wipe.WipeOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
}
