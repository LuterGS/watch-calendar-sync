package dev.lutergs.watchcalsync.wear.data

import dev.lutergs.watchcalsync.shared.model.CalendarEvent
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** CalendarProvider encodes all-day dates at UTC midnight, not as real instants. */
private fun CalendarEvent.localBoundary(millis: Long, zone: ZoneId): Long =
    if (allDay) Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        .atStartOfDay(zone).toInstant().toEpochMilli() else millis

fun CalendarEvent.localStartMillis(zone: ZoneId): Long = localBoundary(startMillis, zone)
fun CalendarEvent.localEndMillis(zone: ZoneId): Long = localBoundary(endMillis, zone)
