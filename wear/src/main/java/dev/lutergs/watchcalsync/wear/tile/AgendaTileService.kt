package dev.lutergs.watchcalsync.wear.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.background
import androidx.wear.protolayout.modifiers.clip
import androidx.wear.protolayout.modifiers.padding
import androidx.wear.protolayout.modifiers.toProtoLayoutModifiers
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dev.lutergs.watchcalsync.wear.data.SnapshotStore
import androidx.compose.ui.graphics.toArgb
import dev.lutergs.watchcalsync.wear.ui.AgendaRow
import dev.lutergs.watchcalsync.wear.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

/**
 * The "위젯": a quick-glance Tile listing what is coming up over the next 7 days.
 *
 * Tiles render in the system's Tile host, not in this app's Compose tree, so the
 * layout is built with ProtoLayout rather than Compose. It reads the same cached
 * snapshot the app screen uses, which means it works with no phone in range.
 */
class AgendaTileService : TileService() {

    // onTileRequest is called on the main thread and must hand back a future, so
    // the cache read is moved off it rather than blocked on.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = scope.future {
        val store = SnapshotStore.get(applicationContext)
        val snapshot = store.snapshot.value ?: store.load()
        val agenda = TileAgendaBuilder.build(snapshot)

        val layout = tileLayout(
            context = applicationContext,
            device = requestParams.deviceConfiguration,
            agenda = agenda,
            // Tapping the tile opens the full agenda — the tile is the glance, the
            // app screen is the detail.
            onClick = Clickable.Builder()
                .setId("open_agenda")
                .setOnClick(
                    ActionBuilders.LaunchAction.Builder()
                        .setAndroidActivity(
                            ActionBuilders.AndroidActivity.Builder()
                                .setPackageName(packageName)
                                .setClassName(MainActivity::class.java.name)
                                .build()
                        )
                        .build()
                )
                .build(),
        )

        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            // Re-render periodically so relative labels like "오늘 14:00" do not go
            // stale and past events fall off; a push from the phone also triggers an
            // explicit update via requestUpdate().
            .setFreshnessIntervalMillis(FRESHNESS_MILLIS)
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(layout)
            )
            .build()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = scope.future {
        ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
    }

    private companion object {
        const val RESOURCES_VERSION = "1"

        /** 15 minutes: often enough that the list is not visibly stale, rarely enough to be cheap. */
        const val FRESHNESS_MILLIS = 15 * 60 * 1000L
    }
}

private fun tileLayout(
    context: Context,
    device: DeviceParameters,
    agenda: TileAgenda,
    onClick: Clickable,
): LayoutElement = materialScope(context, device) {
    primaryLayout(
        titleSlot = {
            text(
                LayoutString("다음 7일"),
                typography = Typography.LABEL_MEDIUM,
                color = colorScheme.onSurfaceVariant,
            )
        },
        mainSlot = {
            when {
                !agenda.hasSnapshot -> centeredMessage("폰 동기화 대기 중")
                agenda.rows.isEmpty() -> centeredMessage("예정된 일정 없음")
                else -> agendaColumn(agenda)
            }
        },
        onClick = onClick,
    )
}

private fun MaterialScope.centeredMessage(message: String): LayoutElement =
    text(
        LayoutString(message),
        typography = Typography.BODY_MEDIUM,
        color = colorScheme.onSurfaceVariant,
    )

private fun MaterialScope.agendaColumn(agenda: TileAgenda): LayoutElement {
    val column = Column.Builder()
        .setWidth(androidx.wear.protolayout.DimensionBuilders.expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)

    agenda.rows.forEachIndexed { index, row ->
        if (index > 0) {
            column.addContent(
                Spacer.Builder()
                    .setHeight(dp(if (row is AgendaRow.DayHeader) 5f else 2f))
                    .build()
            )
        }
        column.addContent(
            when (row) {
                is AgendaRow.DayHeader -> headerRow(row)
                is AgendaRow.AllDay -> entryRow(
                    dotArgb = row.dotColors.firstOrNull()?.toArgb() ?: 0,
                    lead = "종일",
                    title = row.titles,
                )
                is AgendaRow.Event -> entryRow(
                    dotArgb = row.dotColor.toArgb(),
                    lead = row.timeLabel,
                    title = row.title,
                )
            }
        )
    }

    if (agenda.hiddenEvents > 0) {
        column.addContent(Spacer.Builder().setHeight(dp(3f)).build())
        column.addContent(
            text(
                LayoutString("+${agenda.hiddenEvents}개 더"),
                typography = Typography.BODY_EXTRA_SMALL,
                color = colorScheme.onSurfaceVariant,
            )
        )
    }
    return column.build()
}

private fun MaterialScope.headerRow(row: AgendaRow.DayHeader): LayoutElement =
    text(
        LayoutString(row.label),
        typography = Typography.LABEL_SMALL,
        color = colorScheme.onSurfaceVariant,
    )

/**
 * One agenda entry on a single line: colour dot, then "시각  제목".
 *
 * The app screen gives each event two lines, but a tile cannot scroll, so
 * stacking them here would halve how much of the week is visible. The dot, the
 * ordering and the day grouping still match the app.
 */
private fun MaterialScope.entryRow(
    dotArgb: Int,
    lead: String,
    title: String,
): LayoutElement {
    val dotColor = if (dotArgb == 0) colorScheme.primaryDim else LayoutColor(dotArgb)

    return Row.Builder()
        .setWidth(androidx.wear.protolayout.DimensionBuilders.expand())
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .addContent(
            LayoutElementBuilders.Box.Builder()
                .setWidth(dp(6f))
                .setHeight(dp(6f))
                .setModifiers(
                    LayoutModifier
                        .background(dotColor)
                        .clip(3f)
                        .toProtoLayoutModifiers()
                )
                .build()
        )
        .addContent(Spacer.Builder().setWidth(dp(5f)).build())
        .addContent(
            text(
                LayoutString(lead),
                typography = Typography.LABEL_SMALL,
                color = colorScheme.primaryDim,
            )
        )
        .addContent(Spacer.Builder().setWidth(dp(5f)).build())
        .addContent(
            text(
                LayoutString(title),
                typography = Typography.BODY_SMALL,
                color = colorScheme.onSurface,
                maxLines = 1,
            )
        )
        .build()
}
