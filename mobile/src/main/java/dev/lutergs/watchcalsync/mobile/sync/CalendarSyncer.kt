package dev.lutergs.watchcalsync.mobile.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import dev.lutergs.watchcalsync.mobile.calendar.CalendarReader
import dev.lutergs.watchcalsync.mobile.calendar.CalendarSelectionStore
import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import dev.lutergs.watchcalsync.shared.sync.SnapshotCodec

sealed interface SyncOutcome {
    data class Pushed(val eventCount: Int, val bytes: Int) : SyncOutcome
    data class Unchanged(val eventCount: Int) : SyncOutcome
    data object NoPermission : SyncOutcome
    data class Failed(val message: String) : SyncOutcome
}

/**
 * Reads the calendar and pushes it to the watch.
 *
 * The single place that whole path lives, because it is driven from three
 * unrelated entry points — the UI, the periodic worker, and the calendar-change
 * worker — and they must not each grow their own subtly different version of
 * "which calendars, which window, when to skip".
 */
class CalendarSyncer(private val context: Context) {

    private val reader = CalendarReader(context)
    private val selectionStore = CalendarSelectionStore(context)
    private val watchSync = WatchSyncClient(context)

    suspend fun sync(force: Boolean = false): SyncOutcome {
        if (!hasPermission()) {
            // Background work can outlive the grant; failing quietly here is right,
            // the UI is what asks for it back.
            Log.w(TAG, "sync skipped: READ_CALENDAR not granted")
            return SyncOutcome.NoPermission
        }

        return try {
            val snapshot = buildSnapshot()
            val result = watchSync.push(
                snapshot = snapshot,
                lastPushedHash = selectionStore.lastPushedHash(),
                force = force,
            )

            when (result) {
                is PushResult.Pushed -> {
                    // Recorded only on a real write, so a failed push retries.
                    selectionStore.setLastPushedHash(SnapshotCodec.contentHash(snapshot))
                    SyncOutcome.Pushed(result.eventCount, result.bytes)
                }
                is PushResult.Unchanged -> SyncOutcome.Unchanged(result.eventCount)
                is PushResult.Failed -> SyncOutcome.Failed(result.message)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "sync denied", e)
            SyncOutcome.NoPermission
        } catch (e: Exception) {
            Log.e(TAG, "sync failed", e)
            SyncOutcome.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    /** Snapshot honouring the user's calendar selection. */
    suspend fun buildSnapshot(): CalendarSnapshot {
        val calendars = reader.queryCalendars()
        val stored = selectionStore.enabledCalendarIds()
        val existing = calendars.map { it.id }.toSet()
        val enabled = stored?.intersect(existing)
            ?: CalendarReader.defaultEnabledCalendarIds(calendars)

        return reader.snapshot(calendars = calendars, enabledCalendarIds = enabled)
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = CalendarReader.TAG
    }
}
