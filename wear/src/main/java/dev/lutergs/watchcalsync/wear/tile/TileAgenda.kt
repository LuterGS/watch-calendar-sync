package dev.lutergs.watchcalsync.wear.tile

import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import dev.lutergs.watchcalsync.wear.ui.AgendaBuilder
import dev.lutergs.watchcalsync.wear.ui.AgendaRow
import java.time.ZoneId
import java.util.concurrent.TimeUnit

data class TileAgenda(
    /** Exactly the rows the app screen would show, cut to what fits on a tile. */
    val rows: List<AgendaRow>,
    /** Events inside the 7-day window that did not fit. */
    val hiddenEvents: Int,
    val hasSnapshot: Boolean,
)

object TileAgendaBuilder {

    /** How far ahead the glance looks. */
    const val HORIZON_DAYS = 7L

    /**
     * A tile face does not scroll, so this is a hard ceiling on rows, not a
     * preference. Overshooting does not add information — the extra rows are
     * simply clipped off the bottom, which is what made the previous layout look
     * like it only covered three days.
     */
    const val MAX_ROWS = 6

    /**
     * Reuses [AgendaBuilder] rather than formatting its own summary, so the tile
     * and the app screen cannot drift apart: same grouping, same all-day merging,
     * same ordering — just truncated.
     */
    fun build(
        snapshot: CalendarSnapshot?,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        maxRows: Int = MAX_ROWS,
    ): TileAgenda {
        if (snapshot == null) return TileAgenda(emptyList(), 0, hasSnapshot = false)

        val all = AgendaBuilder.build(
            snapshot = snapshot,
            nowMillis = nowMillis,
            zone = zone,
            horizonMillis = nowMillis + TimeUnit.DAYS.toMillis(HORIZON_DAYS),
        )

        // When something will be cut, the last line goes to the "+N개 더" summary.
        // Without reserving it that summary is itself the row past the edge, so it
        // gets clipped and nothing ever hints that the week continues.
        val budget = if (all.size > maxRows) maxRows - 1 else maxRows

        var shown = all.take(budget)
        // A day header with nothing under it reads as a rendering bug.
        if (shown.isNotEmpty() && shown.last() is AgendaRow.DayHeader) {
            shown = shown.dropLast(1)
        }

        return TileAgenda(
            rows = shown,
            hiddenEvents = all.drop(shown.size).count { it !is AgendaRow.DayHeader },
            hasSnapshot = true,
        )
    }
}
