package com.norypt.protect.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.norypt.protect.R
import com.norypt.protect.ShortcutActivity
import com.norypt.protect.admin.Provisioning
import com.norypt.protect.wipe.WipeEngine

class PanicTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            // Unavailable where the platform would refuse the wipe anyway.
            state = if (canWipe()) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
            label = "Norypt Panic"
            icon = Icon.createWithResource(this@PanicTileService, R.mipmap.ic_launcher)
            updateTile()
        }
    }

    /**
     * Never wipes by itself. The tile only opens the wipe confirmation, the same App PIN prompt
     * the launcher shortcut shows ([ShortcutActivity]), locked or unlocked: on an unlocked phone
     * the shade is one swipe away for anyone holding it, so an unlock is no proof that the owner
     * wants a factory reset. Over the lockscreen the system asks for the unlock first (the prompt
     * cannot show over the lockscreen), then the App PIN prompt follows. Dry-run applies as for
     * every other trigger.
     */
    override fun onClick() {
        super.onClick()
        if (!canWipe()) return
        openWipeConfirmation()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // the Intent call is the Android 13 branch only
    private fun openWipeConfirmation() {
        val intent = Intent(this, ShortcutActivity::class.java)
            .putExtra(ShortcutActivity.EXTRA_ACTION, ShortcutActivity.ACTION_WIPE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            // Android 13: the PendingIntent overload does not exist yet. The Intent one only throws
            // for apps targeting 14+ when running on 14+, which this branch never does.
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun canWipe(): Boolean = WipeEngine.canFactoryReset(Provisioning.current(this))
}
