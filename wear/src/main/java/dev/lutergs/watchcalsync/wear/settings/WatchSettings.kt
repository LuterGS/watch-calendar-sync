package dev.lutergs.watchcalsync.wear.settings

import android.content.Context

/** Shared by the watch app and the complication service; survives process restarts. */
class WatchSettings(context: Context) {
    private val preferences = context.getSharedPreferences("watch_settings", Context.MODE_PRIVATE)

    var eventMode: EventMode
        get() = EventMode.entries.firstOrNull { it.name == preferences.getString("event_mode", null) }
            ?: EventMode.UPCOMING
        set(value) { preferences.edit().putString("event_mode", value.name).apply() }

    var fontSize: AgendaFontSize
        get() = AgendaFontSize.entries.firstOrNull { it.name == preferences.getString("font_size", null) }
            ?: AgendaFontSize.NORMAL
        set(value) { preferences.edit().putString("font_size", value.name).apply() }
}
