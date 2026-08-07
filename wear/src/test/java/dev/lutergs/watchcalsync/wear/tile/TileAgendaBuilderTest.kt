package dev.lutergs.watchcalsync.wear.tile

import dev.lutergs.watchcalsync.shared.model.CalendarEvent
import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class TileAgendaBuilderTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = ZonedDateTime.of(2026, 8, 7, 9, 0, 0, 0, zone)
    private val nowMillis = now.toInstant().toEpochMilli()

    private fun event(
        dayOffset: Long,
        hour: Int,
        title: String,
        allDay: Boolean = false,
    ): CalendarEvent {
        val start = now.plusDays(dayOffset).withHour(hour).withMinute(0)
        return CalendarEvent(
            eventId = title.hashCode().toLong(),
            title = title,
            startMillis = start.toInstant().toEpochMilli(),
            endMillis = start.plusHours(1).toInstant().toEpochMilli(),
            allDay = allDay,
            calendarId = 1,
            accountName = "a@b.com",
            calendarDisplayName = "cal",
            color = 0xFF112233.toInt(),
        )
    }

    private fun snapshot(events: List<CalendarEvent>) = CalendarSnapshot(
        generatedAtMillis = nowMillis,
        windowStartMillis = nowMillis - TimeUnit.DAYS.toMillis(1),
        windowEndMillis = nowMillis + TimeUnit.DAYS.toMillis(14),
        events = events,
    )

    /**
     * The bug this guards: the tile used to take the first N *events*, so a single
     * busy day filled it and a "next 7 days" glance only ever showed today.
     */
    @Test
    fun `busy today does not crowd out later days`() {
        val events = listOf(
            event(0, 10, "오늘 1"),
            event(0, 11, "오늘 2"),
            event(0, 12, "오늘 3"),
            event(0, 13, "오늘 4"),
            event(0, 14, "오늘 5"),
            event(1, 10, "내일"),
            event(2, 10, "모레"),
            event(3, 10, "3일 뒤"),
        )

        val agenda = TileAgendaBuilder.build(snapshot(events), nowMillis, zone)

        assertEquals(4, agenda.days.size)
        assertEquals(listOf("오늘", "내일", "8/9 (일)", "8/10 (월)"), agenda.days.map { it.dayLabel })
        // Today keeps its earliest event as the headline, the rest become "+N".
        assertEquals("오늘 1", agenda.days[0].headline)
        assertEquals(4, agenda.days[0].moreCount)
        assertEquals(0, agenda.days[1].moreCount)
    }

    @Test
    fun `days beyond the 7 day horizon are excluded`() {
        val events = listOf(event(1, 10, "이번 주"), event(9, 10, "9일 뒤"))

        val agenda = TileAgendaBuilder.build(snapshot(events), nowMillis, zone)

        assertEquals(listOf("내일"), agenda.days.map { it.dayLabel })
    }

    @Test
    fun `days past the tile capacity are reported as overflow`() {
        val events = (1L..6L).map { event(it, 10, "day $it") }

        val agenda = TileAgendaBuilder.build(snapshot(events), nowMillis, zone)

        assertEquals(TileAgendaBuilder.MAX_DAYS, agenda.days.size)
        assertEquals(2, agenda.overflowDays)
    }

    @Test
    fun `finished events are dropped but a running one is kept`() {
        val running = event(0, 8, "진행 중")  // 08:00-09:00, now is 09:00 → end == now
        val finished = event(0, 6, "끝난 일정") // 06:00-07:00
        val later = event(0, 15, "나중")

        val agenda = TileAgendaBuilder.build(snapshot(listOf(running, finished, later)), nowMillis, zone)

        assertEquals(1, agenda.days.size)
        assertEquals("나중", agenda.days[0].headline)
        assertTrue(agenda.days.none { it.headline == "끝난 일정" })
    }

    @Test
    fun `empty snapshot is distinguished from no snapshot`() {
        assertEquals(false, TileAgendaBuilder.build(null, nowMillis, zone).hasSnapshot)

        val empty = TileAgendaBuilder.build(snapshot(emptyList()), nowMillis, zone)
        assertEquals(true, empty.hasSnapshot)
        assertTrue(empty.days.isEmpty())
    }
}
