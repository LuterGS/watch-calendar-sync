package dev.lutergs.watchcalsync.wear.tile

import dev.lutergs.watchcalsync.shared.model.CalendarEvent
import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import dev.lutergs.watchcalsync.wear.ui.AgendaBuilder
import dev.lutergs.watchcalsync.wear.ui.AgendaRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class TileAgendaBuilderTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = ZonedDateTime.of(2026, 8, 7, 9, 0, 0, 0, zone)
    private val nowMillis = now.toInstant().toEpochMilli()

    private fun event(dayOffset: Long, hour: Int, title: String): CalendarEvent {
        val start = now.plusDays(dayOffset).withHour(hour).withMinute(0)
        return CalendarEvent(
            eventId = (title + dayOffset).hashCode().toLong(),
            title = title,
            startMillis = start.toInstant().toEpochMilli(),
            endMillis = start.plusHours(1).toInstant().toEpochMilli(),
            allDay = false,
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
     * The tile must show the same page as the app, only shorter — so its rows are
     * a prefix of what the app screen renders for the same window.
     */
    @Test
    fun `tile rows are a prefix of the app screen rows`() {
        val events = (0L..3L).flatMap { d -> listOf(event(d, 10, "a$d"), event(d, 14, "b$d")) }
        val snap = snapshot(events)

        val appRows = AgendaBuilder.build(
            snap, nowMillis, zone,
            horizonMillis = nowMillis + TimeUnit.DAYS.toMillis(TileAgendaBuilder.HORIZON_DAYS),
        )
        val tile = TileAgendaBuilder.build(snap, nowMillis, zone)

        assertEquals(appRows.take(tile.rows.size), tile.rows)
    }

    @Test
    fun `row count never exceeds what a tile face can hold`() {
        val events = (0L..6L).flatMap { d -> listOf(event(d, 10, "a$d"), event(d, 14, "b$d")) }

        val tile = TileAgendaBuilder.build(snapshot(events), nowMillis, zone)

        assertTrue(tile.rows.size <= TileAgendaBuilder.MAX_ROWS)
    }

    /** A day header with nothing under it reads as a rendering bug. */
    @Test
    fun `a trailing day header is dropped`() {
        // 3 rows fit: header(8/7), event, header(8/8) — the last header must go.
        val events = listOf(event(0, 10, "오늘"), event(1, 10, "내일"))

        val tile = TileAgendaBuilder.build(snapshot(events), nowMillis, zone, maxRows = 3)

        assertEquals(2, tile.rows.size)
        assertFalse(tile.rows.last() is AgendaRow.DayHeader)
        // The dropped header's event still counts as hidden.
        assertEquals(1, tile.hiddenEvents)
    }

    @Test
    fun `hidden count covers events only, not headers`() {
        val events = (0L..4L).map { event(it, 10, "day $it") }

        val tile = TileAgendaBuilder.build(snapshot(events), nowMillis, zone, maxRows = 4)

        // 10 rows exist, so one line is reserved for the summary: 3 rows are taken
        // (header, event, header), the trailing header drops, leaving 1 of 5 events.
        assertEquals(2, tile.rows.size)
        assertEquals(4, tile.hiddenEvents)
    }

    /** The summary line only earns its slot when something is actually cut. */
    @Test
    fun `nothing is reserved when everything fits`() {
        val events = listOf(event(0, 10, "하나"), event(0, 14, "둘"))

        val tile = TileAgendaBuilder.build(snapshot(events), nowMillis, zone, maxRows = 4)

        assertEquals(3, tile.rows.size) // header + 2 events
        assertEquals(0, tile.hiddenEvents)
    }

    @Test
    fun `events beyond the 7 day horizon are excluded entirely`() {
        val tile = TileAgendaBuilder.build(
            snapshot(listOf(event(1, 10, "이번 주"), event(9, 10, "9일 뒤"))),
            nowMillis, zone,
        )

        assertEquals(0, tile.hiddenEvents)
        assertTrue(tile.rows.filterIsInstance<AgendaRow.Event>().none { it.title == "9일 뒤" })
    }

    @Test
    fun `empty snapshot is distinguished from no snapshot`() {
        assertFalse(TileAgendaBuilder.build(null, nowMillis, zone).hasSnapshot)

        val empty = TileAgendaBuilder.build(snapshot(emptyList()), nowMillis, zone)
        assertTrue(empty.hasSnapshot)
        assertTrue(empty.rows.isEmpty())
    }
}
