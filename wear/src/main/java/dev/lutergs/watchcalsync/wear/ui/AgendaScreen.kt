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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot

@Composable
fun AgendaScreen(
    snapshot: CalendarSnapshot?,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    val listState = rememberTransformingLazyColumnState()

    // The signature Wear OS "expressive" motion: items shrink and fade as they
    // approach the screen edge, so the round display reads as a bowl rather than a
    // clipped rectangle. This is what makes a list look like a stock Pixel Watch
    // app instead of a phone list squeezed onto a watch.
    val transformSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        // Generous top/bottom padding lets the first and last cards sit inside the
        // curve instead of being cut off by it.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 8.dp,
            vertical = 32.dp,
        ),
    ) { contentPadding ->
        if (snapshot == null) {
            EmptyState(
                message = "폰에서 동기화를 기다리는 중",
                refreshing = refreshing,
                onRefresh = onRefresh,
            )
            return@ScreenScaffold
        }

        val rows = remember(snapshot) { AgendaBuilder.build(snapshot) }
        LaunchedEffect(rows) {
            Log.i("WatchCalSync", "agenda: ${rows.size} rows from ${snapshot.events.size} events")
        }
        if (rows.isEmpty()) {
            EmptyState(
                message = "예정된 일정이 없습니다",
                refreshing = refreshing,
                onRefresh = onRefresh,
            )
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
                // kind and cannot reuse their very different layouts.
                contentType = { it.contentType },
            ) { row ->
                // Built once per item slot rather than per recomposition. Items
                // recompose on every scroll frame — that is how the morph tracks
                // scroll position — so allocating a Modifier chain and a
                // SurfaceTransformation inline meant two allocations per visible
                // item per frame, on the UI thread that the jank was traced to.
                val itemModifier = remember(transformSpec) {
                    Modifier.transformedHeight(this, transformSpec)
                }
                val transformation = remember(transformSpec) {
                    SurfaceTransformation(transformSpec)
                }

                when (row) {
                    is AgendaRow.DayHeader ->
                        ListHeader(
                            modifier = itemModifier,
                            transformation = transformation,
                        ) {
                            Text(row.label)
                        }

                    is AgendaRow.AllDay ->
                        AllDayCard(row, itemModifier, transformation)

                    is AgendaRow.Event ->
                        EventCard(row, itemModifier, transformation)
                }
            }

            item(key = "refresh", contentType = "refresh") {
                RefreshEdgeButton(refreshing = refreshing, onRefresh = onRefresh)
            }
        }
    }
}

// Constant across every row, so building them once at class-init keeps the scroll
// path from rebuilding identical Modifier chains each frame.
private val CardModifier = Modifier.fillMaxWidth()
private val DotModifier = Modifier.size(8.dp).clip(CircleShape)

@Composable
private fun AllDayCard(
    row: AgendaRow.AllDay,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    Card(
        onClick = {},
        modifier = modifier.then(CardModifier),
        transformation = transformation,
        colors = CardDefaults.cardColors(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // One dot per contributing calendar, so a merged row still shows it
            // came from more than one place.
            row.dotColors.forEach { color ->
                Box(DotModifier.background(color))
                Spacer(Modifier.width(3.dp))
            }
            Spacer(Modifier.width(3.dp))
            Text(
                text = "종일",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primaryDim,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(2.dp))

        Text(
            text = row.titles,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EventCard(
    row: AgendaRow.Event,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    Card(
        onClick = {},
        modifier = modifier.then(CardModifier),
        transformation = transformation,
        colors = CardDefaults.cardColors(),
    ) {
        // Time first, then title — the same reading order Google Calendar uses on
        // Wear, because the time is what you are actually glancing for.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(DotModifier.background(row.dotColor))
            Spacer(Modifier.width(6.dp))
            Text(
                text = row.timeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primaryDim,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(2.dp))

        Text(
            text = row.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        row.location?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyExtraSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RefreshEdgeButton(refreshing: Boolean, onRefresh: () -> Unit) {
    EdgeButton(onClick = onRefresh, enabled = !refreshing) {
        Text(if (refreshing) "요청 중…" else "새로고침")
    }
}

@Composable
private fun EmptyState(
    message: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRefresh, enabled = !refreshing) {
            Text(if (refreshing) "요청 중…" else "새로고침")
        }
    }
}
