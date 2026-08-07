package dev.lutergs.watchcalsync.mobile.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.lutergs.watchcalsync.mobile.calendar.CalendarReader
import dev.lutergs.watchcalsync.mobile.calendar.CalendarSelectionStore
import dev.lutergs.watchcalsync.mobile.sync.PushResult
import dev.lutergs.watchcalsync.mobile.sync.WatchSyncClient
import dev.lutergs.watchcalsync.shared.model.CalendarEvent
import dev.lutergs.watchcalsync.shared.model.CalendarInfo
import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import dev.lutergs.watchcalsync.shared.sync.SnapshotCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    /** Calendars grouped by owning account, for the filter UI. */
    val calendarsByAccount: Map<String, List<CalendarInfo>>
        get() = calendars.groupBy { it.accountName }

    val eventCountByCalendarId: Map<Long, Int>
        get() = events.groupingBy { it.calendarId }.eachCount()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val reader = CalendarReader(app)
    private val selectionStore = CalendarSelectionStore(app)
    private val watchSync = WatchSyncClient(app)

    /** Most recent snapshot, kept so the manual sync button can re-push it. */
    private var lastSnapshot: CalendarSnapshot? = null

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted) }
        if (granted) refresh()
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
            requeryEvents(next)
        }
    }

    fun refresh() {
        if (!_uiState.value.permissionGranted) return

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val calendars = reader.queryCalendars()

                // A stored selection wins; otherwise fall back to the SYNC_EVENTS
                // default. Ids that no longer exist are dropped so a removed account
                // cannot leave the filter referencing dead calendars forever.
                val stored = selectionStore.enabledCalendarIds.first()
                val existing = calendars.map { it.id }.toSet()
                val enabled = stored?.intersect(existing)
                    ?: CalendarReader.defaultEnabledCalendarIds(calendars)

                _uiState.update { it.copy(calendars = calendars, enabledCalendarIds = enabled) }

                // Step-1 verification hook: `adb logcat -s WatchCalSync`.
                reader.logDiagnostics(calendars, enabled)

                requeryEvents(enabled, calendars)
            } catch (e: SecurityException) {
                Log.w(CalendarReader.TAG, "calendar read denied", e)
                _uiState.update {
                    it.copy(loading = false, permissionGranted = false, error = "권한이 거부되었습니다")
                }
            } catch (e: Exception) {
                Log.e(CalendarReader.TAG, "calendar read failed", e)
                _uiState.update {
                    it.copy(loading = false, error = e.message ?: e::class.java.simpleName)
                }
            }
        }
    }

    /** Manual "워치로 전송" — always writes, even if the agenda has not changed. */
    fun syncToWatchNow() {
        val snapshot = lastSnapshot ?: return
        viewModelScope.launch { pushToWatch(snapshot, force = true) }
    }

    private suspend fun pushToWatch(snapshot: CalendarSnapshot, force: Boolean) {
        _uiState.update { it.copy(syncing = true) }

        val result = watchSync.push(
            snapshot = snapshot,
            lastPushedHash = selectionStore.lastPushedHash(),
            force = force,
        )

        // Record the hash only on a real write, so a failed push retries next time.
        if (result is PushResult.Pushed) {
            selectionStore.setLastPushedHash(SnapshotCodec.contentHash(snapshot))
        }

        val nodes = watchSync.connectedNodeNames()
        _uiState.update {
            it.copy(
                syncing = false,
                connectedNodes = nodes,
                syncStatus = when (result) {
                    is PushResult.Pushed ->
                        "전송됨 · ${result.eventCount}건 / ${result.bytes}B"
                    is PushResult.Unchanged ->
                        "변경 없음 · ${result.eventCount}건 (전송 생략)"
                    is PushResult.Failed -> "전송 실패 · ${result.message}"
                },
            )
        }
    }

    private suspend fun requeryEvents(
        enabled: Set<Long>,
        calendars: List<CalendarInfo> = _uiState.value.calendars,
    ) {
        try {
            _uiState.update { it.copy(loading = true) }
            val snapshot = reader.snapshot(enabledCalendarIds = enabled, calendars = calendars)
            lastSnapshot = snapshot
            _uiState.update {
                it.copy(
                    loading = false,
                    events = snapshot.events,
                    lastLoadedAtMillis = snapshot.generatedAtMillis,
                    error = null,
                )
            }
            pushToWatch(snapshot, force = false)
        } catch (e: Exception) {
            Log.e(CalendarReader.TAG, "event query failed", e)
            _uiState.update {
                it.copy(loading = false, error = e.message ?: e::class.java.simpleName)
            }
        }
    }
}
