package com.norypt.protect.checkup

import android.app.admin.DevicePolicyManager
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CheckStatus { OK, ATTENTION, INFO, CONFIRM }

enum class CheckId(val title: String) {
    DEVICE_NAME("Device name"),
    BLUETOOTH_NAME("Bluetooth name"),
    USB_DEBUGGING("USB debugging"),
    SCREEN_LOCK("Screen lock"),
    SECURITY_PATCH("Security patch"),
    VPN("VPN"),
    PRIVATE_DNS("Private DNS"),
    LOCKSCREEN_NOTIFICATIONS("Lock-screen notifications"),
    TRUST_AGENTS("Smart Lock"),
    CELLULAR_2G("2G"),
    GOS_AUTO_REBOOT("Auto reboot"),
    GOS_USB_C("USB-C port"),
    GOS_DURESS_PIN("Duress PIN"),
    GOS_FINGERPRINT_PIN("2-factor fingerprint unlock"),
}

data class CheckResult(val id: CheckId, val status: CheckStatus, val detail: String)

/** Everything the checkup reads, as plain values. Null means "could not be read". */
data class Readings(
    val model: String,
    val deviceName: String?,
    val bluetoothName: String?,
    /** Whether the owner allowed the Nearby devices permission, which reading the Bluetooth name needs. */
    val bluetoothAllowed: Boolean,
    val adbEnabled: Boolean?,
    val passwordComplexity: Int?,
    val securityPatch: LocalDate?,
    val today: LocalDate,
    val alwaysOnVpn: String?,
    /** Whether [alwaysOnVpn] could be read at all: only a Device Owner can. */
    val vpnReadable: Boolean,
    val vpnLockdown: Boolean,
    val privateDnsMode: Int?,
    /** Norypt's own keyguard restrictions. */
    val keyguardDisabledFeatures: Int,
    /** The owner's "show sensitive content on the lock screen" setting. */
    val privateNotificationsAllowed: Boolean?,
    /** Whether 2G is blocked by policy; null below Android 14. */
    val twoGBlocked: Boolean?,
    val grapheneOs: Boolean,
    /** GrapheneOS items the owner ticked as set. */
    val confirmed: Set<CheckId>,
)

/**
 * Readings to check results. Every status comes from what the platform reports, never from a
 * button having been pressed, so a refused fix stays in Attention.
 */
object CheckupRules {

    const val PATCH_MAX_AGE_DAYS = 60L

    /** GrapheneOS settings no app can read: where the owner finds each one. */
    private val GRAPHENE_WHERE = linkedMapOf(
        CheckId.GOS_AUTO_REBOOT to "Security & privacy › Exploit protection › Auto reboot. A short time returns a " +
            "seized phone to its encrypted, locked state.",
        CheckId.GOS_USB_C to "Security & privacy › Exploit protection › USB-C port: charging only while locked.",
        CheckId.GOS_DURESS_PIN to "Security & privacy › Device unlock: a duress PIN or password wipes the phone when entered.",
        CheckId.GOS_FINGERPRINT_PIN to "Security & privacy › Device unlock › Fingerprint: 2-factor unlock asks for the " +
            "PIN after the finger, so a forced finger is not enough.",
    )

    fun evaluate(r: Readings): List<CheckResult> = buildList {
        add(name(CheckId.DEVICE_NAME, r.deviceName, r.model))
        add(bluetooth(r))
        add(usbDebugging(r.adbEnabled))
        add(screenLock(r.passwordComplexity))
        add(patch(r.securityPatch, r.today))
        add(vpn(r))
        add(privateDns(r))
        add(lockscreenNotifications(r))
        add(trustAgents(r))
        add(twoG(r))
        if (r.grapheneOs) addAll(grapheneConfirms(r.confirmed))
    }

    fun needsAttention(results: List<CheckResult>): Int =
        results.count { it.status == CheckStatus.ATTENTION || it.status == CheckStatus.CONFIRM }

    private fun ok(id: CheckId, detail: String) = CheckResult(id, CheckStatus.OK, detail)
    private fun attention(id: CheckId, detail: String) = CheckResult(id, CheckStatus.ATTENTION, detail)
    private fun info(id: CheckId, detail: String) = CheckResult(id, CheckStatus.INFO, detail)

    private fun name(id: CheckId, value: String?, model: String): CheckResult = when {
        value == null -> info(id, "Could not be read.")
        value.equals(model, ignoreCase = true) -> ok(id, "Shows only the model name.")
        else -> attention(id, "Shows “$value” to nearby devices and networks. Names often include the owner's name.")
    }

    private fun bluetooth(r: Readings): CheckResult =
        if (r.bluetoothAllowed) {
            name(CheckId.BLUETOOTH_NAME, r.bluetoothName, r.model)
        } else {
            info(CheckId.BLUETOOTH_NAME, "Reading it needs the Nearby devices permission, which you can allow here.")
        }

