package com.norypt.protect.triggers

import android.app.usage.UsageStatsManager
import android.content.Context
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs

object FakeMessengerMonitor {
    fun tick(context: Context) {
        if (!ProtectPrefs.isTriggerEnabled(context, "A10")) return
        val pkg = ProtectPrefs.fakeMessengerPackage(context)
        if (pkg.isNullOrEmpty()) return
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 30_000L,
            now,
        ) ?: return
        val mostRecent = stats.maxByOrNull { it.lastTimeUsed } ?: return
        if (mostRecent.packageName == pkg) {
            PanicHandler.panic(context, "fake.messenger")
        }
    }
}

object FakeMessengerTrigger : Trigger {
    override val id = "A10"
    override val label = "Fake messenger trap"
    override val description = "Wipe if the decoy app is opened (Usage Stats access required). " +
        "Requires Device Owner — the wipe call is denied for non-DO admins on Android 13+."
    override val requiredTier = Tier.DeviceOwner
    override fun arm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A10", true)
    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, "A10", false)
}
