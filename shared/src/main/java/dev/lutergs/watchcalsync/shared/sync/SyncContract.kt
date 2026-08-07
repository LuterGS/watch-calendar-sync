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

    /** DataMap key holding the UTF-8 JSON bytes of the snapshot. */
    const val KEY_SNAPSHOT_JSON = "snapshot_json"

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
