package com.norypt.protect.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import com.norypt.protect.dpm.PowerMenuGuard

/**
 * Transparent / invisible activity whose sole purpose is to call
 * [startLockTask] so Device Owner Lock Task mode can suppress the stock
 * Power menu while the screen is locked.
 *
 * Launched by [PowerMenuGuard] on ACTION_SCREEN_OFF.
 * Finishes on the ACTION_STOP broadcast (fired by PowerMenuGuard on
 * ACTION_USER_PRESENT) or when the user unlocks.
 */
class PowerMenuBlockerActivity : Activity() {

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            runCatching { stopLockTask() }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(false)
        runCatching { startLockTask() }
        registerReceiver(
            stopReceiver,
            IntentFilter(PowerMenuGuard.ACTION_STOP),
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(stopReceiver) }
        super.onDestroy()
    }
}
