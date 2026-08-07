package dev.lutergs.watchcalsync.mobile.sync

import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private const val PERIODIC_WORK = "calendar-sync-periodic"
    private const val CHANGE_WORK = "calendar-sync-on-change"

    /** Twice the 15-minute floor: the change trigger carries the urgent cases. */
    private const val PERIOD_MINUTES = 30L

    fun scheduleAll(context: Context) {
        schedulePeriodic(context)
        rearmCalendarChangeTrigger(context)
    }

    private fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
            PERIOD_MINUTES, TimeUnit.MINUTES,
        ).setConstraints(
            // No network constraint — this syncs over Bluetooth to a paired watch,
            // so requiring connectivity would strand it exactly when offline sync is
            // the point.
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            // KEEP, so re-opening the app does not reset the interval and starve the job.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * (Re-)registers the calendar-change trigger.
     *
     * Content-URI triggers are only supported on one-time work, so this has to be
     * re-armed after every run — [CalendarChangeWorker] calls back into it.
     * REPLACE is deliberate: a stale pending copy must not accumulate.
     */
    fun rearmCalendarChangeTrigger(context: Context) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
            // The provider fires a burst of notifications for a single edit, and
            // again while an account sync writes rows. Collapsing them avoids
            // waking the radio once per row.
            .setTriggerContentUpdateDelay(30, TimeUnit.SECONDS)
            // ...but never sit on a change for longer than this.
            .setTriggerContentMaxDelay(5, TimeUnit.MINUTES)
            .build()

        val request = OneTimeWorkRequestBuilder<CalendarChangeWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            CHANGE_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
