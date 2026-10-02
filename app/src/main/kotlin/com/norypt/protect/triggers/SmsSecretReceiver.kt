package com.norypt.protect.triggers

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.dpm.OwnPermissions
import com.norypt.protect.prefs.ProtectPrefs

class SmsSecretReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ProtectPrefs.isTriggerEnabled(context, "A6")) return
        val code = ProtectPrefs.smsSecretCode(context) ?: return
        if (!isUsableCode(code)) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val triggered = messages.any { msg -> matches(msg.messageBody, code) }
        if (triggered) {
            PanicHandler.panic(context, "sms.secret")
        }
    }

    companion object {
        /**
         * Shorter codes are rejected outright rather than armed. The trigger writes the
         * code as the user types, so a lower bound is also what stops a one-character
         * prefix of the intended code from being live against inbound SMS.
         */
        const val MIN_CODE_LENGTH = 8

        /** What the settings recommend, and what [generateCode] produces at least. */
        const val RECOMMENDED_CODE_LENGTH = 12

        private const val GENERATED_LENGTH = 16

        /** No 0/O, 1/l/I: the code is read off a screen and typed on another phone. */
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"

        /** A random code of [GENERATED_LENGTH] characters, about 93 bits. */
        fun generateCode(): String {
            val random = java.security.SecureRandom()
            return String(CharArray(GENERATED_LENGTH) { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] })
        }

        /**
         * Whitespace-ish code points that `Char.isWhitespace()` does not cover and that SMS
         * gateways insert. Written as escapes on purpose — literal copies of these are
         * invisible in a diff, and one of them is a byte-order mark.
         */
        private val NON_BREAKING_WHITESPACE = charArrayOf(
            '\u00A0', // NO-BREAK SPACE
            '\u202F', // NARROW NO-BREAK SPACE
            '\uFEFF', // ZERO WIDTH NO-BREAK SPACE / BOM
        )

        /**
         * Normalisation policy, applied identically to the stored code and the message body:
         *
         * - **NFKC** — a code typed on one keyboard and delivered through a network that
         *   re-encodes it must still compare equal. Without this, full-width digits or
         *   composed accents produce a silent non-match at the moment the user needs it.
         * - **Unicode whitespace trimmed**, including NBSP/NNBSP, which gateways insert and
         *   `String.trim()` alone does not remove on all of them.
         * - **Case-sensitive.** Case folding would throw away roughly a bit of entropy per
         *   letter, and the code is copy-pasted or typed deliberately, not spoken.
         *
         * Deliberately NOT normalised away: interior whitespace, and any carrier-appended
         * prefix or suffix. Tolerating affixes would mean accepting a substring match again,
         * which is the false positive that made an unrelated bank OTP wipe the device.
         */
        private fun normalise(value: String): String =
            java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
                .trim { it.isWhitespace() || it in NON_BREAKING_WHITESPACE }

        fun isUsableCode(code: String): Boolean = normalise(code).length >= MIN_CODE_LENGTH

        /**
         * The whole message must be the code. A substring test would fire on any unrelated
         * SMS that happens to contain it — a bank OTP, an order number, a marketing line —
         * and the consequence here is an irreversible factory reset.
         */
        fun matches(body: String?, code: String): Boolean {
            if (!isUsableCode(code)) return false
            if (body == null) return false
            return normalise(body) == normalise(code)
        }
    }
}

object SmsSecretTrigger : Trigger {
    override val id = "A6"
    override val label = "Secret SMS"
    override val description = "Wipe the device when an SMS consisting of exactly your secret code " +
        "arrives. Send it as a plain SMS: between phones that both use RCS chat, a message travels as " +
        "chat and is never seen as an SMS. Requires Device Owner — the wipe call is denied for non-DO " +
        "admins on Android 13+."
    override val requiredTier = Tier.DeviceOwner
    override fun arm(context: Context) {
        ProtectPrefs.setTriggerEnabled(context, "A6", true)
        OwnPermissions.grant(context, Manifest.permission.RECEIVE_SMS)
    }

    override fun disarm(context: Context) {
        ProtectPrefs.setTriggerEnabled(context, "A6", false)
        OwnPermissions.revoke(context, Manifest.permission.RECEIVE_SMS)
    }

    override fun problem(context: Context): String? {
        val code = ProtectPrefs.smsSecretCode(context)
        return when {
            code.isNullOrEmpty() -> "No secret code is set."
            !SmsSecretReceiver.isUsableCode(code) ->
                "The code is shorter than ${SmsSecretReceiver.MIN_CODE_LENGTH} characters, so it is ignored."
            context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ->
                "The SMS permission is not granted."
            else -> null
        }
    }
}
