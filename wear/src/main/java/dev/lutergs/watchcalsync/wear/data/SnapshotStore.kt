package dev.lutergs.watchcalsync.wear.data

import android.content.Context
import android.util.Log
import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import dev.lutergs.watchcalsync.shared.sync.SyncContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * The watch's offline cache: the last snapshot received from the phone, kept as a
 * plain JSON file.
 *
 * A file rather than a database because the watch never queries this data — it
 * replaces the whole snapshot on every sync and reads it back in one go. Room
 * would add a compiler plugin and a schema to migrate for no benefit.
 */
class SnapshotStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val writeLock = Mutex()

    private val _snapshot = MutableStateFlow<CalendarSnapshot?>(null)

    /** Last known snapshot. Null until [load] finds a cached file or a sync arrives. */
    val snapshot: StateFlow<CalendarSnapshot?> = _snapshot.asStateFlow()

    suspend fun load(): CalendarSnapshot? = withContext(Dispatchers.IO) {
        val cached = runCatching {
            if (file.exists()) SyncContract.json.decodeFromString<CalendarSnapshot>(file.readText())
            else null
        }.getOrElse {
            // A corrupt or schema-incompatible cache must not brick the app; drop it.
            Log.w(TAG, "cache unreadable, discarding", it)
            runCatching { file.delete() }
            null
        }
        cached?.let { _snapshot.value = it }
        cached
    }

    suspend fun save(snapshot: CalendarSnapshot) = withContext(Dispatchers.IO) {
        // Publish first so the UI updates even if the disk write is slow.
        _snapshot.value = snapshot
        writeLock.withLock {
            runCatching {
                // Write-then-rename so a kill mid-write cannot leave a truncated cache.
                val tmp = File(file.parentFile, "$FILE_NAME.tmp")
                tmp.writeText(SyncContract.json.encodeToString(snapshot))
                if (!tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
            }.onFailure { Log.e(TAG, "cache write failed", it) }
        }
    }

    companion object {
        const val TAG = "WatchCalSync"
        private const val FILE_NAME = "snapshot.json"

        @Volatile
        private var instance: SnapshotStore? = null

        /**
         * Single shared instance: the listener service and the Activity are separate
         * entry points in the same process, and both must see the same cache state.
         */
        fun get(context: Context): SnapshotStore =
            instance ?: synchronized(this) {
                instance ?: SnapshotStore(context.applicationContext).also { instance = it }
            }
    }
}
