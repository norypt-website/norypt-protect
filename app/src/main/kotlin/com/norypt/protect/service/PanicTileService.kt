package com.norypt.protect.service

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.norypt.protect.R
import com.norypt.protect.panic.PanicHandler

class PanicTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "Norypt Panic"
            icon = Icon.createWithResource(this@PanicTileService, R.mipmap.ic_launcher)
            updateTile()
        }
    }

    /**
     * The quick-settings shade opens over the lockscreen, so without this a single stray
     * tap — by a thief, or by the owner's own thumb pulling the shade down — factory-resets
     * the device with no confirmation. Deferring behind an unlock keeps the tile a
     * one-gesture panic action for the owner while requiring proof of presence, which is
     * the same bar the launcher shortcut meets with its PIN dialog.
     */
    override fun onClick() {
        super.onClick()
        unlockAndRun { PanicHandler.panic(this, reason = "qs.tile") }
    }
}
