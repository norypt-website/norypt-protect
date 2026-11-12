package com.norypt.protect.triggers

import android.content.Context
import com.norypt.protect.admin.Tier

interface Trigger {
    val id: String
    val label: String
    val description: String
    val requiredTier: Tier
    /**
     * Optional note shown in the Triggers UI when running on GrapheneOS. Use
     * this to flag triggers that are fully blocked or partially degraded by
     * GrapheneOS's hardening (e.g. filtered broadcasts, scoped secure reads).
     * Null = no GrapheneOS-specific caveat.
     */
    val grapheneOsNote: String? get() = null
    fun arm(context: Context)
    fun disarm(context: Context)

    /**
     * Why this trigger cannot fire although it is armed (a missing code, permission or
     * pairing), or null when nothing stands in its way. The UI shows it next to the switch
     * and Home counts only triggers without one, so "armed" never silently means "inert".
     */
    fun problem(context: Context): String? = null
}

/**
 * Central registry of every Trigger Norypt Protect ships.
 * Each Trigger module appends its singleton here.
 */
object TriggerRegistry {
    val all: List<Trigger> = listOf(
        ExternalPanicTrigger,
        SmsSecretTrigger,
        ExternalBroadcastTrigger,
        UsbLockedTrigger,
        UnlockedTimerTrigger,
        FakeMessengerTrigger,
        MaxFailedTrigger,
        FailedAuthNotifTrigger,
        DuressPanicTrigger,
        PowerGestureTrigger,
        DeadmanTrigger,
        UnlockDeadlineTrigger,
        ForcedRestartTrigger,
        PackageInternetTrigger,
        // B6 (notification listener) removed: it asked for access to every notification on
        // the phone and did nothing with it. A12 (work-profile wipe) removed: nothing read
        // its switch, so arming it changed nothing.
        // C5 ShutdownTrigger removed: it waited for ACTION_SHUTDOWN on a manifest receiver, where
        // Android 14+ never delivers it. C9 registers for it at runtime, where it is delivered.
    )
}
