package com.norypt.protect.shield

/** What the audit knows about one installed app. */
data class AppFacts(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val deviceAdmin: Boolean,
    val accessibility: Boolean,
    val keyboard: Boolean,
    val notificationAccess: Boolean,
    val alwaysOnVpn: Boolean,
    /** Installing package; null when Android does not know it (ADB installs, for one). */
    val installer: String?,
    val inLauncher: Boolean,
    /** Sensitive runtime permissions the app holds right now. */
    val sensitive: Set<String>,
)

/** One audit row. */
data class AuditEntry(val packageName: String, val label: String, val system: Boolean, val reasons: List<String>)

/** Why an app is worth the owner's attention. Pure, so the wording and the order are tested. */
object AuditRules {

    /** App stores and F-Droid clients; an app they installed is not flagged for where it came from. */
    val KNOWN_STORES = setOf(
        "com.android.vending", // Google Play
        "org.fdroid.fdroid", // F-Droid
        "org.fdroid.basic", // F-Droid Basic
        "com.looker.droidify", // Droid-ify, an F-Droid client
        "com.machiav3lli.fdroid", // Neo Store, an F-Droid client
        "app.accrescent.client", // Accrescent
        "app.grapheneos.apps", // GrapheneOS App Store
        "com.aurora.store", // Aurora Store
    )

    /** The system installer that installs APK files. */
    private val FILE_INSTALLERS = setOf("com.android.packageinstaller", "com.google.android.packageinstaller")

    /** Sensitive runtime permissions, in the order and the words the owner reads. */
    val SENSITIVE = linkedMapOf(
        "android.permission.CAMERA" to "camera",
        "android.permission.RECORD_AUDIO" to "microphone",
        "android.permission.ACCESS_FINE_LOCATION" to "location",
        "android.permission.ACCESS_COARSE_LOCATION" to "location",
        "android.permission.ACCESS_BACKGROUND_LOCATION" to "location in the background",
        "android.permission.READ_SMS" to "SMS",
        "android.permission.RECEIVE_SMS" to "SMS",
        "android.permission.SEND_SMS" to "SMS",
        "android.permission.READ_CALL_LOG" to "call log",
        "android.permission.READ_CONTACTS" to "contacts",
        "android.permission.READ_PHONE_STATE" to "phone",
        "android.permission.READ_PHONE_NUMBERS" to "phone",
        "android.permission.CALL_PHONE" to "phone",
        "android.permission.BODY_SENSORS" to "body sensors",
        "android.permission.BODY_SENSORS_BACKGROUND" to "body sensors",
        "android.permission.BLUETOOTH_SCAN" to "nearby devices",
        "android.permission.NEARBY_WIFI_DEVICES" to "nearby devices",
    )

    fun reasons(f: AppFacts): List<String> = buildList {
        if (f.deviceAdmin) add("Device admin: can lock the phone or apply policies")
        if (f.accessibility) add("Accessibility service on: can read the screen and act for you")
        if (f.keyboard) add("Keyboard: sees everything you type")
        if (f.notificationAccess) add("Reads all your notifications")
        if (f.alwaysOnVpn) add("Always-on VPN: carries all your traffic")
        installerReason(f)?.let { add(it) }
        val held = SENSITIVE.filterKeys { it in f.sensitive }.values.distinct()
        if (held.isNotEmpty()) add("Holds: " + held.joinToString(", "))
        if (isNotEmpty() && !f.inLauncher && !f.system) add("No icon in the app drawer")
    }

    private fun installerReason(f: AppFacts): String? = when {
        f.system || f.installer in KNOWN_STORES -> null
        f.installer == null -> "Not installed from an app store (ADB or an unknown installer)"
        f.installer in FILE_INSTALLERS -> "Installed from an APK file, not an app store"
        else -> "Installed by ${f.installer}, not a known app store"
    }

    fun entries(facts: List<AppFacts>, showSystem: Boolean, selfPackage: String): List<AuditEntry> =
        facts.asSequence()
            .filter { it.packageName != selfPackage && (showSystem || !it.system) }
            .map { AuditEntry(it.packageName, it.label, it.system, reasons(it)) }
            .filter { it.reasons.isNotEmpty() }
            .sortedWith(compareByDescending<AuditEntry> { it.reasons.size }.thenBy { it.label.lowercase() })
            .toList()
}
