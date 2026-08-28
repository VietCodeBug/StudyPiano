package com.ian.pianotrainer.domain.model

enum class PracticeMode(val labelResId: Int) {
    WAIT_FOR_NOTE(com.ian.pianotrainer.R.string.mode_wait_for_note),
    RHYTHM(com.ian.pianotrainer.R.string.mode_in_tempo)
}

enum class KeyboardRangeMode(val label: String) {
    AUTO("Tự động"),
    TWO_OCTAVES("2 Quãng tám"),
    FOUR_OCTAVES("4 Quãng tám"),
    SIX_OCTAVES("6 Quãng tám"),
    FULL_88_KEYS("Toàn bộ 88 phím")
}

enum class NoteDisplaySize(val label: String, val laneMarginRatio: Float) {
    AUTO("Tự động", 0.14f),
    SMALL("Nhỏ", 0.28f),
    MEDIUM("Vừa", 0.14f),
    LARGE("Lớn", 0.05f)
}

enum class VisualLookAhead(val lookAheadMs: Long, val label: String) {
    SLOW(6000L, "Chậm (6s)"),
    MEDIUM(4000L, "Vừa (4s)"),
    FAST(2500L, "Nhanh (2.5s)")
}

enum class TrailDuration(val durationMs: Long, val label: String) {
    SHORT(3000L, "3 giây"),
    MEDIUM(5000L, "5 giây"),
    LONG(8000L, "8 giây")
}

enum class HandMode(val labelResId: Int) {
    RIGHT(com.ian.pianotrainer.R.string.hand_right),
    LEFT(com.ian.pianotrainer.R.string.hand_left),
    BOTH(com.ian.pianotrainer.R.string.hand_both)
}

enum class DisplayMode(val labelResId: Int) {
    FALLING_NOTES(com.ian.pianotrainer.R.string.display_falling_notes),
    SHEET_MUSIC(com.ian.pianotrainer.R.string.display_sheet_music)
}

enum class NoteNamingMode(val labelResId: Int) {
    CDE(com.ian.pianotrainer.R.string.naming_cde),
    DOREMI(com.ian.pianotrainer.R.string.naming_doremi)
}

enum class NoteResultType {
    CORRECT,
    WRONG,
    MISSED,
    EARLY,
    LATE
}

enum class DeviceConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    ERROR
}
