package com.norypt.protect.triggers

import android.content.Context
import com.norypt.protect.admin.Tier

interface Trigger {
    val id: String
    val label: String
    val description: String
    val requiredTier: Tier
    fun arm(context: Context)
    fun disarm(context: Context)
}

/**
 * Central registry of all available triggers.
 * Later tasks add their Trigger instances to [all].
 * The list is intentionally empty in Batch 2-A; Batch 2-B/C will extend it.
 */
object TriggerRegistry {
    val all: List<Trigger> = listOf(
        PanicKitTrigger,
        SmsSecretTrigger,
        ExternalBroadcastTrigger,
        UsbLockedTrigger,
        UnlockedTimerTrigger,
        FakeMessengerTrigger,
        MaxFailedTrigger,
        FailedAuthNotifTrigger,
        DuressPanicTrigger,
        PowerGestureTrigger,
    )
}
