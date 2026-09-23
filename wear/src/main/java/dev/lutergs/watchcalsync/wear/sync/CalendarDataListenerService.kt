package dev.lutergs.watchcalsync.wear.sync

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import androidx.wear.tiles.TileService
import dev.lutergs.watchcalsync.shared.sync.SnapshotCodec
import dev.lutergs.watchcalsync.shared.sync.SyncContract
import dev.lutergs.watchcalsync.wear.complication.NextEventComplicationService
import dev.lutergs.watchcalsync.wear.data.SnapshotStore
import dev.lutergs.watchcalsync.wear.tile.AgendaTileService
import kotlinx.coroutines.runBlocking

/**
 * Receives calendar snapshots pushed by the phone.
 *
 * Wear starts this service on delivery even when the app is not running, so the
 * cache stays warm and the agenda is populated the moment the user opens the app.
 */
class CalendarDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // The buffer is released as soon as this returns, so it cannot be handed to
        // a background coroutine — the work has to finish here. Data Layer callbacks
        // already run on a background thread, so blocking is safe.
        dataEvents.use { buffer ->
            for (event in buffer) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                if (event.dataItem.uri.path != SyncContract.PATH_SNAPSHOT) continue

                runCatching {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap

                    val version = map.getInt(SyncContract.KEY_SCHEMA_VERSION, -1)
                    if (version != SyncContract.SCHEMA_VERSION) {
                        // Phone is on a newer payload shape than this build understands.
                        // Ignoring beats crashing or caching something unparseable.
                        Log.w(TAG, "ignoring snapshot: schema $version != ${SyncContract.SCHEMA_VERSION}")
                        return@runCatching
                    }

                    val gzipped = map.getByteArray(SyncContract.KEY_SNAPSHOT_GZIP)
                    if (gzipped == null) {
                        Log.w(TAG, "ignoring snapshot: payload missing")
                        return@runCatching
                    }

                    val snapshot = SnapshotCodec.decode(gzipped)
                    Log.i(
                        TAG,
                        "received ${snapshot.events.size} events " +
                            "(${gzipped.size}B gzip, generatedAt=${snapshot.generatedAtMillis})",
                    )

                    runBlocking { SnapshotStore.get(applicationContext).save(snapshot) }

                    // Replace both cached surfaces, including the complication timeline.
                    TileService.getUpdater(applicationContext)
                        .requestUpdate(AgendaTileService::class.java)
                    NextEventComplicationService.requestUpdate(applicationContext)
                }.onFailure { Log.e(TAG, "failed to handle snapshot", it) }
            }
        }
    }

    private companion object {
        const val TAG = "WatchCalSync"
    }
}
