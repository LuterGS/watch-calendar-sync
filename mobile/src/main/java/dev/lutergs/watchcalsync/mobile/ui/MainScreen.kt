package dev.lutergs.watchcalsync.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lutergs.watchcalsync.shared.model.CalendarEvent
import dev.lutergs.watchcalsync.shared.model.CalendarInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onCalendarEnabledChange: (Long, Boolean) -> Unit,
    onCalendarsEnabledChange: (Set<Long>, Boolean) -> Unit,
    onSyncToWatch: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Watch Calendar Sync") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!state.permissionGranted) {
                PermissionPrompt(onRequestPermission)
                return@Column
            }

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("일정 ${state.events.size}") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("캘린더 ${state.enabledCalendarIds.size}/${state.calendars.size}") },
                )
            }

            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Box(Modifier.padding(horizontal = 16.dp)) {
                when (selectedTab) {
                    0 -> AgendaTab(state, onRefresh, onSyncToWatch)
                    else -> CalendarFilterTab(
                        state = state,
                        onCalendarEnabledChange = onCalendarEnabledChange,
                        onCalendarsEnabledChange = onCalendarsEnabledChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun AgendaTab(
    state: MainUiState,
    onRefresh: () -> Unit,
    onSyncToWatch: () -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("어제 ~ +14일", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRefresh, enabled = !state.loading) {
                        Text("새로고침")
                    }
                    Button(onClick = onSyncToWatch, enabled = !state.syncing) {
                        Text("워치로 전송")
                    }
                }
            }
        }

        item { WatchSyncStatus(state) }

        items(state.events, key = { it.occurrenceKey }) { EventRow(it) }

        if (state.events.isEmpty() && !state.loading) {
            item {
                Text(
                    "표시할 일정이 없습니다. '캘린더' 탭에서 동기화할 캘린더를 켜세요.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CalendarFilterTab(
    state: MainUiState,
    onCalendarEnabledChange: (Long, Boolean) -> Unit,
    onCalendarsEnabledChange: (Set<Long>, Boolean) -> Unit,
) {
    val counts = state.eventCountByCalendarId

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = "켜진 캘린더의 일정만 워치로 전송됩니다. 건수는 현재 조회 구간(어제~+14일) 기준입니다.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        state.calendarsByAccount.forEach { (account, calendars) ->
            item(key = "header-$account") {
                AccountHeader(
                    account = account,
                    calendars = calendars,
                    enabledIds = state.enabledCalendarIds,
                    onCalendarsEnabledChange = onCalendarsEnabledChange,
                )
            }
            items(calendars, key = { it.id }) { calendar ->
                CalendarToggleRow(
                    calendar = calendar,
                    enabled = calendar.id in state.enabledCalendarIds,
                    eventCount = counts[calendar.id] ?: 0,
                    onEnabledChange = { onCalendarEnabledChange(calendar.id, it) },
                )
            }
        }
    }
}

@Composable
private fun AccountHeader(
    account: String,
    calendars: List<CalendarInfo>,
    enabledIds: Set<Long>,
    onCalendarsEnabledChange: (Set<Long>, Boolean) -> Unit,
) {
    val allOn = calendars.all { it.id in enabledIds }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = account,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        TextButton(onClick = {
            onCalendarsEnabledChange(calendars.map { it.id }.toSet(), !allOn)
        }) {
            Text(if (allOn) "모두 끄기" else "모두 켜기")
        }
    }
}

@Composable
private fun CalendarToggleRow(
    calendar: CalendarInfo,
    enabled: Boolean,
    eventCount: Int,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEnabledChange(!enabled) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = enabled, onCheckedChange = onEnabledChange)
        ColorDot(calendar.color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(calendar.displayName, style = MaterialTheme.typography.bodyMedium)
            Text(
                // synced=false calendars hold no fresh rows, so flag them: turning
                // one on will not produce events.
                text = if (calendar.synced) "$eventCount 건" else "$eventCount 건 · 동기화 꺼짐",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun WatchSyncStatus(state: MainUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = state.syncStatus ?: "아직 전송하지 않음",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                // Zero nodes is normal when the watch is out of Bluetooth range; the
                // DataItem still syncs once it reconnects.
                text = if (state.connectedNodes.isEmpty()) {
                    "연결된 워치 없음 (재연결되면 자동 반영)"
                } else {
                    "연결됨: ${state.connectedNodes.joinToString()}"
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun PermissionPrompt(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("캘린더 읽기 권한이 필요합니다.")
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRequestPermission) { Text("권한 허용") }
    }
}

@Composable
private fun EventRow(event: CalendarEvent) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorDot(event.color)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(event.title, style = MaterialTheme.typography.bodyLarge)
                Text(formatRange(event), style = MaterialTheme.typography.bodySmall)
                Text(
                    text = buildString {
                        append(event.accountName)
                        append(" · ")
                        append(event.calendarDisplayName)
                        event.location?.let { append(" · 📍$it") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ColorDot(color: Int) {
    Box(
        Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(if (color == 0) Color.Gray else Color(color)),
    )
}

/**
 * All-day events are stored on UTC midnight boundaries, so they must be formatted
 * in UTC — using the local zone shifts them onto the wrong day.
 */
private fun formatRange(event: CalendarEvent): String {
    return if (event.allDay) {
        val fmt = SimpleDateFormat("M/d (E)", Locale.KOREA).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        "${fmt.format(Date(event.startMillis))} · 종일"
    } else {
        val dayFmt = SimpleDateFormat("M/d (E)", Locale.KOREA)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.KOREA)
        "${dayFmt.format(Date(event.startMillis))} " +
            "${timeFmt.format(Date(event.startMillis))}–${timeFmt.format(Date(event.endMillis))}"
    }
}
