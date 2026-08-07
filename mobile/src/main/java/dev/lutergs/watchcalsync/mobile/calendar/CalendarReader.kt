package dev.lutergs.watchcalsync.mobile.calendar

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import dev.lutergs.watchcalsync.shared.model.CalendarEvent
import dev.lutergs.watchcalsync.shared.model.CalendarInfo
import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Reads the phone's calendar provider. Because the provider surfaces every account
 * registered on the device, this picks up the 회사 account alongside the 개인 one
 * with no extra sign-in — which is the entire point of the project.
 *
 * All queries require [android.Manifest.permission.READ_CALENDAR] to have been granted.
 */
class CalendarReader(private val context: Context) {

    /**
     * Lists every calendar the provider knows about. Also serves as the lookup table
     * for account attribution when reading instances — see [queryEvents].
     */
    suspend fun queryCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.SYNC_EVENTS,
        )

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            "${CalendarContract.Calendars.ACCOUNT_NAME} ASC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        CalendarInfo(
                            id = cursor.getLong(0),
                            displayName = cursor.getStringOrEmpty(1),
                            accountName = cursor.getStringOrEmpty(2),
                            accountType = cursor.getStringOrEmpty(3),
                            ownerAccount = cursor.getStringOrNull(4),
                            color = cursor.getInt(5),
                            visible = cursor.getInt(6) == 1,
                            synced = cursor.getInt(7) == 1,
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    /**
     * Queries occurrences in `[windowStartMillis, windowEndMillis)`.
     *
     * Uses `Instances` rather than `Events` deliberately: a recurring event has one
     * `Events` row whose DTSTART is the *first* occurrence, so querying `Events`
     * would miss every later repeat. `Instances` makes the provider expand the
     * recurrence rules for us.
     *
     * Account name is resolved from [calendars] rather than projected directly:
     * `Instances` implements only `EventsColumns` + `CalendarColumns`, so
     * `ACCOUNT_NAME` (a `SyncColumns` field) is not part of its documented surface.
     */
    suspend fun queryEvents(
        windowStartMillis: Long,
        windowEndMillis: Long,
        calendars: List<CalendarInfo>,
        enabledCalendarIds: Set<Long> = defaultEnabledCalendarIds(calendars),
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (enabledCalendarIds.isEmpty()) return@withContext emptyList()

        val byId = calendars.associateBy { it.id }

        // For Instances the time window is encoded in the URI path, not the selection.
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(windowStartMillis.toString())
            .appendPath(windowEndMillis.toString())
            .build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.EVENT_COLOR,
            CalendarContract.Instances.CALENDAR_COLOR,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        )

        // Restrict by calendar id rather than by Instances.VISIBLE.
        //
        // VISIBLE mirrors the checkbox in whichever Calendar app owns the account,
        // and on this phone every 회사(Google Workspace) calendar is unchecked even
        // though the provider is actively syncing 1000+ events into it. Filtering on
        // VISIBLE therefore drops the entire work account — the exact thing this app
        // exists to surface. Calendar choice belongs to *this* app instead.
        val idList = enabledCalendarIds.joinToString(",")
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN ($idList)"

        context.contentResolver.query(
            uri,
            projection,
            selection,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getIntOrNull(10) == CalendarContract.Instances.STATUS_CANCELED) {
                        continue
                    }
                    if (cursor.getIntOrNull(11) ==
                        CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
                    ) {
                        continue
                    }

                    val calendarId = cursor.getLong(6)
                    val calendar = byId[calendarId]

                    add(
                        CalendarEvent(
                            eventId = cursor.getLong(0),
                            title = cursor.getStringOrNull(1)?.takeIf { it.isNotBlank() }
                                ?: "(제목 없음)",
                            startMillis = cursor.getLong(2),
                            endMillis = cursor.getLong(3),
                            allDay = cursor.getInt(4) == 1,
                            location = cursor.getStringOrNull(5)?.takeIf { it.isNotBlank() },
                            calendarId = calendarId,
                            accountName = calendar?.accountName.orEmpty(),
                            calendarDisplayName = cursor.getStringOrNull(7)
                                ?: calendar?.displayName.orEmpty(),
                            // Per-event override wins, else the calendar's own color.
                            color = cursor.getIntOrNull(8)?.takeIf { it != 0 }
                                ?: cursor.getIntOrNull(9)?.takeIf { it != 0 }
                                ?: calendar?.color
                                ?: 0,
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    /** Convenience wrapper producing the payload the watch consumes. */
    suspend fun snapshot(
        now: Long = System.currentTimeMillis(),
        pastDays: Long = DEFAULT_PAST_DAYS,
        futureDays: Long = DEFAULT_FUTURE_DAYS,
        calendars: List<CalendarInfo>? = null,
        enabledCalendarIds: Set<Long>? = null,
    ): CalendarSnapshot {
        val start = now - TimeUnit.DAYS.toMillis(pastDays)
        val end = now + TimeUnit.DAYS.toMillis(futureDays)
        val cals = calendars ?: queryCalendars()
        return CalendarSnapshot(
            generatedAtMillis = now,
            windowStartMillis = start,
            windowEndMillis = end,
            events = queryEvents(
                windowStartMillis = start,
                windowEndMillis = end,
                calendars = cals,
                enabledCalendarIds = enabledCalendarIds ?: defaultEnabledCalendarIds(cals),
            ),
        )
    }

    /**
     * Dumps calendars and the current window to logcat. This is the step-1
     * verification hook: `adb logcat -s WatchCalSync`.
     */
    suspend fun logDiagnostics(
        calendars: List<CalendarInfo>? = null,
        enabledCalendarIds: Set<Long>? = null,
    ) {
        @Suppress("NAME_SHADOWING")
        val calendars = calendars ?: queryCalendars()
        val enabled = enabledCalendarIds ?: defaultEnabledCalendarIds(calendars)

        Log.i(TAG, "===== Calendars (${calendars.size}) =====")
        calendars.forEach {
            Log.i(
                TAG,
                "${if (it.id in enabled) "[ON ]" else "[off]"} id=${it.id} " +
                    "account=${it.accountName} (${it.accountType}) " +
                    "name='${it.displayName}' color=#${Integer.toHexString(it.color)} " +
                    "visible=${it.visible} synced=${it.synced}",
            )
        }
        Log.i(TAG, "distinct accounts = ${calendars.map { it.accountName }.distinct()}")

        val snap = snapshot(calendars = calendars, enabledCalendarIds = enabled)
        val byAccount = snap.events.groupingBy { it.accountName }.eachCount()
        Log.i(TAG, "===== Events (${snap.events.size}) in window, per account: $byAccount =====")
        snap.events.forEach {
            Log.i(
                TAG,
                "[${it.accountName}/${it.calendarDisplayName}] '${it.title}' " +
                    "${it.startMillis}..${it.endMillis} allDay=${it.allDay} loc=${it.location}",
            )
        }
        Log.i(TAG, "===== end =====")
    }

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun Cursor.getStringOrEmpty(index: Int): String =
        getStringOrNull(index).orEmpty()

    private fun Cursor.getIntOrNull(index: Int): Int? =
        if (isNull(index)) null else getInt(index)

    companion object {
        const val TAG = "WatchCalSync"

        /**
         * Which calendars to read when the user has not chosen explicitly.
         *
         * Keyed on SYNC_EVENTS, not VISIBLE: SYNC_EVENTS means the provider is
         * actually pulling data into the calendar, whereas VISIBLE only reflects a
         * checkbox in the phone's Calendar UI and is false for every 회사 calendar
         * on this device. Unsynced calendars are skipped because they hold no fresh
         * rows — including them would only add dead entries.
         */
        fun defaultEnabledCalendarIds(calendars: List<CalendarInfo>): Set<Long> =
            calendars.filter { it.synced }.map { it.id }.toSet()

        /** Yesterday onward — enough to still show an event that started before now. */
        const val DEFAULT_PAST_DAYS = 1L

        /** Two weeks ahead keeps the payload small while covering any glanceable agenda. */
        const val DEFAULT_FUTURE_DAYS = 14L
    }
}
