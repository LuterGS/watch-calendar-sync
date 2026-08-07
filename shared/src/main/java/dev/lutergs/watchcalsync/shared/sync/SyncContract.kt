package dev.lutergs.watchcalsync.shared.sync

import kotlinx.serialization.json.Json

/**
 * Paths and keys shared by both sides of the Data Layer. Changing anything here
 * requires reinstalling both APKs, so keep it stable.
 */
object SyncContract {
    /** DataClient item path carrying the latest [dev.lutergs.watchcalsync.shared.model.CalendarSnapshot]. */
    const val PATH_SNAPSHOT = "/calendar/snapshot"

    /** MessageClient path the watch uses to ask the phone for a fresh sync. */
    const val PATH_REQUEST_SYNC = "/calendar/request-sync"

    /**
     * DataMap key holding the snapshot as **gzipped** UTF-8 JSON.
     *
     * A DataItem is capped at 100 KB. Uncompressed this payload is a few hundred
     * bytes per event, so a busy two-week window on a work calendar can approach
     * that ceiling; calendar JSON is highly repetitive and gzips roughly 5-10x,
     * which keeps a comfortable margin. Compression is unconditional — there is no
     * "maybe compressed" case for the reader to handle.
     */
    const val KEY_SNAPSHOT_GZIP = "snapshot_json_gzip"

    /** Mirrors CalendarSnapshot.generatedAtMillis so the watch can show staleness. */
    const val KEY_GENERATED_AT = "generated_at"

    /**
     * Bumped whenever the snapshot payload changes shape, so a stale watch build
     * ignores data it cannot parse instead of crashing.
     */
    const val KEY_SCHEMA_VERSION = "schema_version"
    const val SCHEMA_VERSION = 1

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
