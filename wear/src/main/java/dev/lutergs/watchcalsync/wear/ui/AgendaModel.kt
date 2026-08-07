package dev.lutergs.watchcalsync.wear.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.lutergs.watchcalsync.shared.model.CalendarEvent
import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A row in the watch agenda, pre-rendered into exactly the values the UI draws.
 *
 * Formatting and color resolution happen here, once per sync, rather than inside
 * the item composable where they would run on every scroll frame. Holding only
 * primitives, Strings and [Color] also keeps these types trivially stable, so the
 * lazy list can skip re-composing rows that have not changed.
 */
@Immutable
sealed interface AgendaRow {
    val key: String
    val contentType: String

    @Immutable
    data class DayHeader(val label: String) : AgendaRow {
        override val key: String get() = "day-$label"
        override val contentType: String get() = "header"
    }

    @Immutable
    data class Event(
        private val occurrenceKey: String,
        val title: String,
        val timeLabel: String,
        val location: String?,
        val dotColor: Color,
    ) : AgendaRow {
        override val key: String get() = "ev-$occurrenceKey"
        override val contentType: String get() = "event"
    }
}

object AgendaBuilder {

    private val dayFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREA)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA)

    /**
     * Flattens a snapshot into day-grouped rows.
     *
     * Events that already ended are dropped: the phone deliberately queries from
     * yesterday so an in-progress event is not lost, but on a glanceable watch
     * screen finished events are pure noise. Filtering on *end* rather than start
     * is what keeps the currently-running meeting at the top.
     */
    fun build(
        snapshot: CalendarSnapshot,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<AgendaRow> {
        val upcoming = snapshot.events
            .filter { it.endMillis > nowMillis }
            .sortedWith(compareBy({ it.startMillis }, { it.title }))

        val rows = ArrayList<AgendaRow>(upcoming.size + 8)
        var lastDate: LocalDate? = null

        for (event in upcoming) {
            val date = dateOf(event, zone)
            if (date != lastDate) {
                rows += AgendaRow.DayHeader(dayFormatter.format(date))
                lastDate = date
            }
            rows += AgendaRow.Event(
                occurrenceKey = event.occurrenceKey,
                title = event.title,
                timeLabel = timeLabel(event, zone),
                location = event.location,
                dotColor = if (event.color == 0) Color.Gray else Color(event.color),
            )
        }
        return rows
    }

    /**
     * All-day events are stored on UTC midnight boundaries rather than local time,
     * so resolving them in the local zone shifts them onto the wrong day — the
     * classic off-by-one that puts a 종일 event on the day before.
     */
    fun dateOf(event: CalendarEvent, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(event.startMillis)
            .atZone(if (event.allDay) ZoneOffset.UTC else zone)
            .toLocalDate()

    private fun timeLabel(event: CalendarEvent, zone: ZoneId): String {
        if (event.allDay) return "종일"
        val fmt = timeFormatter.withZone(zone)
        return "${fmt.format(Instant.ofEpochMilli(event.startMillis))}" +
            "–${fmt.format(Instant.ofEpochMilli(event.endMillis))}"
    }
}
