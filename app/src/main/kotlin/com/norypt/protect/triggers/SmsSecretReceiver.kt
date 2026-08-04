package com.norypt.protect.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
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
    override val description = "Wipe device when an SMS containing your secret code is received. " +
        "Requires Device Owner — the wipe call is denied for non-DO admins on Android 13+."
    override val requiredTier = Tier.DeviceOwner
    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A6", true)
    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A6", false)
}
