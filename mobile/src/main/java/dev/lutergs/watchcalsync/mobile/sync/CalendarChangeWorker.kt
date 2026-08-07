package dev.lutergs.watchcalsync.mobile.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.lutergs.watchcalsync.mobile.calendar.CalendarReader

/**
 * Runs when the calendar provider reports a change.
 *
 * This is the background half of "watch the calendar URI". A plain
 * [android.database.ContentObserver] only lives as long as the process that
 * registered it, so it stops working the moment the app is swapped out — which is
 * most of the time. A content-URI trigger is registered with the system instead,
 * so the job starts even from a cold process.
 *
 * Content-URI triggers only attach to one-time work, so the trigger has to be
 * re-armed after each firing. This worker deliberately does **not** do that
 * itself: re-arming means enqueuing the same unique work name with REPLACE, and
 * REPLACE cancels work that is currently running — which here is this very
 * worker. Doing it from doWork() cancelled the sync mid-flight every time
 * ("calendar change sync failed: Job was cancelled").
 *
 * Re-arming is [CalendarSyncWorker]'s job instead. It runs under a different
 * unique name so REPLACE is safe there, and its period bounds how long the
 * trigger can stay disarmed — a window the periodic sync itself already covers.
 */
class CalendarChangeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return when (val outcome = CalendarSyncer(applicationContext).sync()) {
            is SyncOutcome.Pushed -> {
                Log.i(TAG, "calendar changed: pushed ${outcome.eventCount} events")
                Result.success()
            }
            is SyncOutcome.Unchanged -> {
                // Most provider notifications do not change the agenda window at all.
                Log.d(TAG, "calendar changed: agenda unchanged, nothing sent")
                Result.success()
            }
            SyncOutcome.NoPermission -> Result.success()
            is SyncOutcome.Failed -> {
                Log.w(TAG, "calendar change sync failed: ${outcome.message}")
                Result.retry()
            }
        }
    }

    private companion object {
        const val TAG = CalendarReader.TAG
    }
}
