package dev.lutergs.watchcalsync.wear.settings

enum class EventMode(val label: String) {
    UPCOMING("다가오는 일정"),
    ALL_DAY("종일 일정"),
}

enum class AgendaFontSize(val label: String, val scale: Float) {
    SMALL("작게", 0.85f), NORMAL("보통", 1f), LARGE("크게", 1.2f),
}
