package dev.lutergs.watchcalsync.mobile.calendar

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.calendarSelectionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "calendar_selection",
)

/**
 * Persists which calendars the user wants synced to the watch.
 *
 * The stored value is deliberately distinct from "no calendars selected": an absent
 * key means the user has never opened the filter, in which case
 * [CalendarReader.defaultEnabledCalendarIds] decides. Storing an empty set means the
 * user really did turn everything off, and that choice is honoured.
 */
class CalendarSelectionStore(private val context: Context) {

    /** Emits null until the user makes an explicit choice. */
    val enabledCalendarIds: Flow<Set<Long>?> =
        context.calendarSelectionDataStore.data.map { prefs ->
            prefs[KEY_ENABLED]?.mapNotNull { it.toLongOrNull() }?.toSet()
        }

    suspend fun setEnabled(ids: Set<Long>) {
        context.calendarSelectionDataStore.edit { prefs ->
            prefs[KEY_ENABLED] = ids.map { it.toString() }.toSet()
        }
    }

    /**
     * Content hash of the snapshot last pushed to the watch, so a restart does not
     * re-send an agenda the watch already has.
     */
    suspend fun lastPushedHash(): Int? =
        context.calendarSelectionDataStore.data.map { it[KEY_LAST_PUSHED_HASH] }.first()

    suspend fun setLastPushedHash(hash: Int) {
        context.calendarSelectionDataStore.edit { it[KEY_LAST_PUSHED_HASH] = hash }
    }

    private companion object {
        // Preferences has no Long-set type, so ids round-trip as strings.
        val KEY_ENABLED = stringSetPreferencesKey("enabled_calendar_ids")
        val KEY_LAST_PUSHED_HASH = intPreferencesKey("last_pushed_hash")
    }
}
