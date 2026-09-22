package com.norypt.protect.service

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import com.norypt.protect.dpm.PowerMenuGuard

/**
 * Transparent / invisible activity whose sole purpose is to call
 * [startLockTask] so Device Owner Lock Task mode can suppress the stock
 * Power menu while the screen is locked.
 *
 * Launched by [PowerMenuGuard] once the keyguard is up. Finishes on the ACTION_STOP
 * broadcast, or when the platform clears its task because the guard emptied the
 * lock-task allow-list.
 *
 * It runs in a task of its own (taskAffinity="" in the manifest). Sharing Norypt Protect's
 * task would put this invisible window on top of MainActivity and swallow every touch
 * whenever the launcher brought that task forward, and the allow-list clear that ends the
 * lock task would finish MainActivity along with it.
 */
class PowerMenuBlockerActivity : Activity() {

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            leave()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Shown over the lockscreen; keep it out of screenshots and recents.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        // Inert to touch: should it ever outlive the lock screen, whatever is underneath
        // keeps working instead of the phone appearing frozen.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        // DO NOT setShowWhenLocked(true) — that puts this invisible activity
        // on top of the keyguard and blocks the user from reaching the PIN
        // pad. Lock Task mode suppresses the power menu system-wide; we do
        // not need to be visible above the lockscreen for that effect.
        registerReceiver(
            stopReceiver,
            IntentFilter(PowerMenuGuard.ACTION_STOP),
            Context.RECEIVER_NOT_EXPORTED,
        )
        lockIfPermitted()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: a second engage reaches the existing instance here.
        lockIfPermitted()
    }

    private fun lockIfPermitted() {
        if (!PowerMenuGuard.isEnabled(this)) {
            leave()
            return
        }
        // If the guard released between the launch and this point, the allow-list is empty and
        // startLockTask() would not lock at all — it would pop the system's "pin this app?"
        // dialog on the lock screen instead.
        val dpm = getSystemService(DevicePolicyManager::class.java)
        if (dpm?.isLockTaskPermitted(packageName) != true) {
            finish()
            return
        }
        val am = getSystemService(ActivityManager::class.java)
        if (am?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_LOCKED) {
            runCatching { startLockTask() }
        }
    }

    private fun leave() {
        runCatching { stopLockTask() }
        finish()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(stopReceiver) }
        super.onDestroy()
    }
}
