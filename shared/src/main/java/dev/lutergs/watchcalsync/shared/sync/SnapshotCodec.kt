package dev.lutergs.watchcalsync.shared.sync

import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Encodes/decodes the snapshot payload. Lives in :shared so the phone and the watch
 * can never disagree about the wire format.
 */
object SnapshotCodec {

    fun encode(snapshot: CalendarSnapshot): ByteArray {
        val json = SyncContract.json.encodeToString(snapshot).toByteArray(Charsets.UTF_8)
        return ByteArrayOutputStream().use { out ->
            GZIPOutputStream(out).use { it.write(json) }
            out.toByteArray()
        }
    }

    fun decode(gzipped: ByteArray): CalendarSnapshot {
        val json = GZIPInputStream(gzipped.inputStream()).use { it.readBytes() }
            .toString(Charsets.UTF_8)
        return SyncContract.json.decodeFromString(json)
    }

    /**
     * Content fingerprint of the events, deliberately excluding
     * [CalendarSnapshot.generatedAtMillis] and the window bounds.
     *
     * The phone re-reads the calendar on a timer and on every provider change
     * notification, but the result is usually identical. Hashing only the events
     * lets an unchanged agenda skip the Data Layer write entirely instead of
     * pushing a byte-different payload — which the timestamp alone would guarantee
     * — over Bluetooth every cycle.
     */
    fun contentHash(snapshot: CalendarSnapshot): Int = snapshot.events.hashCode()
}
