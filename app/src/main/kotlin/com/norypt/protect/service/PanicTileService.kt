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

    override fun onClick() {
        super.onClick()
        PanicHandler.panic(this, reason = "qs.tile")
    }
}
