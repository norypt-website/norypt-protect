package com.norypt.protect.dpm

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for EmergencySos path-selection logic (no Android context required). */
class EmergencySosTest {

    private fun state(
        current: Int = 1,
        hasWriteSecure: Boolean = false,
        isOwner: Boolean = false,
        secureOk: Boolean = true,
        dpmOk: Boolean = true,
        fallbackOk: Boolean = true,
    ): EmergencySos.SosState = object : EmergencySos.SosState {
        override fun currentValue() = current
        override fun hasWriteSecureSettings() = hasWriteSecure
        override fun isDeviceOwner() = isOwner
        override fun applyViaSecureSettings(value: Int) = secureOk
        override fun applyViaDpm(value: Int) = dpmOk
        override fun openSettingsFallback() = fallbackOk
    }

    @Test fun `path1 taken when WRITE_SECURE_SETTINGS granted`() {
        val result = EmergencySos.selectPath(state(hasWriteSecure = true), 0)
        assertEquals(DisableResult.PATH_SECURE_SETTINGS, result)
    }

    @Test fun `path2 taken when Device Owner and no WRITE_SECURE_SETTINGS`() {
        val result = EmergencySos.selectPath(state(isOwner = true), 0)
        assertEquals(DisableResult.PATH_DPM, result)
    }

    @Test fun `path3 fallback UI when no special privilege`() {
        val result = EmergencySos.selectPath(state(), 0)
        assertEquals(DisableResult.PATH_FALLBACK_UI, result)
    }

    @Test fun `already disabled returns ALREADY_DISABLED`() {
        val result = EmergencySos.selectPath(state(current = 0), 0)
        assertEquals(DisableResult.ALREADY_DISABLED, result)
    }

    @Test fun `FAILED when no privilege and fallback returns false`() {
        val result = EmergencySos.selectPath(state(fallbackOk = false), 0)
        assertEquals(DisableResult.FAILED, result)
    }

    @Test fun `path1 preferred over path2 when both available`() {
        val result = EmergencySos.selectPath(state(hasWriteSecure = true, isOwner = true), 0)
        assertEquals(DisableResult.PATH_SECURE_SETTINGS, result)
    }

    @Test fun `falls through to path2 when path1 fails`() {
        val result = EmergencySos.selectPath(
            state(hasWriteSecure = true, isOwner = true, secureOk = false),
            0,
        )
        assertEquals(DisableResult.PATH_DPM, result)
    }

    @Test fun `re-enable uses same path logic with targetValue 1`() {
        val result = EmergencySos.selectPath(state(current = 0, hasWriteSecure = true), 1)
        assertEquals(DisableResult.PATH_SECURE_SETTINGS, result)
    }
}
