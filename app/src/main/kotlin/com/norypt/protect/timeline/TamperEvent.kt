package com.norypt.protect.timeline

/** How much a timeline entry should stand out when the owner reads it back. */
enum class Severity { Info, Notable, Alert }

/**
 * Everything the timeline can record. The label is what the Timeline tab shows; the detail
 * string on each event carries the specifics.
 *
 * Enum names are persisted, so renaming one orphans the entries already written under it.
 * [TamperLogStore.decode] drops an unknown name instead of failing the whole read.
 */
enum class TamperKind(val label: String, val defaultSeverity: Severity) {
    BOOT("Device booted", Severity.Notable),
    UNOBSERVED_BOOTS("Booted while monitoring was off", Severity.Alert),
    MONITOR_STARTED("Monitoring started", Severity.Info),
    APP_UPDATED("Norypt Protect updated", Severity.Notable),
    UNLOCK("Device unlocked", Severity.Info),
    UNLOCK_FAILED("Failed unlock attempt", Severity.Notable),
    CREDENTIAL_CHANGED("Screen-lock credential changed", Severity.Alert),
    SCREEN_LOCK_REMOVED("Screen lock removed", Severity.Alert),
    SCREEN_LOCK_SET("Screen lock set", Severity.Notable),
    BIOMETRIC_CHANGED("Biometric enrollment changed", Severity.Alert),
    SIM_CHANGED("SIM changed", Severity.Alert),
    USB_CONNECTED("USB connected", Severity.Info),
    USB_DISCONNECTED("USB disconnected", Severity.Info),
    USB_DEBUGGING("USB debugging", Severity.Alert),
    CLOCK_CHANGED("System clock changed", Severity.Notable),
    ADMIN_DISABLED("Device admin removed", Severity.Alert),
    WIPE_TRIGGERED("Wipe trigger fired", Severity.Alert),
    LOCKDOWN("Lockdown mode", Severity.Notable),
    INSTALL_BLOCK("App installation block", Severity.Notable),
    MOTION_LOCK("Locked by sudden movement", Severity.Notable),
    TIMELINE("Timeline", Severity.Info),
}

data class TamperEvent(
    /** Wall-clock time: what the owner reads. Wrong if the clock was changed, hence the next field. */
    val epochMs: Long,
    /** Monotonic time since boot: orders entries within one boot even if the clock jumped. */
    val elapsedMs: Long,
    val kind: TamperKind,
    val severity: Severity,
    val detail: String,
)
