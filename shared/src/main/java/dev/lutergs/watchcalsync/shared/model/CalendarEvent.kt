package dev.lutergs.watchcalsync.shared.model

import kotlinx.serialization.Serializable

/**
 * One *occurrence* of a calendar event, in the minimal shape the watch needs.
 *
 * This models a row from `CalendarContract.Instances`, not `Events`: a recurring
 * event has a single `Events` row but one `Instances` row per occurrence, and an
 * agenda wants the occurrences. Identity is therefore ([eventId], [startMillis]) —
 * `eventId` alone repeats across a recurring series.
 *
 * Times are epoch millis. [allDay] events are a special case: the provider stores
 * their [startMillis]/[endMillis] as UTC midnight boundaries rather than local
 * time, so they must be formatted in UTC or they render on the wrong day.
 */
@Serializable
data class CalendarEvent(
    /** CalendarContract.Instances.EVENT_ID — the owning event; repeats across a series. */
    val eventId: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String? = null,
    /** CalendarContract.Events.CALENDAR_ID — which calendar this came from. */
    val calendarId: Long,
    /** Owning account, e.g. "me@gmail.com" vs "me@company.com". Distinguishes 개인/회사. */
    val accountName: String,
    /** Human-readable calendar name as shown in the Calendar app. */
    val calendarDisplayName: String,
    /** ARGB display color (event override if set, otherwise the calendar's color). */
    val color: Int,
) {
    /** Stable identity for an occurrence; safe as a list key. */
    val occurrenceKey: String get() = "$eventId@$startMillis"
}

/**
 * A full snapshot of the phone's agenda window. The watch replaces its cache with
 * this wholesale rather than merging, which makes deletions correct for free.
 */
@Serializable
data class CalendarSnapshot(
    /** When the phone produced this snapshot (epoch millis). */
    val generatedAtMillis: Long,
    /** Inclusive start of the queried window. */
    val windowStartMillis: Long,
    /** Exclusive end of the queried window. */
    val windowEndMillis: Long,
    val events: List<CalendarEvent>,
)

/** A calendar available on the phone. Used for the diagnostic dump and, later, filtering. */
@Serializable
data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val ownerAccount: String?,
    val color: Int,
    /** Whether the user has this calendar checked in the Calendar app. */
    val visible: Boolean,
    /** Whether the provider is syncing it at all. */
    val synced: Boolean,
)
