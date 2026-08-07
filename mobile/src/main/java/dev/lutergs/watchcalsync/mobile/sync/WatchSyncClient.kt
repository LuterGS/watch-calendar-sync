package dev.lutergs.watchcalsync.mobile.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dev.lutergs.watchcalsync.mobile.calendar.CalendarReader
import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import dev.lutergs.watchcalsync.shared.sync.SnapshotCodec
import dev.lutergs.watchcalsync.shared.sync.SyncContract
import kotlinx.coroutines.tasks.await

sealed interface PushResult {
    /** Pushed to the Data Layer. [nodeCount] is informational — the item syncs even with 0 nodes online. */
    data class Pushed(val bytes: Int, val eventCount: Int, val nodeCount: Int) : PushResult

    /** Agenda is byte-identical to what was last pushed, so nothing was written. */
    data class Unchanged(val eventCount: Int) : PushResult

    data class Failed(val message: String) : PushResult
}

/**
 * Pushes calendar snapshots to the watch over the Wearable Data Layer.
 *
 * There is no server and no account here: the Data Layer pairs the phone and watch
 * apps purely on matching applicationId + signing key, and syncs over Bluetooth.
 */
class WatchSyncClient(private val context: Context) {

    private val dataClient = Wearable.getDataClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    /** Nodes currently reachable. Empty is normal when the watch is out of range. */
    suspend fun connectedNodeNames(): List<String> = runCatching {
        nodeClient.connectedNodes.await().map { it.displayName }
    }.getOrElse {
        Log.w(TAG, "node lookup failed", it)
        emptyList()
    }

    /**
     * Writes [snapshot] as the single snapshot DataItem.
     *
     * The item is *replaced*, never appended: the watch mirrors whatever is at
     * [SyncContract.PATH_SNAPSHOT], so deletions on the phone propagate for free.
     *
     * @param lastPushedHash content hash of the previous push, or null if unknown.
     * @param force skip the unchanged check (used by the manual sync button).
     */
    suspend fun push(
        snapshot: CalendarSnapshot,
        lastPushedHash: Int? = null,
        force: Boolean = false,
    ): PushResult {
        val hash = SnapshotCodec.contentHash(snapshot)
        if (!force && hash == lastPushedHash) {
            return PushResult.Unchanged(snapshot.events.size)
        }

        return try {
            val payload = SnapshotCodec.encode(snapshot)
            if (payload.size > MAX_DATA_ITEM_BYTES) {
                // Better to say so loudly than to have the watch silently stop updating.
                return PushResult.Failed(
                    "페이로드 ${payload.size}B 가 DataItem 한도(${MAX_DATA_ITEM_BYTES}B)를 초과합니다. " +
                        "조회 구간을 줄이거나 캘린더를 더 끄세요.",
                )
            }

            val request = PutDataMapRequest.create(SyncContract.PATH_SNAPSHOT).apply {
                dataMap.putInt(SyncContract.KEY_SCHEMA_VERSION, SyncContract.SCHEMA_VERSION)
                dataMap.putByteArray(SyncContract.KEY_SNAPSHOT_GZIP, payload)
                dataMap.putLong(SyncContract.KEY_GENERATED_AT, snapshot.generatedAtMillis)
            }.asPutDataRequest().setUrgent()

            dataClient.putDataItem(request).await()

            val nodes = connectedNodeNames()
            Log.i(
                TAG,
                "pushed ${snapshot.events.size} events, ${payload.size}B gzip, nodes=$nodes",
            )
            PushResult.Pushed(payload.size, snapshot.events.size, nodes.size)
        } catch (e: Exception) {
            Log.e(TAG, "push failed", e)
            PushResult.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    private companion object {
        const val TAG = CalendarReader.TAG

        /** Data Layer caps a DataItem at 100 KB; leave headroom for the DataMap framing. */
        const val MAX_DATA_ITEM_BYTES = 95_000
    }
}
