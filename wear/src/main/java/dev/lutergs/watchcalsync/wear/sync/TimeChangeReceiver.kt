package dev.lutergs.watchcalsync.wear.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.wear.tiles.TileService
import dev.lutergs.watchcalsync.wear.complication.NextEventComplicationService
import dev.lutergs.watchcalsync.wear.tile.AgendaTileService

/** Rebuild local-date boundaries when the user changes the clock or time zone. */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIME_CHANGED && intent.action != Intent.ACTION_TIMEZONE_CHANGED) return
        NextEventComplicationService.requestUpdate(context)
        TileService.getUpdater(context).requestUpdate(AgendaTileService::class.java)
    }
}
