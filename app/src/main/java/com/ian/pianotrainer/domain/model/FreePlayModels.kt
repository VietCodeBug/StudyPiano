package com.ian.pianotrainer.domain.model

data class RecordedMidiEvent(
    val timestampMs: Long,
    val isNoteOn: Boolean,
    val note: Int,
    val velocity: Int,
    val channel: Int = 0
)

data class FreePlayRecording(
    val id: String,
    val title: String,
    val createdAt: Long,
    val durationMs: Long,
    val noteCount: Int,
    val hasAudio: Boolean = false,
    val audioFilePath: String? = null,
    val midiFilePath: String? = null,
    val events: List<RecordedMidiEvent> = emptyList()
)
