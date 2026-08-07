package dev.lutergs.watchcalsync.wear.tile

import android.content.Context
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
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
                agenda.entries.isEmpty() -> centeredMessage("예정된 일정 없음")
                else -> agendaColumn(agenda)
            }
        },
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

    agenda.entries.forEachIndexed { index, entry ->
        if (index > 0) {
            column.addContent(Spacer.Builder().setHeight(dp(6f)).build())
        }
        column.addContent(entryRow(entry))
    }

    if (agenda.overflowCount > 0) {
        column.addContent(Spacer.Builder().setHeight(dp(4f)).build())
        column.addContent(
            text(
                LayoutString("+${agenda.overflowCount}개 더"),
                typography = Typography.BODY_EXTRA_SMALL,
                color = colorScheme.onSurfaceVariant,
            )
        )
    }
    return column.build()
}

/** A colour chip plus "언제 · 무엇", matching the app screen's reading order. */
private fun MaterialScope.entryRow(entry: TileEntry): LayoutElement {
    val dotColor = if (entry.colorArgb == 0) colorScheme.primaryDim
    else LayoutColor(entry.colorArgb)

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
        .addContent(Spacer.Builder().setWidth(dp(6f)).build())
        .addContent(
            Column.Builder()
                .setWidth(androidx.wear.protolayout.DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                .addContent(
                    text(
                        LayoutString(entry.whenLabel),
                        typography = Typography.LABEL_SMALL,
                        color = colorScheme.primaryDim,
                    )
                )
                .addContent(
                    text(
                        LayoutString(entry.title),
                        typography = Typography.BODY_SMALL,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                    )
                )
                .build()
        )
        .build()
}
