package dev.lutergs.watchcalsync.wear.complication

import dev.lutergs.watchcalsync.shared.model.CalendarEvent
import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class NextEventFormatterTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = ZonedDateTime.of(2026, 8, 7, 9, 0, 0, 0, zone)
    private val nowMillis = now.toInstant().toEpochMilli()

    private fun timed(dayOffset: Long, hour: Int, title: String) = CalendarEvent(
        eventId = title.hashCode().toLong(),
        title = title,
        startMillis = now.plusDays(dayOffset).withHour(hour).toInstant().toEpochMilli(),
        endMillis = now.plusDays(dayOffset).withHour(hour + 1).toInstant().toEpochMilli(),
        allDay = false,
        calendarId = 1,
        accountName = "a@b.com",
        calendarDisplayName = "cal",
        color = 0,
    )

    private fun allDay(date: LocalDate, title: String) = CalendarEvent(
        eventId = title.hashCode().toLong(),
        title = title,
        startMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        endMillis = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        allDay = true,
        calendarId = 1,
        accountName = "a@b.com",
        calendarDisplayName = "cal",
        color = 0,
    )

    private fun snapshot(events: List<CalendarEvent>) = CalendarSnapshot(
        generatedAtMillis = nowMillis,
        windowStartMillis = nowMillis - TimeUnit.DAYS.toMillis(1),
        windowEndMillis = nowMillis + TimeUnit.DAYS.toMillis(14),
        events = events,
    )

    @Test
    fun `today's next event shows the clock time`() {
        val text = NextEventFormatter.next(
            snapshot(listOf(timed(0, 14, "주간회의"), timed(0, 17, "나중"))),
            nowMillis, zone,
        )
        assertEquals("14:00", text.shortText)
        assertEquals("주간회의", text.shortTitle)
        assertEquals("14:00 주간회의", text.longText)
        assertEquals("오늘", text.longTitle)
    }

    /** In a slot this small the date is more use than the minute. */
    @Test
    fun `an event on a later day shows the date instead of the time`() {
        val text = NextEventFormatter.next(snapshot(listOf(timed(3, 14, "출장"))), nowMillis, zone)
        assertEquals("8/10", text.shortText)
        assertEquals("8/10", text.longTitle)
    }

    @Test
    fun `all-day events read as 종일`() {
        val text = NextEventFormatter.next(
            snapshot(listOf(allDay(LocalDate.of(2026, 8, 7), "휴가"))),
            nowMillis, zone,
        )
        assertEquals("종일", text.shortText)
        assertEquals("휴가", text.shortTitle)
    }

    @Test
    fun `finished events are skipped`() {
        val text = NextEventFormatter.next(
            snapshot(listOf(timed(0, 6, "끝남"), timed(0, 14, "다음"))),
            nowMillis, zone,
        )
        assertEquals("다음", text.shortTitle)
    }

    @Test
    fun `no snapshot and no events both degrade to a placeholder`() {
        assertEquals("—", NextEventFormatter.next(null, nowMillis, zone).shortText)
        assertEquals(
            "예정된 일정 없음",
            NextEventFormatter.next(snapshot(emptyList()), nowMillis, zone).longText,
        )
    }
}
