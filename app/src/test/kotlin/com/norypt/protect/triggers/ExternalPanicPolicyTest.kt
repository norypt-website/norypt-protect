package com.norypt.protect.triggers

import com.norypt.protect.triggers.ExternalPanicPolicy.ACTION_CONNECT
import com.norypt.protect.triggers.ExternalPanicPolicy.ACTION_DISCONNECT
import com.norypt.protect.triggers.ExternalPanicPolicy.ACTION_TRIGGER
import com.norypt.protect.triggers.ExternalPanicPolicy.Decision
import com.norypt.protect.triggers.ExternalPanicPolicy.decide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPanicPolicyTest {

    private val self = "com.norypt.protect"
    private val paired = "info.guardianproject.ripple"
    private val stranger = "com.evil.app"

    private fun call(
        action: String?,
        caller: String?,
        pairedPkg: String?,
        enabled: Boolean,
        signer: Boolean = true,
        cooldownOver: Boolean = true,
    ) = decide(action, caller, pairedPkg, enabled, self, signerMatches = signer, pairingCooldownOver = cooldownOver)

    private fun trigger(
        caller: String?,
        pairedPkg: String? = paired,
        enabled: Boolean = true,
        signerMatches: Boolean = true,
    ) = call(ACTION_TRIGGER, caller, pairedPkg, enabled, signerMatches)

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
        assertTrue(call("android.intent.action.VIEW", paired, paired, true) is Decision.Refuse)
        assertTrue(call(null, paired, paired, true) is Decision.Refuse)
    }

    // --- Pairing ---

    @Test
    fun `connect from an identifiable app offers pairing rather than pairing silently`() {
        val d = call(ACTION_CONNECT, stranger, null, true)

        // Offer, not Fire and not an automatic pairing: consent is the user's to give.
        assertEquals(Decision.OfferPairing(stranger), d)
    }

    @Test
    fun `connect from an unidentifiable caller is refused`() {
        assertTrue(call(ACTION_CONNECT, null, null, true) is Decision.Refuse)
    }

    @Test
    fun `the app cannot pair with itself`() {
        assertTrue(call(ACTION_CONNECT, self, null, true) is Decision.Refuse)
    }

    // A disarmed A5 has no pairing to offer: otherwise any app could raise the PIN prompt at will.
    @Test
    fun `connect is refused while the trigger is disarmed`() {
        assertTrue(call(ACTION_CONNECT, stranger, null, false) is Decision.Refuse)
    }

    // One prompt a minute at most, so a caller cannot keep the pairing prompt in the owner's face.
    @Test
    fun `connect is refused during the pairing cooldown`() {
        assertTrue(call(ACTION_CONNECT, stranger, null, true, cooldownOver = false) is Decision.Refuse)
    }

    @Test
    fun `the cooldown does not hold back firing or unpairing`() {
        assertEquals(Decision.Fire, call(ACTION_TRIGGER, paired, paired, true, cooldownOver = false))
        assertEquals(Decision.Unpair, call(ACTION_DISCONNECT, paired, paired, true, cooldownOver = false))
    }

    @Test
    fun `the cooldown lasts a minute from the last pairing prompt`() {
        val t0 = 5_000_000L
        assertTrue(ExternalPanicPolicy.pairingCooldownOver(lastOfferElapsedMs = null, nowElapsedMs = t0))
        assertFalse(ExternalPanicPolicy.pairingCooldownOver(t0, t0))
        assertFalse(ExternalPanicPolicy.pairingCooldownOver(t0, t0 + ExternalPanicPolicy.PAIRING_COOLDOWN_MS - 1))
        assertTrue(ExternalPanicPolicy.pairingCooldownOver(t0, t0 + ExternalPanicPolicy.PAIRING_COOLDOWN_MS))
        // A clock that reads earlier than the last prompt cannot end the cooldown early.
        assertFalse(ExternalPanicPolicy.pairingCooldownOver(t0, t0 - 1))
    }

    // --- Unpairing ---

    @Test
    fun `the paired app may unpair itself`() {
        assertEquals(Decision.Unpair, call(ACTION_DISCONNECT, paired, paired, true))
    }

    @Test
    fun `a stranger cannot unpair the user's trigger app`() {
        assertTrue(call(ACTION_DISCONNECT, stranger, paired, true) is Decision.Refuse)
        assertTrue(call(ACTION_DISCONNECT, null, paired, true) is Decision.Refuse)
    }

    // --- A pairing does not generalise ---

    @Test
    fun `pairing one app does not let a similarly named app fire`() {
        assertTrue(trigger("info.guardianproject.ripple.evil") is Decision.Refuse)
        assertTrue(trigger("info.guardianproject.rippl") is Decision.Refuse)
        assertTrue(trigger(paired.uppercase()) is Decision.Refuse)
    }

    // --- The pairing is bound to the paired app's signing key ---

    @Test
    fun `an app reinstalled under the paired name with another key cannot fire`() {
        assertTrue(trigger(paired, signerMatches = false) is Decision.Refuse)
    }

    @Test
    fun `an app reinstalled under the paired name with another key cannot unpair`() {
        assertTrue(call(ACTION_DISCONNECT, paired, paired, true, signer = false) is Decision.Refuse)
    }

    private fun resumes(caller: String?, enabled: Boolean, signer: Boolean) =
        ExternalPanicPolicy.resumesPairing(savedPackage = paired, callingPackage = caller, triggerEnabled = enabled, signerMatches = signer)

    // A rotation or a dark-mode switch recreates the prompt; the cooldown it stamped must not refuse it.
    @Test
    fun `a prompt recreated by a configuration change resumes for the same caller while armed`() {
        assertTrue(resumes(paired, enabled = true, signer = true))
    }

    @Test
    fun `a recreated prompt does not resume for another caller, none, or once A5 is disarmed`() {
        assertFalse(resumes("evil.app", enabled = true, signer = true))
        assertFalse(resumes(null, enabled = true, signer = true))
        assertFalse(resumes(paired, enabled = false, signer = true))
    }

    @Test
    fun `a recreated prompt does not resume once the caller is signed with another key`() {
        assertFalse(
            resumes(paired, enabled = true, signer = false),
        )
    }
}