    private fun usbDebugging(adb: Boolean?): CheckResult = when (adb) {
        true -> attention(
            CheckId.USB_DEBUGGING,
            "On: a computer you once allowed can control this phone. Turn it off when you are not developing.",
        )
        false -> ok(CheckId.USB_DEBUGGING, "Off.")
        null -> info(CheckId.USB_DEBUGGING, "Could not be read.")
    }

    private fun screenLock(complexity: Int?): CheckResult = when (complexity) {
        DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH -> ok(CheckId.SCREEN_LOCK, "Strong.")
        DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM ->
            info(CheckId.SCREEN_LOCK, "Medium. A PIN of 8 or more digits, or a passphrase, counts as strong.")
        null -> info(CheckId.SCREEN_LOCK, "Could not be read.")
        else -> attention(
            CheckId.SCREEN_LOCK,
            "Weak or missing. Set a PIN of 6 or more digits without repeats or sequences, or a passphrase.",
        )
    }

    private fun patch(date: LocalDate?, today: LocalDate): CheckResult {
        if (date == null) return info(CheckId.SECURITY_PATCH, "Could not be read.")
        val age = ChronoUnit.DAYS.between(date, today)
        return if (age > PATCH_MAX_AGE_DAYS) {
            attention(CheckId.SECURITY_PATCH, "$age days old. Phones are broken into through known holes; install the update.")
        } else {
            ok(CheckId.SECURITY_PATCH, "From $date.")
        }
    }

    private fun vpn(r: Readings): CheckResult = when {
        !r.vpnReadable -> info(CheckId.VPN, "Could not be read without Device Owner.")
        r.alwaysOnVpn == null -> info(CheckId.VPN, "No always-on VPN. Each network you join sees where you connect.")
        r.vpnLockdown -> ok(CheckId.VPN, "Always on, and nothing gets out without it.")
        else -> attention(
            CheckId.VPN,
            "Always on, but traffic goes out without it while it reconnects. Turn on “Block connections without VPN”.",
        )
    }

    private fun privateDns(r: Readings): CheckResult = when (r.privateDnsMode) {
        DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME -> ok(CheckId.PRIVATE_DNS, "Set to a provider you chose.")
        DevicePolicyManager.PRIVATE_DNS_MODE_OPPORTUNISTIC ->
            info(CheckId.PRIVATE_DNS, "Automatic: encrypted only where the network supports it.")
        DevicePolicyManager.PRIVATE_DNS_MODE_OFF -> if (r.alwaysOnVpn != null) {
            info(CheckId.PRIVATE_DNS, "Off. Your always-on VPN carries lookups if it is set up to.")
        } else {
            attention(CheckId.PRIVATE_DNS, "Off: every network you join sees the names of the sites and services you look up.")
        }
        else -> info(CheckId.PRIVATE_DNS, "Could not be read.")
    }

    private fun lockscreenNotifications(r: Readings): CheckResult {
        val id = CheckId.LOCKSCREEN_NOTIFICATIONS
        val byNorypt = r.keyguardDisabledFeatures and DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS != 0
        return when {
            byNorypt -> ok(id, "Content hidden by Norypt.")
            r.privateNotificationsAllowed == false -> ok(id, "Content hidden by your setting.")
            r.privateNotificationsAllowed == true -> attention(id, "Message content shows to whoever holds the locked phone.")
            else -> info(id, "Could not be read. Norypt can hide the content.")
        }
    }

    private fun trustAgents(r: Readings): CheckResult {
        val blocked = r.keyguardDisabledFeatures and DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS != 0
        return when {
            blocked -> ok(CheckId.TRUST_AGENTS, "Blocked by Norypt.")
            r.grapheneOs -> ok(CheckId.TRUST_AGENTS, "GrapheneOS has no Smart Lock.")
            else -> attention(CheckId.TRUST_AGENTS, "Can keep the phone unlocked in places or near devices. Norypt can block it.")
        }
    }

    private fun twoG(r: Readings): CheckResult = when (r.twoGBlocked) {
        null -> info(CheckId.CELLULAR_2G, "Blocking 2G needs Android 14 or later.")
        true -> ok(CheckId.CELLULAR_2G, "Blocked.")
        false -> if (r.grapheneOs) {
            info(CheckId.CELLULAR_2G, "Allowed. GrapheneOS's LTE-only mode also keeps the phone off 2G.")
        } else {
            attention(CheckId.CELLULAR_2G, "Allowed. Fake base stations force phones down to 2G to intercept them.")
        }
    }

    private fun grapheneConfirms(confirmed: Set<CheckId>): List<CheckResult> = GRAPHENE_WHERE.map { (id, where) ->
        if (id in confirmed) {
            ok(id, "Confirmed by you.")
        } else {
            CheckResult(id, CheckStatus.CONFIRM, "$where Confirm once you have set it.")
        }
    }
}
