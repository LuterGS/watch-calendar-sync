package dev.lutergs.watchcalsync.mobile.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Watch Calendar Sync") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            when {
                !state.permissionGranted -> PermissionPrompt(onRequestPermission)

                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "캘린더 ${state.calendars.size}개 · 일정 ${state.events.size}건",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = onRefresh, enabled = !state.loading) {
                            Text("새로고침")
                        }
                    }

                    state.error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (state.loading) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { SectionHeader("발견된 캘린더") }
                        items(state.calendars, key = { it.id }) { CalendarRow(it) }

                        item {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            SectionHeader("일정 (어제 ~ +14일)")
                        }
                        items(state.events, key = { it.occurrenceKey }) { EventRow(it) }

                        if (state.events.isEmpty() && !state.loading) {
                            item {
                                Text(
                                    "표시할 일정이 없습니다.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
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
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun CalendarRow(calendar: CalendarInfo) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorDot(calendar.color)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(calendar.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${calendar.accountName} · ${calendar.accountType}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "visible=${calendar.visible} synced=${calendar.synced}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
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
