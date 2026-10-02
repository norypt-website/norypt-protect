package com.norypt.protect

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.norypt.protect.admin.Provisioning
import com.norypt.protect.admin.Tier
import com.norypt.protect.panic.PanicHandler
import com.norypt.protect.security.AppPin
import com.norypt.protect.security.SelfVerification
import com.norypt.protect.ui.components.PinEntryDialog
import com.norypt.protect.ui.theme.NoryptProtectTheme
import com.norypt.protect.wipe.WipeEngine

/**
 * Runs the launcher shortcuts, lock and wipe.
 *
 * Not exported: the launcher starts a shortcut as the app that published it, so it can reach
 * this activity and no other app can. The actions used to arrive as an extra on the exported
 * main activity, which let any app lock the phone after every unlock or raise the wipe PIN
 * prompt whenever it chose.
 */
class ShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SelfVerification.isTrustedCert(this)) {
            finishAndRemoveTask()
            return
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        when (intent?.getStringExtra(EXTRA_ACTION)) {
            ACTION_LOCK -> {
                if (Provisioning.current(this) >= Tier.DeviceAdmin) {
                    getSystemService(DevicePolicyManager::class.java).lockNow()
                }
                finish()
            }
            ACTION_WIPE -> confirmWipe()
            else -> finish()
        }
    }

    private fun confirmWipe() {
        // No PIN set yet, or a phone this app cannot wipe: the app explains either case.
        if (!AppPin.isSet(this) || !WipeEngine.canFactoryReset(Provisioning.current(this))) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        setContent {
            NoryptProtectTheme {
                PinEntryDialog(
                    title = "Confirm wipe",
                    onVerified = {
                        PanicHandler.panic(this, "shortcut.wipe")
                        finish()
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val ACTION_LOCK = "lock"
        const val ACTION_WIPE = "wipe"
    }
}
