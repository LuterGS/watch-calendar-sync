package dev.lutergs.watchcalsync.mobile

import android.app.Application
import dev.lutergs.watchcalsync.mobile.sync.SyncScheduler

class WatchCalSyncApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Both schedules are unique-work, so re-running this on every process start
        // is idempotent — it re-arms after a reboot or an app update without
        // stacking duplicates.
        SyncScheduler.scheduleAll(this)
    }
}
