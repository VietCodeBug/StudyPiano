package com.ian.pianotrainer.domain.model

data class VisualNoteFeedback(
    val noteId: String? = null,
    val chordId: String? = null,
    val midiNote: Int = 0,
    val startMs: Long = 0L,
    val result: NoteResultType = NoteResultType.CORRECT,
    val eventTimestampMs: Long = 0L
)
