package dev.lutergs.watchcalsync.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import dev.lutergs.watchcalsync.wear.data.SnapshotStore
import dev.lutergs.watchcalsync.wear.settings.WatchSettings
import dev.lutergs.watchcalsync.wear.ui.MainActivity
import java.time.Instant
import java.time.ZoneId

/**
 * Watch-face complication showing the next event.
 *
 * Reads the same cached snapshot as the app and the tile, so it keeps working with
 * no phone in range. Only SHORT_TEXT and LONG_TEXT are offered: the data is text,
 * and claiming ranged or icon types would put this in slots it cannot fill well.
 */
class NextEventComplicationService : SuspendingTimelineComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? {
        val store = SnapshotStore.get(applicationContext)
        val snapshot = store.snapshot.value ?: store.load()
        val mode = WatchSettings(applicationContext).eventMode
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val fallback = data(request.complicationType, NextEventFormatter.next(null, now, zone, mode))
            ?: return null
        return ComplicationDataTimeline(
            defaultComplicationData = fallback,
            timelineEntries = EventTimeline.build(snapshot, now, zone, mode).map { entry ->
                TimelineEntry(
                    validity = TimeInterval(
                        Instant.ofEpochMilli(entry.startMillis), Instant.ofEpochMilli(entry.endMillis),
                    ),
                    complicationData = data(request.complicationType, entry.text)!!,
                )
            },
        )
    }

    private fun data(type: ComplicationType, next: NextEventText): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(next.shortText).build(),
                contentDescription = PlainComplicationText.Builder(next.description).build(),
            ).setTitle(
                next.shortTitle?.let { PlainComplicationText.Builder(it).build() }
            ).setTapAction(tapAction()).build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(next.longText).build(),
                contentDescription = PlainComplicationText.Builder(next.description).build(),
            ).setTitle(
                next.longTitle?.let { PlainComplicationText.Builder(it).build() }
            ).setTapAction(tapAction()).build()

            // Returning null for an unrequested type is how the framework is told
            // this source has nothing for that slot.
            else -> null
        }
    }

    /**
     * Preview shown in the complication picker. It must not read live data — the
     * picker renders it before the user has chosen anything.
     */
    override fun getPreviewData(type: ComplicationType): ComplicationData? = when (type) {
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("14:00").build(),
            contentDescription = PlainComplicationText.Builder("다음 일정").build(),
        ).setTitle(PlainComplicationText.Builder("주간회의").build()).build()

        ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder("14:00 주간회의").build(),
            contentDescription = PlainComplicationText.Builder("다음 일정").build(),
        ).setTitle(PlainComplicationText.Builder("다음 일정").build()).build()

        else -> null
    }

    private fun tapAction(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        /**
         * Nudges the watch face to re-read this complication. Called after a sync,
         * because otherwise the face would keep the previous event until its own
         * update period elapsed.
         */
        fun requestUpdate(context: Context) {
            ComplicationDataSourceUpdateRequester
                .create(
                    context = context,
                    complicationDataSourceComponent = ComponentName(
                        context,
                        NextEventComplicationService::class.java,
                    ),
                )
                .requestUpdateAll()
        }
    }
}
