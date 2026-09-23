package dev.lutergs.watchcalsync.wear.complication

import dev.lutergs.watchcalsync.shared.model.CalendarEvent
import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import dev.lutergs.watchcalsync.wear.settings.EventMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

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
            nowMillis, zone, EventMode.ALL_DAY,
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
    @Test
    fun `upcoming mode skips all-day and already started events`() {
        val data = snapshot(listOf(
            allDay(now.toLocalDate(), "휴가"), timed(0, 9, "진행 중"), timed(0, 14, "다음 회의"),
        ))
        assertEquals("다음 회의", NextEventFormatter.next(data, nowMillis, zone).shortTitle)
        assertEquals("휴가", NextEventFormatter.next(data, nowMillis, zone, EventMode.ALL_DAY).shortTitle)
    }

    @Test
    fun `Seoul 1am excludes yesterday all-day event`() {
        val early = now.withHour(1).toInstant().toEpochMilli()
        val data = snapshot(listOf(
            allDay(now.toLocalDate().minusDays(1), "어제"), allDay(now.toLocalDate(), "오늘 휴가"),
        ))
        val text = NextEventFormatter.next(data, early, zone, EventMode.ALL_DAY)
        assertEquals("오늘 휴가", text.shortTitle)
        assertEquals("오늘", text.longTitle)
    }

    @Test
    fun `all-day mode excludes tomorrow and keeps multiday events through local end`() {
        val trip = allDay(now.toLocalDate().minusDays(1), "여행").copy(
            endMillis = now.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        assertEquals("오늘", NextEventFormatter.next(snapshot(listOf(trip)), nowMillis, zone, EventMode.ALL_DAY).longTitle)
        assertEquals("오늘 종일 일정 없음", NextEventFormatter.next(
            snapshot(listOf(allDay(now.toLocalDate().plusDays(1), "내일"))), nowMillis, zone, EventMode.ALL_DAY,
        ).longText)
    }

    @Test
    fun `timeline switches at local midnight without a sync`() {
        val midnight = now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val data = snapshot(listOf(
            allDay(now.toLocalDate().minusDays(1), "어제"), allDay(now.toLocalDate(), "오늘 휴가"),
        ))
        val entries = EventTimeline.build(data, midnight - 60_000, zone, EventMode.ALL_DAY)
        assertEquals("어제", entries.first().text.shortTitle)
        assertEquals(midnight, entries.first().endMillis)
        assertEquals("오늘 휴가", entries.first { it.startMillis == midnight }.text.shortTitle)
    }

    @Test
    fun `timeline switches to next event when a meeting starts`() {
        val first = timed(0, 14, "첫 회의")
        val entries = EventTimeline.build(snapshot(listOf(first, timed(0, 17, "다음 회의"))), nowMillis, zone, EventMode.UPCOMING)
        assertEquals("첫 회의", entries.first().text.shortTitle)
        assertEquals(first.startMillis, entries.first().endMillis)
        assertEquals("다음 회의", entries.first { it.startMillis == first.startMillis }.text.shortTitle)
        assertEquals("예정된 일정 없음", entries.last().text.longText)
    }

    @Test
    fun `timeline relabels tomorrow event as today at midnight`() {
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val entries = EventTimeline.build(snapshot(listOf(timed(1, 14, "회의"))), nowMillis, zone, EventMode.UPCOMING)
        assertEquals("8/8", entries.first().text.shortText)
        assertEquals("14:00", entries.first { it.startMillis == midnight }.text.shortText)
    }

    @Test
    fun `no cache or expired cache produces no timeline`() {
        assertEquals(emptyList<EventTimelineEntry>(), EventTimeline.build(null, nowMillis, zone, EventMode.UPCOMING))
        val expired = snapshot(listOf(timed(0, 14, "회의"))).copy(windowEndMillis = nowMillis)
        assertEquals(emptyList<EventTimelineEntry>(), EventTimeline.build(expired, nowMillis, zone, EventMode.UPCOMING))
    }

    @Test
    fun `upcoming mode never falls back to all-day events`() {
        val data = snapshot(listOf(allDay(now.toLocalDate(), "휴가")))
        assertEquals("예정된 일정 없음", NextEventFormatter.next(data, nowMillis, zone).longText)
    }

}
