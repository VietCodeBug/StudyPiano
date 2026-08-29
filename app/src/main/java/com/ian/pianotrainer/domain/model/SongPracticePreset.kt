package com.ian.pianotrainer.domain.model

data class SongPracticePreset(
    val id: String,
    val songId: String,
    val name: String,
    val loopStartMs: Long? = null,
    val loopEndMs: Long? = null,
    val handMode: HandMode = HandMode.BOTH,
    val practiceMode: PracticeMode = PracticeMode.WAIT_FOR_NOTE,
    val targetBpm: Int = 120,
    val speedMultiplier: Float = 1.0f,
    val lookAhead: VisualLookAhead = VisualLookAhead.MEDIUM,
    val noteDisplaySize: NoteDisplaySize = NoteDisplaySize.AUTO,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
