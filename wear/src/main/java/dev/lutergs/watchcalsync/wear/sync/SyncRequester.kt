package dev.lutergs.watchcalsync.wear.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import dev.lutergs.watchcalsync.shared.sync.SyncContract
import kotlinx.coroutines.tasks.await

sealed interface RequestResult {
    data object Sent : RequestResult
    /** No phone in range. The cached agenda is still shown; it is just not refreshed. */
    data object NoPhone : RequestResult
    data class Failed(val message: String) : RequestResult
}

/** Asks the phone to re-read its calendar and push a fresh snapshot. */
class SyncRequester(private val context: Context) {

    suspend fun requestSync(): RequestResult = try {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        if (nodes.isEmpty()) {
            RequestResult.NoPhone
        } else {
            // Broadcast rather than pick one: there is normally a single paired
            // phone, and guessing wrong would silently do nothing.
            nodes.forEach { node ->
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, SyncContract.PATH_REQUEST_SYNC, ByteArray(0))
                    .await()
            }
            Log.i(TAG, "sync requested from ${nodes.size} node(s)")
            RequestResult.Sent
        }
    } catch (e: Exception) {
        Log.w(TAG, "sync request failed", e)
        RequestResult.Failed(e.message ?: e::class.java.simpleName)
    }

    private companion object {
        const val TAG = "WatchCalSync"
    }
}
