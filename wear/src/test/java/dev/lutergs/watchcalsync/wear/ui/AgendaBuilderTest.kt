package dev.lutergs.watchcalsync.wear.ui

import dev.lutergs.watchcalsync.shared.model.CalendarEvent
import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class AgendaBuilderTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = ZonedDateTime.of(2026, 8, 7, 9, 0, 0, 0, zone)
    private val nowMillis = now.toInstant().toEpochMilli()

    private fun timed(hour: Int, title: String, color: Int = 0xFF112233.toInt(), location: String? = null) =
        CalendarEvent(
            eventId = title.hashCode().toLong(),
            title = title,
            startMillis = now.withHour(hour).toInstant().toEpochMilli(),
            endMillis = now.withHour(hour + 1).toInstant().toEpochMilli(),
            allDay = false,
            location = location,
            calendarId = 1,
            accountName = "a@b.com",
            calendarDisplayName = "cal",
            color = color,
        )

    /** All-day rows are stored on UTC midnight boundaries, as the provider does. */
    private fun allDay(date: LocalDate, title: String, color: Int, calendarId: Long) =
        CalendarEvent(
            eventId = (title + calendarId).hashCode().toLong(),
            title = title,
            startMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            endMillis = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            allDay = true,
            calendarId = calendarId,
            accountName = "a@b.com",
            calendarDisplayName = "cal$calendarId",
            color = color,
        )

    private fun snapshot(events: List<CalendarEvent>) = CalendarSnapshot(
        generatedAtMillis = nowMillis,
        windowStartMillis = nowMillis - TimeUnit.DAYS.toMillis(1),
        windowEndMillis = nowMillis + TimeUnit.DAYS.toMillis(14),
        events = events,
    )

    @Test
    fun `all-day events on one day collapse into a single row`() {
        val today = LocalDate.of(2026, 8, 7)
        val events = listOf(
            allDay(today, "광복절", 0xFF16A765.toInt(), calendarId = 1),
            allDay(today, "입추", 0xFFECAD4C.toInt(), calendarId = 2),
            timed(14, "회의"),
        )

        val rows = AgendaBuilder.build(snapshot(events), nowMillis, zone)

        val allDayRows = rows.filterIsInstance<AgendaRow.AllDay>()
        assertEquals(1, allDayRows.size)
        assertEquals("광복절 · 입추", allDayRows[0].titles)
        // One dot per contributing calendar.
        assertEquals(2, allDayRows[0].dotColors.size)
    }

    /**
     * The same holiday typically exists in the 개인, 회사 and Samsung calendars at
     * once; showing it three times pushed the day's real meetings off screen.
     */
    @Test
    fun `the same all-day title from several calendars appears once`() {
        val today = LocalDate.of(2026, 8, 7)
        val events = listOf(
            allDay(today, "광복절", 0xFF16A765.toInt(), calendarId = 1),
            allDay(today, "광복절", 0xFFD75F64.toInt(), calendarId = 2),
            allDay(today, "광복절", 0xFF4986E7.toInt(), calendarId = 3),
        )

        val rows = AgendaBuilder.build(snapshot(events), nowMillis, zone)

        val allDayRow = rows.filterIsInstance<AgendaRow.AllDay>().single()
        assertEquals("광복절", allDayRow.titles)
        assertEquals(3, allDayRow.dotColors.size)
    }

    @Test
    fun `all-day events group under their own day, not the previous one`() {
        // 09:00 KST on the 7th is 00:00 UTC on the 7th. Resolving the all-day row in
        // the local zone would place it on the 7th at 09:00 → still the 7th here, but
        // for KST the UTC-midnight instant is 09:00 local, so a naive local
        // conversion of the *8th* would slip to the 8th 09:00. Guard both days.
        val events = listOf(
            allDay(LocalDate.of(2026, 8, 8), "내일 종일", 0xFF16A765.toInt(), 1),
        )

        val rows = AgendaBuilder.build(snapshot(events), nowMillis, zone)

        val header = rows.filterIsInstance<AgendaRow.DayHeader>().single()
        assertEquals("8월 8일 (토)", header.label)
    }

    @Test
    fun `all-day row is ordered before timed events of the same day`() {
        val today = LocalDate.of(2026, 8, 7)
        val events = listOf(
            timed(14, "회의"),
            allDay(today, "휴가", 0xFF16A765.toInt(), 1),
        )

        val rows = AgendaBuilder.build(snapshot(events), nowMillis, zone)

        assertTrue(rows[0] is AgendaRow.DayHeader)
        assertTrue(rows[1] is AgendaRow.AllDay)
        assertTrue(rows[2] is AgendaRow.Event)
    }

    @Test
    fun `bare video call links collapse to a label`() {
        val rows = AgendaBuilder.build(
            snapshot(listOf(timed(14, "회의", location = "https://example.zoom.us/j/1234567890?pwd=REDACTED"))),
            nowMillis,
            zone,
        )
        assertEquals("화상회의", rows.filterIsInstance<AgendaRow.Event>().single().location)
    }

    @Test
    fun `a location with a room name keeps the room and drops the link`() {
        val rows = AgendaBuilder.build(
            snapshot(listOf(timed(14, "회의", location = "회의실 A, https://meet.google.com/abc-defg-hij"))),
            nowMillis,
            zone,
        )
        assertEquals("회의실 A", rows.filterIsInstance<AgendaRow.Event>().single().location)
    }

    @Test
    fun `blank location becomes null rather than an empty line`() {
        val rows = AgendaBuilder.build(snapshot(listOf(timed(14, "회의", location = "   "))), nowMillis, zone)
        assertNull(rows.filterIsInstance<AgendaRow.Event>().single().location)
    }
}
