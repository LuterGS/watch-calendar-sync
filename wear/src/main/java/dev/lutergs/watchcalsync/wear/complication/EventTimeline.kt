package dev.lutergs.watchcalsync.wear.complication

import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import dev.lutergs.watchcalsync.wear.data.localStartMillis
import dev.lutergs.watchcalsync.wear.data.localEndMillis
import dev.lutergs.watchcalsync.wear.settings.EventMode
import java.time.Instant
import java.time.ZoneId

data class EventTimelineEntry(val startMillis: Long, val endMillis: Long, val text: NextEventText)

/** Pre-compute changes so midnight and event starts need no phone sync or alarm. */
object EventTimeline {
    fun build(
        snapshot: CalendarSnapshot?,
        nowMillis: Long,
        zone: ZoneId,
        mode: EventMode,
    ): List<EventTimelineEntry> {
        if (snapshot == null || snapshot.windowEndMillis <= nowMillis) return emptyList()
        val end = snapshot.windowEndMillis
        val boundaries = sortedSetOf(nowMillis, end)
        snapshot.events.forEach { event ->
            listOf(event.localStartMillis(zone), event.localEndMillis(zone)).forEach {
                if (it > nowMillis && it < end) boundaries += it
            }
        }
        var day = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().plusDays(1)
        while (day.atStartOfDay(zone).toInstant().toEpochMilli() < end) {
            boundaries += day.atStartOfDay(zone).toInstant().toEpochMilli()
            day = day.plusDays(1)
        }
        val result = mutableListOf<EventTimelineEntry>()
        boundaries.toList().zipWithNext().forEach { (start, stop) ->
            val text = NextEventFormatter.next(snapshot, start, zone, mode)
            val previous = result.lastOrNull()
            if (previous?.text == text) {
                result[result.lastIndex] = previous.copy(endMillis = stop)
            } else {
                result += EventTimelineEntry(start, stop, text)
            }
        }
        return result
    }
}
