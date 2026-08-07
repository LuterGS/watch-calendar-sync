package dev.lutergs.watchcalsync.mobile.calendar

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watches the calendar provider while the app is in the foreground.
 *
 * This is the *fast* path only. The durable one is the WorkManager content-URI
 * trigger, which survives process death; a ContentObserver dies with the process
 * that registered it. Keeping this as well means an edit made in the Calendar app
 * and a switch back here shows up immediately rather than after a scheduler delay.
 */
class CalendarChangeObserver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onChanged: suspend () -> Unit,
) {

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            // A single edit produces a burst of notifications, and an account sync
            // produces one per written row; without debouncing each would kick off a
            // full re-read and a Bluetooth write.
            pending?.cancel()
            pending = scope.launch {
                delay(DEBOUNCE_MILLIS)
                Log.i(TAG, "calendar changed (foreground observer)")
                onChanged()
            }
        }
    }

    private var pending: Job? = null
    private var registered = false

    fun start() {
        if (registered) return
        context.contentResolver.registerContentObserver(
            CalendarContract.CONTENT_URI,
            /* notifyForDescendants = */ true,
            observer,
        )
        registered = true
    }

    fun stop() {
        if (!registered) return
        context.contentResolver.unregisterContentObserver(observer)
        pending?.cancel()
        registered = false
    }

    private companion object {
        const val TAG = CalendarReader.TAG
        const val DEBOUNCE_MILLIS = 1_500L
    }
}
