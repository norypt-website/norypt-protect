package com.norypt.protect.triggers

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.prefs.ProtectPrefs

/**
 * A10 — wipe if the decoy app is opened.
 *
 * Reads the usage-event stream since the last check rather than a usage summary. The summary
 * only showed which app was used last, so a short look at the decoy followed by Home was
 * missed unless a 30-second poll happened to land inside it.
 */
object FakeMessengerMonitor {

    fun tick(context: Context) {
        if (!ProtectPrefs.isTriggerEnabled(context, FakeMessengerTrigger.id)) return
        val pkg = ProtectPrefs.fakeMessengerPackage(context)?.trim()
        if (pkg.isNullOrEmpty()) return
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return
        val now = System.currentTimeMillis()
        // First check after arming starts from now: opening the app before it was armed
        // must not count.
        val since = ProtectPrefs.fakeMessengerCursorMs(context).takeIf { it in 1..now } ?: now
        ProtectPrefs.setFakeMessengerCursorMs(context, now)
        if (openedSince(usm.queryEvents(since, now), pkg)) {
            PanicHandler.panic(context, "fake.messenger")
        }
    }

    private fun openedSince(events: UsageEvents, pkg: String): Boolean {
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (isDecoyOpened(event.eventType, event.packageName, pkg)) return true
        }
        return false
    }

    internal fun isDecoyOpened(eventType: Int, packageName: String?, decoy: String): Boolean =
        eventType == UsageEvents.Event.ACTIVITY_RESUMED && packageName == decoy

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }
}

object FakeMessengerTrigger : Trigger {
    override val id = "A10"
    override val label = "Fake messenger trap"
    override val description = "Wipe if the decoy app is opened (Usage Stats access required). " +
        "Requires Device Owner — the wipe call is denied for non-DO admins on Android 13+."
    override val requiredTier = Tier.DeviceOwner

    override fun arm(context: Context) {
        ProtectPrefs.setFakeMessengerCursorMs(context, System.currentTimeMillis())
        ProtectPrefs.setTriggerEnabled(context, id, true)
    }

    override fun disarm(context: Context) = ProtectPrefs.setTriggerEnabled(context, id, false)

    override fun problem(context: Context): String? {
        val pkg = ProtectPrefs.fakeMessengerPackage(context)?.trim()
        return when {
            pkg.isNullOrEmpty() -> "No decoy app is set."
            !isInstalled(context, pkg) -> "The decoy app ($pkg) is not installed."
            !FakeMessengerMonitor.hasUsageAccess(context) -> "Usage access is off, so opening the decoy is not seen."
            else -> null
        }
    }

    private fun isInstalled(context: Context, pkg: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        true
    }.getOrDefault(false)
}
