package com.ian.pianotrainer.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ian.pianotrainer.domain.model.NoteResultType
import com.ian.pianotrainer.domain.model.PracticeNoteResult

@Entity(
    tableName = "practice_note_results",
    foreignKeys = [
        ForeignKey(
            entity = PracticeSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"])
    ]
)
data class PracticeNoteResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val expectedMidiNote: Int?,
    val playedMidiNote: Int?,
    val timingOffsetMs: Long?,
    val resultType: String,
    val occurredAtOffsetMs: Long
)

fun PracticeNoteResultEntity.toDomainModel(): PracticeNoteResult {
    return PracticeNoteResult(
        id = id,
        sessionId = sessionId,
        expectedMidiNote = expectedMidiNote,
        playedMidiNote = playedMidiNote,
        timingOffsetMs = timingOffsetMs,
        resultType = runCatching { NoteResultType.valueOf(resultType) }.getOrDefault(NoteResultType.CORRECT),
        occurredAtOffsetMs = occurredAtOffsetMs
    )
}

fun PracticeNoteResult.toEntity(): PracticeNoteResultEntity {
    return PracticeNoteResultEntity(
        id = id,
        sessionId = sessionId,
        expectedMidiNote = expectedMidiNote,
        playedMidiNote = playedMidiNote,
        timingOffsetMs = timingOffsetMs,
        resultType = resultType.name,
        occurredAtOffsetMs = occurredAtOffsetMs
    )
}
