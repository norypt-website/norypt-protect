package com.norypt.protect.triggers

/**
 * Decides what an inbound PanicKit intent is allowed to do. Pure so the rules that stand
 * between a third-party app and a factory reset are unit-testable.
 *
 * The threat here is asymmetric. A false negative costs the user an interop convenience;
 * a false positive is an unrelated app wiping their phone. Every rule below therefore
 * fails closed, and the trigger fires only when the caller is *both* identifiable and the
 * one app the user explicitly paired.
 */
object ExternalPanicPolicy {

    const val ACTION_TRIGGER = "info.guardianproject.panic.action.TRIGGER"
    const val ACTION_CONNECT = "info.guardianproject.panic.action.CONNECT"
    const val ACTION_DISCONNECT = "info.guardianproject.panic.action.DISCONNECT"

    sealed interface Decision {
        /** Caller is the paired trigger app: run the panic. */
        object Fire : Decision

        /** Caller wants to become the paired trigger app; needs explicit user consent. */
        data class OfferPairing(val packageName: String) : Decision

        /** Paired app asked to be unpaired. */
        object Unpair : Decision

        /** Do nothing, and say why. */
        data class Refuse(val reason: String) : Decision
    }

    /**
     * @param callingPackage from `Activity.getCallingActivity()?.packageName`. Null when the
     *   sender did not use `startActivityForResult`, which means the platform will not tell
     *   us who they are.
     * @param pairedPackage the single trigger app the user has approved, if any.
     * @param signerMatches whether the caller is signed with the key recorded at pairing.
     *   A package name alone is not an identity: if the paired app is uninstalled, any app
     *   later installed under the same name would inherit the pairing.
     * @param pairingCooldownOver whether [PAIRING_COOLDOWN_MS] has passed since the last
     *   pairing prompt (see [pairingCooldownOver]). Only pairing waits for it.
     */
    fun decide(
        action: String?,
        callingPackage: String?,
        pairedPackage: String?,
        triggerEnabled: Boolean,
        selfPackage: String,
        signerMatches: Boolean,
        pairingCooldownOver: Boolean,
    ): Decision = when (action) {
        ACTION_TRIGGER -> when {
            !triggerEnabled -> refuse("A5 disarmed")
            // An unidentifiable caller is the whole reason the receiver version was unsafe.
            // Anyone can send an intent; only startActivityForResult reveals who they are.
            callingPackage.isNullOrEmpty() -> refuse("caller not identifiable")
            pairedPackage.isNullOrEmpty() -> refuse("no trigger app paired")
            callingPackage != pairedPackage -> refuse("caller is not the paired trigger app")
            // Also covers pairings made before the key was recorded: they must be renewed.
            !signerMatches -> refuse("caller is not signed like the paired trigger app")
            else -> Decision.Fire
        }

        ACTION_CONNECT -> when {
            // Disarmed means no pairing prompt at all: otherwise any installed app could raise
            // a "pair me" PIN prompt whenever it liked and wait for a hurried owner to type.
            !triggerEnabled -> refuse("A5 disarmed")
            callingPackage.isNullOrEmpty() -> refuse("caller not identifiable")
            // Refuse to pair with ourselves — that would let our own exported surface
            // authorise itself.
            callingPackage == selfPackage -> refuse("cannot pair with self")
            // One prompt a minute at most, so a caller cannot keep it on screen until it is accepted.
            !pairingCooldownOver -> refuse("pairing prompt cooldown")
            else -> Decision.OfferPairing(callingPackage)
        }

        // Only the currently paired app may drop the pairing, so an unrelated app cannot
        // silently disarm the interop the user set up.
        ACTION_DISCONNECT -> when {
            callingPackage.isNullOrEmpty() -> refuse("caller not identifiable")
            callingPackage != pairedPackage -> refuse("caller is not the paired trigger app")
            !signerMatches -> refuse("caller is not signed like the paired trigger app")
            else -> Decision.Unpair
        }

        else -> refuse("unsupported action")
    }

    /** Least time between two pairing prompts. */
    const val PAIRING_COOLDOWN_MS = 60_000L

    /**
     * Whether a pairing prompt may be shown at [nowElapsedMs], the last one having been shown at
     * [lastOfferElapsedMs] (null: none yet). A clock reading before the last prompt keeps waiting.
     */
    fun pairingCooldownOver(lastOfferElapsedMs: Long?, nowElapsedMs: Long): Boolean {
        if (lastOfferElapsedMs == null) return true
        val age = nowElapsedMs - lastOfferElapsedMs
        return age >= PAIRING_COOLDOWN_MS
    }

    /**
     * Whether a pairing prompt recreated by a configuration change (rotation, dark mode) carries on
     * where it was instead of being decided afresh, which the cooldown it stamped would refuse. Only
     * for the same caller that was shown, still signed with the key that was shown, and only while
     * A5 is still armed.
     */
    fun resumesPairing(savedPackage: String, callingPackage: String?, triggerEnabled: Boolean, signerMatches: Boolean): Boolean =
        triggerEnabled && signerMatches && callingPackage == savedPackage

    private fun refuse(reason: String) = Decision.Refuse(reason)
}
