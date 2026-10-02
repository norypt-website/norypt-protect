package com.norypt.protect.checkup

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.prefs.ProtectPrefs

/**
 * Location services off while the screen is off and back on at unlock, but only if Norypt switched
 * them off: an owner who had location off finds it off. Device Owner only. It switches location
 * services; it never reads a location.
 */
object LocationWhileLocked {

    enum class Action { TURN_OFF, TURN_ON, NOTHING }

    internal fun onScreenOff(featureOn: Boolean, locationOn: Boolean): Action =
        if (featureOn && locationOn) Action.TURN_OFF else Action.NOTHING

    internal fun onUnlocked(weTurnedItOff: Boolean): Action = if (weTurnedItOff) Action.TURN_ON else Action.NOTHING

    private var receiver: BroadcastReceiver? = null

    fun isEnabled(ctx: Context): Boolean = ProtectPrefs.locationWhileLockedOn(ctx)

    /** Turning it off while Norypt holds location off gives location back at once. */
    fun setEnabled(ctx: Context, on: Boolean): Boolean {
        if (dpm(ctx)?.isDeviceOwnerApp(ctx.packageName) != true) return false
        ProtectPrefs.setLocationWhileLockedOn(ctx, on)
        if (!on) restore(ctx)
        return true
    }

    /** Runs with the monitoring service; also gives back a switch-off that a killed process left behind. */
    fun start(ctx: Context) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> perform(c, onScreenOff(isEnabled(c), locationOn(c)))
                    Intent.ACTION_USER_PRESENT -> restore(c)
                    // A wake inside the lock delay sends no USER_PRESENT: the phone never locked.
                    Intent.ACTION_SCREEN_ON -> if (!isLocked(c)) restore(c)
                }
            }
        }
        receiver = r
        ctx.applicationContext.registerReceiver(
            r,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
        if (!isLocked(ctx)) restore(ctx)
    }

    fun stop(ctx: Context) {
        receiver?.let { runCatching { ctx.applicationContext.unregisterReceiver(it) } }
        receiver = null
    }

    private fun restore(ctx: Context) = perform(ctx, onUnlocked(ProtectPrefs.locationTurnedOffByUs(ctx)))

    private fun perform(ctx: Context, action: Action) {
        when (action) {
            Action.TURN_OFF -> if (setLocation(ctx, false)) ProtectPrefs.setLocationTurnedOffByUs(ctx, true)
            Action.TURN_ON -> if (setLocation(ctx, true)) ProtectPrefs.setLocationTurnedOffByUs(ctx, false)
            Action.NOTHING -> Unit
        }
    }

    private fun setLocation(ctx: Context, on: Boolean): Boolean = runCatching {
        val dpm = dpm(ctx) ?: return false
        dpm.setLocationEnabled(ComponentName(ctx, ProtectAdminReceiver::class.java), on)
    }.isSuccess

    private fun locationOn(ctx: Context): Boolean = ctx.getSystemService(LocationManager::class.java)?.isLocationEnabled == true

    /** Unknown counts as locked: location then stays off until a real unlock. */
    private fun isLocked(ctx: Context): Boolean = ctx.getSystemService(KeyguardManager::class.java)?.isDeviceLocked != false

    private fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)
}
