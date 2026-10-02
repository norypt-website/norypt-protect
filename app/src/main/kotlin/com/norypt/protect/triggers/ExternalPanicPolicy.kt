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
     */
    fun decide(
        action: String?,
        callingPackage: String?,
        pairedPackage: String?,
        triggerEnabled: Boolean,
        selfPackage: String,
        signerMatches: Boolean,
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
            callingPackage.isNullOrEmpty() -> refuse("caller not identifiable")
            // Refuse to pair with ourselves — that would let our own exported surface
            // authorise itself.
            callingPackage == selfPackage -> refuse("cannot pair with self")
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

    private fun refuse(reason: String) = Decision.Refuse(reason)
}
