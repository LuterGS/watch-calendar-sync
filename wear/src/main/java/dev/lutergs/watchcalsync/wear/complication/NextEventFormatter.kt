package dev.lutergs.watchcalsync.wear.complication

import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import dev.lutergs.watchcalsync.wear.data.localEndMillis
import dev.lutergs.watchcalsync.wear.data.localStartMillis
import dev.lutergs.watchcalsync.wear.settings.EventMode
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Text for the next-event complication.
 *
 * A complication slot is a handful of characters, so this settles what gets cut:
 * the time survives, the title is what gets truncated by the watch face.
 */
data class NextEventText(
    /** A few characters — typically the time. */
    val shortText: String,
    val shortTitle: String?,
    val longText: String,
    val longTitle: String?,
    val description: String,
)

object NextEventFormatter {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA)
    private val dayFormatter = DateTimeFormatter.ofPattern("M/d", Locale.KOREA)

    fun next(
        snapshot: CalendarSnapshot?,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        mode: EventMode = EventMode.UPCOMING,
    ): NextEventText {
        val event = snapshot?.events
            ?.filter {
                when (mode) {
                    EventMode.UPCOMING -> !it.allDay && it.startMillis > nowMillis
                    EventMode.ALL_DAY -> it.allDay && it.localStartMillis(zone) <= nowMillis &&
                        it.localEndMillis(zone) > nowMillis
                }
            }
            ?.minWithOrNull(compareBy({ it.startMillis }, { it.title }))
            ?: return NextEventText(
                shortText = "—",
                shortTitle = null,
                longText = if (mode == EventMode.ALL_DAY) "오늘 종일 일정 없음" else "예정된 일정 없음",
                longTitle = null,
                description = if (mode == EventMode.ALL_DAY) "오늘 종일 일정 없음" else "예정된 일정 없음",
            )

        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        // All-day events sit on UTC midnight boundaries; resolving them locally
        // shifts them onto the wrong day.
        val startDate = Instant.ofEpochMilli(event.startMillis)
            .atZone(if (event.allDay) ZoneOffset.UTC else zone)
            .toLocalDate()

        val date = if (event.allDay) today else startDate

        val time = when {
            event.allDay -> "종일"
            date == today -> timeFormatter.format(Instant.ofEpochMilli(event.startMillis).atZone(zone))
            // Not today: the date matters more than the minute in a tiny slot.
            else -> dayFormatter.format(date)
        }

        return NextEventText(
            shortText = time,
            shortTitle = event.title,
            longText = "$time ${event.title}",
            longTitle = if (date == today) "오늘" else dayFormatter.format(date),
            description = "다음 일정: $time ${event.title}",
        )
    }
}
