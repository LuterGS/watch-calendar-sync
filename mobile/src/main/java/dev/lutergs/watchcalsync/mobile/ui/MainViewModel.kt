package dev.lutergs.watchcalsync.mobile.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.lutergs.watchcalsync.mobile.calendar.CalendarReader
import dev.lutergs.watchcalsync.shared.model.CalendarEvent
import dev.lutergs.watchcalsync.shared.model.CalendarInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val loading: Boolean = false,
    val permissionGranted: Boolean = false,
    val calendars: List<CalendarInfo> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val lastLoadedAtMillis: Long? = null,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val reader = CalendarReader(app)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted) }
        if (granted) refresh()
    }

    fun refresh() {
        if (!_uiState.value.permissionGranted) return

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val calendars = reader.queryCalendars()
                val snapshot = reader.snapshot()

                // Step-1 verification: the full dump lands in `adb logcat -s WatchCalSync`.
                reader.logDiagnostics()

                _uiState.update {
                    it.copy(
                        loading = false,
                        calendars = calendars,
                        events = snapshot.events,
                        lastLoadedAtMillis = snapshot.generatedAtMillis,
                    )
                }
            } catch (e: SecurityException) {
                // Permission revoked between the check and the query.
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
}
