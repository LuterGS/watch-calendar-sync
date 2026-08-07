package dev.lutergs.watchcalsync.wear.tile

import dev.lutergs.watchcalsync.shared.model.CalendarEvent
import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One line on the tile. Pre-formatted so the layout builder does no date maths. */
data class TileEntry(
    val whenLabel: String,
    val title: String,
    val colorArgb: Int,
)

data class TileAgenda(
    val entries: List<TileEntry>,
    /** Events inside the window that did not fit on the tile. */
    val overflowCount: Int,
    val hasSnapshot: Boolean,
)

object TileAgendaBuilder {

    /** A tile is one small screen — more than this is unreadable, not more useful. */
    const val MAX_ENTRIES = 4

    private val dayFormatter = DateTimeFormatter.ofPattern("M/d", Locale.KOREA)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA)

    /**
     * Events starting within the next 7 days, in order.
     *
     * The window is clamped to 7 days regardless of how far the phone's snapshot
     * reaches, because the tile is a "what's coming up" glance rather than a full
     * agenda — that is what the app screen is for.
     */
    fun build(
        snapshot: CalendarSnapshot?,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): TileAgenda {
        if (snapshot == null) return TileAgenda(emptyList(), 0, hasSnapshot = false)

        val horizon = nowMillis + TimeUnit.DAYS.toMillis(7)
        val upcoming = snapshot.events
            .filter { it.endMillis > nowMillis && it.startMillis < horizon }
            .sortedWith(compareBy({ it.startMillis }, { it.title }))

        val today = LocalDate.now(zone)
        val entries = upcoming.take(MAX_ENTRIES).map { event ->
            TileEntry(
                whenLabel = whenLabel(event, today, zone),
                title = event.title,
                colorArgb = event.color,
            )
        }

        return TileAgenda(
            entries = entries,
            overflowCount = (upcoming.size - entries.size).coerceAtLeast(0),
            hasSnapshot = true,
        )
    }

    /**
     * "오늘 14:00", "내일 종일", "8/12 09:30" — the date is dropped for today and
     * tomorrow because those are the ones a glance is usually about.
     */
    private fun whenLabel(event: CalendarEvent, today: LocalDate, zone: ZoneId): String {
        // All-day events sit on UTC midnight boundaries, so they must be resolved in
        // UTC or they land on the wrong day.
        val date = Instant.ofEpochMilli(event.startMillis)
            .atZone(if (event.allDay) ZoneOffset.UTC else zone)
            .toLocalDate()

        val dayPart = when (date) {
            today -> "오늘"
            today.plusDays(1) -> "내일"
            else -> dayFormatter.format(date)
        }

        val timePart = if (event.allDay) {
            "종일"
        } else {
            timeFormatter.format(Instant.ofEpochMilli(event.startMillis).atZone(zone))
        }
        return "$dayPart $timePart"
    }
}
