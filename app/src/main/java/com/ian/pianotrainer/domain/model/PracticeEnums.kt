package com.ian.pianotrainer.domain.model

enum class PracticeMode(val labelResId: Int) {
    WAIT_FOR_NOTE(com.ian.pianotrainer.R.string.mode_wait_for_note),
    IN_TEMPO(com.ian.pianotrainer.R.string.mode_in_tempo)
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
