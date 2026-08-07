package dev.lutergs.watchcalsync.wear.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.lutergs.watchcalsync.shared.model.CalendarEvent
import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot

@Composable
fun AgendaScreen(snapshot: CalendarSnapshot?) {
    val listState = rememberTransformingLazyColumnState()

    // Rotary (crown) scrolling is wired into TransformingLazyColumn by default —
    // passing the state to both ScreenScaffold and the column is all it takes.
    ScreenScaffold(scrollState = listState) { contentPadding ->
        if (snapshot == null) {
            EmptyState("폰에서 동기화를 기다리는 중")
            return@ScreenScaffold
        }

        val rows = remember(snapshot) { AgendaBuilder.build(snapshot) }
        LaunchedEffect(rows) {
            Log.i(
                "WatchCalSync",
                "agenda: ${rows.size} rows from ${snapshot.events.size} cached events",
            )
        }
        if (rows.isEmpty()) {
            EmptyState("예정된 일정이 없습니다")
            return@ScreenScaffold
        }

        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                rows,
                key = { it.key },
                // Without a contentType the list treats headers and events as one
                // kind and cannot reuse their very different layouts, so scrolling
                // rebuilds subtrees it could otherwise recycle.
                contentType = { it.contentType },
            ) { row ->
                when (row) {
                    is AgendaRow.DayHeader -> ListHeader { Text(row.label) }
                    is AgendaRow.Event -> EventRow(row)
                }
            }
        }
    }
}

// Modifiers are constant per row, so building them once at class-init keeps the
// scroll path from re-allocating a chain for every item on every frame.
private val RowModifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)
private val DotModifier = Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape)

@Composable
private fun EventRow(row: AgendaRow.Event) {
    Row(modifier = RowModifier, verticalAlignment = Alignment.Top) {
        // Calendar color is the only cue distinguishing 개인 from 회사 at a glance,
        // so it gets its own column rather than being folded into the text.
        Box(DotModifier.background(row.dotColor))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.location?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
