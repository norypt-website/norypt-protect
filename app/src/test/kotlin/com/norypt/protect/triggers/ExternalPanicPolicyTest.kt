package com.norypt.protect.triggers

import com.norypt.protect.triggers.ExternalPanicPolicy.ACTION_CONNECT
import com.norypt.protect.triggers.ExternalPanicPolicy.ACTION_DISCONNECT
import com.norypt.protect.triggers.ExternalPanicPolicy.ACTION_TRIGGER
import com.norypt.protect.triggers.ExternalPanicPolicy.Decision
import com.norypt.protect.triggers.ExternalPanicPolicy.decide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPanicPolicyTest {

    private val self = "com.norypt.protect"
    private val paired = "info.guardianproject.ripple"
    private val stranger = "com.evil.app"

    private fun trigger(
        caller: String?,
        pairedPkg: String? = paired,
        enabled: Boolean = true,
    ) = decide(ACTION_TRIGGER, caller, pairedPkg, enabled, self)

    // --- The only path that may wipe the device ---

    @Test
    fun `paired app fires the panic`() {
        assertEquals(Decision.Fire, trigger(paired))
    }

    // --- Everything else must refuse. Each of these is a device-wipe if it gets through. ---

    @Test
    fun `an unidentifiable caller cannot fire`() {
        // This is the case the BroadcastReceiver version could never distinguish: an intent
        // sent without startActivityForResult carries no caller identity.
        assertTrue(trigger(null) is Decision.Refuse)
        assertTrue(trigger("") is Decision.Refuse)
    }

    @Test
    fun `an unpaired stranger cannot fire`() {
        assertTrue(trigger(stranger) is Decision.Refuse)
    }

    @Test
    fun `nothing fires when no app is paired`() {
        assertTrue(trigger(stranger, pairedPkg = null) is Decision.Refuse)
        assertTrue(trigger(paired, pairedPkg = null) is Decision.Refuse)
        assertTrue(trigger(paired, pairedPkg = "") is Decision.Refuse)
    }

    @Test
    fun `a disarmed trigger cannot fire even for the paired app`() {
        assertTrue(trigger(paired, enabled = false) is Decision.Refuse)
    }

    @Test
    fun `an unsupported action cannot fire`() {
        assertTrue(decide("android.intent.action.VIEW", paired, paired, true, self) is Decision.Refuse)
        assertTrue(decide(null, paired, paired, true, self) is Decision.Refuse)
    }

    // --- Pairing ---

    @Test
    fun `connect from an identifiable app offers pairing rather than pairing silently`() {
        val d = decide(ACTION_CONNECT, stranger, null, true, self)

        // Offer, not Fire and not an automatic pairing: consent is the user's to give.
        assertEquals(Decision.OfferPairing(stranger), d)
    }

    @Test
    fun `connect from an unidentifiable caller is refused`() {
        assertTrue(decide(ACTION_CONNECT, null, null, true, self) is Decision.Refuse)
    }

    @Test
    fun `the app cannot pair with itself`() {
        assertTrue(decide(ACTION_CONNECT, self, null, true, self) is Decision.Refuse)
    }

    @Test
    fun `connect does not depend on the trigger being armed`() {
        // Pairing while disarmed is fine; firing while disarmed is not.
        assertEquals(Decision.OfferPairing(stranger), decide(ACTION_CONNECT, stranger, null, false, self))
    }

    // --- Unpairing ---

    @Test
    fun `the paired app may unpair itself`() {
        assertEquals(Decision.Unpair, decide(ACTION_DISCONNECT, paired, paired, true, self))
    }

    @Test
    fun `a stranger cannot unpair the user's trigger app`() {
        assertTrue(decide(ACTION_DISCONNECT, stranger, paired, true, self) is Decision.Refuse)
        assertTrue(decide(ACTION_DISCONNECT, null, paired, true, self) is Decision.Refuse)
    }

    // --- A pairing does not generalise ---

    @Test
    fun `pairing one app does not let a similarly named app fire`() {
        assertTrue(trigger("info.guardianproject.ripple.evil") is Decision.Refuse)
        assertTrue(trigger("info.guardianproject.rippl") is Decision.Refuse)
        assertTrue(trigger(paired.uppercase()) is Decision.Refuse)
    }
}
