package dev.lutergs.watchcalsync.wear.tile

import dev.lutergs.watchcalsync.shared.model.CalendarEvent
import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One day on the tile.
 *
 * The tile is day-oriented rather than event-oriented on purpose: listing raw
 * events meant a single busy day filled the whole tile, so a "next 7 days" glance
 * only ever showed today. A row per day guarantees the week is actually visible.
 */
data class TileDay(
    val dayLabel: String,
    /** Earliest event of the day — what the glance is usually about. */
    val headline: String,
    val timeLabel: String,
    /** Remaining events that day, for the "+N" suffix. */
    val moreCount: Int,
    val colorArgb: Int,
)

data class TileAgenda(
    val days: List<TileDay>,
    /** Days with events inside the window that did not fit on the tile. */
    val overflowDays: Int,
    val hasSnapshot: Boolean,
)

object TileAgendaBuilder {

    /** How far ahead the glance looks. */
    const val HORIZON_DAYS = 7L

    /** A tile is one small screen; beyond this rows stop being readable. */
    const val MAX_DAYS = 4

    private val dayFormatter = DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREA)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA)

    /**
     * Groups upcoming events into one row per day, covering [HORIZON_DAYS] days.
     * Days with no events are skipped rather than rendered empty.
     */
    fun build(
        snapshot: CalendarSnapshot?,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): TileAgenda {
        if (snapshot == null) return TileAgenda(emptyList(), 0, hasSnapshot = false)

        val today = LocalDate.now(zone)
        val lastDay = today.plusDays(HORIZON_DAYS)

        val byDay = snapshot.events
            .filter { it.endMillis > nowMillis }
            .map { dateOf(it, zone) to it }
            .filter { (date, _) -> !date.isBefore(today) && !date.isAfter(lastDay) }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()

        val days = byDay.entries.take(MAX_DAYS).map { (date, events) ->
            val sorted = events.sortedWith(compareBy({ it.startMillis }, { it.title }))
            val first = sorted.first()
            TileDay(
                dayLabel = when (date) {
                    today -> "오늘"
                    today.plusDays(1) -> "내일"
                    else -> dayFormatter.format(date)
                },
                headline = first.title,
                timeLabel = if (first.allDay) {
                    "종일"
                } else {
                    timeFormatter.format(Instant.ofEpochMilli(first.startMillis).atZone(zone))
                },
                moreCount = sorted.size - 1,
                colorArgb = first.color,
            )
        }

        return TileAgenda(
            days = days,
            overflowDays = (byDay.size - days.size).coerceAtLeast(0),
            hasSnapshot = true,
        )
    }

    /**
     * All-day events sit on UTC midnight boundaries rather than local time, so
     * resolving them in the local zone shifts them onto the wrong day.
     */
    private fun dateOf(event: CalendarEvent, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(event.startMillis)
            .atZone(if (event.allDay) ZoneOffset.UTC else zone)
            .toLocalDate()
}
