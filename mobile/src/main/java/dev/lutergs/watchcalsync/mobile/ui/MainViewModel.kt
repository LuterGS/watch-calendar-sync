package dev.lutergs.watchcalsync.mobile.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.lutergs.watchcalsync.mobile.calendar.CalendarChangeObserver
import dev.lutergs.watchcalsync.mobile.calendar.CalendarReader
import dev.lutergs.watchcalsync.mobile.calendar.CalendarSelectionStore
import dev.lutergs.watchcalsync.mobile.sync.CalendarSyncer
import dev.lutergs.watchcalsync.mobile.sync.SyncOutcome
import dev.lutergs.watchcalsync.mobile.sync.SyncScheduler
import dev.lutergs.watchcalsync.mobile.sync.WatchSyncClient
import dev.lutergs.watchcalsync.shared.model.CalendarEvent
import dev.lutergs.watchcalsync.shared.model.CalendarInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [Immutable] because Compose treats `List<T>` as unstable — the interface admits
 * mutable implementations — even though every list here is built once and never
 * modified. Without it the whole screen re-composes on any state read.
 */
@Immutable
data class MainUiState(
    val loading: Boolean = false,
    val permissionGranted: Boolean = false,
    val calendars: List<CalendarInfo> = emptyList(),
    /** Which calendars feed the agenda. Never null once [calendars] is populated. */
    val enabledCalendarIds: Set<Long> = emptySet(),
    val events: List<CalendarEvent> = emptyList(),
    val lastLoadedAtMillis: Long? = null,
    val error: String? = null,
    /** Human-readable outcome of the most recent watch push. */
    val syncStatus: String? = null,
    val connectedNodes: List<String> = emptyList(),
    val syncing: Boolean = false,
) {
    // `by lazy`, not `get()`: these are read from composition, and a getter would
    // re-group all 23 calendars and 48 events on every recomposition.
    /** Calendars grouped by owning account, for the filter UI. */
    val calendarsByAccount: Map<String, List<CalendarInfo>> by lazy {
        calendars.groupBy { it.accountName }
    }

    val eventCountByCalendarId: Map<Long, Int> by lazy {
        events.groupingBy { it.calendarId }.eachCount()
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val reader = CalendarReader(app)
    private val selectionStore = CalendarSelectionStore(app)
    private val syncer = CalendarSyncer(app)
    private val watchSync = WatchSyncClient(app)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /**
     * Foreground change detection. The WorkManager trigger handles the background,
     * but while the user is looking at this screen they should not have to wait for
     * a scheduler window to see an edit they just made.
     */
    private val changeObserver = CalendarChangeObserver(app, viewModelScope) {
        Log.i(CalendarReader.TAG, "resyncing after calendar change")
        refresh()
    }

    override fun onCleared() {
        changeObserver.stop()
        super.onCleared()
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted) }
        if (granted) {
            changeObserver.start()
            refresh()
        } else {
            changeObserver.stop()
        }
    }

    fun setCalendarEnabled(calendarId: Long, enabled: Boolean) =
        setCalendarsEnabled(setOf(calendarId), enabled)

    /**
     * Toggles a whole group at once. The bulk form matters for the per-account
     * "모두 켜기/끄기" action: toggling one id at a time would issue one DataStore
     * write and one full requery per calendar.
     */
    fun setCalendarsEnabled(calendarIds: Set<Long>, enabled: Boolean) {
        val current = _uiState.value.enabledCalendarIds
        val next = if (enabled) current + calendarIds else current - calendarIds
        if (next == current) return

        // Optimistic: reflect the toggle immediately, then persist and requery.
        _uiState.update { it.copy(enabledCalendarIds = next) }
        viewModelScope.launch {
            selectionStore.setEnabled(next)
            refresh()
        }
    }

    /** Manual "워치로 전송" — always writes, even if the agenda has not changed. */
    fun syncToWatchNow() {
        viewModelScope.launch { runSync(force = true) }
    }

    fun refresh() {
        if (!_uiState.value.permissionGranted) return
        viewModelScope.launch { runSync(force = false) }
    }

    private suspend fun runSync(force: Boolean) {
        // The change trigger is one-time work and is consumed when it fires, so
        // re-arm on any user-driven sync too. Otherwise it could sit disarmed until
        // the next periodic run even while the app is being actively used.
        SyncScheduler.rearmCalendarChangeTrigger(getApplication())

        _uiState.update { it.copy(loading = true, syncing = true, error = null) }
        try {
            val calendars = reader.queryCalendars()
            val stored = selectionStore.enabledCalendarIds()
            val existing = calendars.map { it.id }.toSet()
            val enabled = stored?.intersect(existing)
                ?: CalendarReader.defaultEnabledCalendarIds(calendars)

            // Step-1 verification hook: `adb logcat -s WatchCalSync`.
            reader.logDiagnostics(calendars, enabled)

            val snapshot = reader.snapshot(calendars = calendars, enabledCalendarIds = enabled)
            _uiState.update {
                it.copy(
                    loading = false,
                    calendars = calendars,
                    enabledCalendarIds = enabled,
                    events = snapshot.events,
                    lastLoadedAtMillis = snapshot.generatedAtMillis,
                )
            }

            // Push through the shared syncer so the UI, the periodic worker and the
            // change worker all apply the same skip rules and hash bookkeeping.
            val outcome = syncer.sync(force = force)
            val nodes = watchSync.connectedNodeNames()
            _uiState.update {
                it.copy(
                    syncing = false,
                    connectedNodes = nodes,
                    syncStatus = when (outcome) {
                        is SyncOutcome.Pushed -> "전송됨 · ${outcome.eventCount}건 / ${outcome.bytes}B"
                        is SyncOutcome.Unchanged -> "변경 없음 · ${outcome.eventCount}건 (전송 생략)"
                        SyncOutcome.NoPermission -> "권한 없음"
                        is SyncOutcome.Failed -> "전송 실패 · ${outcome.message}"
                    },
                )
            }
        } catch (e: SecurityException) {
            Log.w(CalendarReader.TAG, "calendar read denied", e)
            _uiState.update {
                it.copy(
                    loading = false,
                    syncing = false,
                    permissionGranted = false,
                    error = "권한이 거부되었습니다",
                )
            }
        } catch (e: Exception) {
            Log.e(CalendarReader.TAG, "sync failed", e)
            _uiState.update {
                it.copy(
                    loading = false,
                    syncing = false,
                    error = e.message ?: e::class.java.simpleName,
                )
            }
        }
    }
}
