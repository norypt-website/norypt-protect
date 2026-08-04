package com.norypt.protect.triggers

import com.norypt.protect.triggers.SmsSecretReceiver.Companion.MIN_CODE_LENGTH
import com.norypt.protect.triggers.SmsSecretReceiver.Companion.isUsableCode
import com.norypt.protect.triggers.SmsSecretReceiver.Companion.matches
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSecretMatchTest {

    private val code = "correct-horse-battery"

    @Test
    fun `exact body matches`() {
        assertTrue(matches(code, code))
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertTrue(matches("  $code \n", code))
    }

    @Test
    fun `a message merely containing the code does not fire`() {
        // The substring behaviour this replaces would factory-reset the device on any
        // unrelated SMS that happened to contain the code.
        assertFalse(matches("Your verification code is $code, do not share it", code))
        assertFalse(matches("$code extra", code))
        assertFalse(matches("prefix $code", code))
    }

    @Test
    fun `unrelated message does not fire`() {
        assertFalse(matches("Your package is out for delivery", code))
        assertFalse(matches(null, code))
        assertFalse(matches("", code))
    }

    @Test
    fun `matching is case sensitive`() {
        assertFalse(matches(code.uppercase(), code))
    }

    @Test
    fun `codes shorter than the minimum are never armed`() {
        val short = "W"
        assertFalse(isUsableCode(short))
        // The trigger UI persists as the user types, so a one-character prefix of the
        // intended code must not be live against inbound SMS.
        assertFalse(matches(short, short))

        assertFalse(isUsableCode("911"))
        assertFalse(matches("911", "911"))
    }

    @Test
    fun `a code exactly at the minimum length is usable`() {
        val atLimit = "a".repeat(MIN_CODE_LENGTH)
        assertTrue(isUsableCode(atLimit))
        assertTrue(matches(atLimit, atLimit))

        val belowLimit = "a".repeat(MIN_CODE_LENGTH - 1)
        assertFalse(isUsableCode(belowLimit))
    }

    @Test
    fun `whitespace padding does not inflate a short code past the minimum`() {
        val padded = "   W   "
        assertFalse(isUsableCode(padded))
    }

    // --- Normalisation policy: trim + NFKC, case-sensitive ---
    // Each of these is a silent false negative if it does not match: the user sends the
    // panic SMS under coercion and nothing happens.

    @Test
    fun `trailing newline still matches`() {
        assertTrue(matches("$code\n", code))
        assertTrue(matches("$code\r\n", code))
    }

    @Test
    fun `leading and trailing spaces still match`() {
        assertTrue(matches(" $code", code))
        assertTrue(matches("$code  ", code))
        assertTrue(matches("\t$code\t", code))
    }

    @Test
    fun `non-breaking spaces around the code still match`() {
        assertTrue(matches("\u00A0$code\u00A0", code))     // NBSP
        assertTrue(matches("\u202F$code\u202F", code))     // narrow NBSP
        assertTrue(matches("\uFEFF$code", code))         // BOM / zero-width NBSP
    }

    @Test
    fun `full-width digits are NFKC-folded to their ascii form`() {
        val ascii = "wipe12345678"
        val fullWidth = "wipe\uFF11\uFF12\uFF13\uFF14\uFF15\uFF16\uFF17\uFF18"
        assertTrue(matches(fullWidth, ascii))
    }

    @Test
    fun `decomposed accents match their composed form`() {
        val composed = "r\u00E9sistance"                    // e-acute as one code point
        val decomposed = "re\u0301sistance"                 // e + combining acute
        assertTrue(matches(decomposed, composed))
    }

    @Test
    fun `case is still significant after normalisation`() {
        assertFalse(matches(code.uppercase(), code))
        assertFalse(matches(code.replaceFirstChar { it.uppercase() }, code))
    }

    @Test
    fun `carrier-appended text does not match, by design`() {
        // Accepting affixes would reopen the substring false positive. Documented as a
        // known limitation rather than silently tolerated.
        assertFalse(matches("$code - Sent via ExampleCarrier", code))
        assertFalse(matches("FREE MSG: $code", code))
    }

    @Test
    fun `interior whitespace is significant`() {
        assertFalse(matches(code.replace("-", " "), code))
    }

    @Test
    fun `a stored code with stray padding still arms and matches the clean body`() {
        val padded = "  $code\n"
        assertTrue(isUsableCode(padded))
        assertTrue(matches(code, padded))
    }
}
