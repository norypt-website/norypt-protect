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
        PackageInternetTrigger,
        NotificationListenerTrigger,
        WorkProfileTrigger,
    )
}
