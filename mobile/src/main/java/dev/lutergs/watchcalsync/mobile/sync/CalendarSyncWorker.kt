package dev.lutergs.watchcalsync.mobile.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.lutergs.watchcalsync.mobile.calendar.CalendarReader

/**
 * Periodic backstop sync.
 *
 * The calendar-change trigger ([CalendarChangeWorker]) catches edits, but it can
 * miss them: the provider does not always notify for server-side changes pulled
 * in by a background account sync, and the system can defer content jobs. Running
 * on a timer as well means the watch is never more than one period stale.
 */
class CalendarSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Re-arms the calendar-change trigger, which is one-time work and so is
        // consumed each time it fires. It cannot re-arm itself — REPLACE on its own
        // unique name would cancel the very run doing the re-arming — so this
        // worker, running under a different name, owns that. Worst case the trigger
        // is disarmed for one period, and this sync covers exactly that window.
        SyncScheduler.rearmCalendarChangeTrigger(applicationContext)

        return when (val outcome = CalendarSyncer(applicationContext).sync()) {
            is SyncOutcome.Pushed -> {
                Log.i(TAG, "periodic sync: pushed ${outcome.eventCount} events")
                Result.success()
            }
            is SyncOutcome.Unchanged -> {
                // The common case, and worth logging: without it a healthy periodic
                // run is indistinguishable in logcat from one that never fired.
                Log.i(TAG, "periodic sync: ${outcome.eventCount} events, unchanged")
                Result.success()
            }
            // Nothing to retry against — the UI is what re-requests permission.
            SyncOutcome.NoPermission -> Result.success()
            is SyncOutcome.Failed -> {
                Log.w(TAG, "periodic sync failed: ${outcome.message}")
                Result.retry()
            }
        }
    }

    private companion object {
        const val TAG = CalendarReader.TAG
    }
}
