package dev.lutergs.watchcalsync.mobile.sync

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dev.lutergs.watchcalsync.mobile.calendar.CalendarReader
import dev.lutergs.watchcalsync.shared.sync.SyncContract
import kotlinx.coroutines.runBlocking

/**
 * Handles a "sync now" message from the watch.
 *
 * Without this the phone is the only side that can start a sync, so a watch whose
 * cache was wiped — reinstalled app, cleared storage — sits empty until the user
 * remembers to open the phone app. Wear starts this service on delivery even when
 * the phone app is not running.
 */
class SyncRequestListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != SyncContract.PATH_REQUEST_SYNC) return

        Log.i(TAG, "sync requested by watch (${messageEvent.sourceNodeId})")

        // Data Layer callbacks already run off the main thread, and the framework
        // tears the service down once this returns, so the work has to finish here.
        runBlocking {
            // Forced: the watch asked precisely because what it holds is wrong, so
            // the unchanged-content shortcut must not silently do nothing.
            when (val outcome = CalendarSyncer(applicationContext).sync(force = true)) {
                is SyncOutcome.Pushed ->
                    Log.i(TAG, "watch-requested sync: pushed ${outcome.eventCount} events")
                is SyncOutcome.Unchanged ->
                    Log.i(TAG, "watch-requested sync: ${outcome.eventCount} events")
                SyncOutcome.NoPermission ->
                    Log.w(TAG, "watch-requested sync: READ_CALENDAR not granted")
                is SyncOutcome.Failed ->
                    Log.w(TAG, "watch-requested sync failed: ${outcome.message}")
            }
        }
    }

    private companion object {
        const val TAG = CalendarReader.TAG
    }
}
