package com.ian.pianotrainer.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.PracticeMode
import com.ian.pianotrainer.domain.model.PracticeSession

@Entity(tableName = "practice_sessions")
data class PracticeSessionEntity(
    @PrimaryKey val id: String,
    val sourceType: String,
    val sourceId: String?,
    val practiceMode: String,
    val handMode: String,
    val displayMode: String,
    val bpm: Int,
    val startedAt: Long,
    val durationMs: Long,
    val totalExpectedNotes: Int,
    val correctNotes: Int,
    val wrongNotes: Int,
    val missedNotes: Int,
    val earlyNotes: Int,
    val lateNotes: Int,
    val accuracy: Float
)

fun PracticeSessionEntity.toDomainModel(): PracticeSession {
    return PracticeSession(
        id = id,
        sourceType = sourceType,
        sourceId = sourceId,
        practiceMode = runCatching { PracticeMode.valueOf(practiceMode) }.getOrDefault(PracticeMode.WAIT_FOR_NOTE),
        handMode = runCatching { HandMode.valueOf(handMode) }.getOrDefault(HandMode.RIGHT),
        displayMode = runCatching { DisplayMode.valueOf(displayMode) }.getOrDefault(DisplayMode.FALLING_NOTES),
        bpm = bpm,
        startedAt = startedAt,
        durationMs = durationMs,
        totalExpectedNotes = totalExpectedNotes,
        correctNotes = correctNotes,
        wrongNotes = wrongNotes,
        missedNotes = missedNotes,
        earlyNotes = earlyNotes,
        lateNotes = lateNotes,
        accuracy = accuracy
    )
}

fun PracticeSession.toEntity(): PracticeSessionEntity {
    return PracticeSessionEntity(
        id = id,
        sourceType = sourceType,
        sourceId = sourceId,
        practiceMode = practiceMode.name,
        handMode = handMode.name,
        displayMode = displayMode.name,
        bpm = bpm,
        startedAt = startedAt,
        durationMs = durationMs,
        totalExpectedNotes = totalExpectedNotes,
        correctNotes = correctNotes,
        wrongNotes = wrongNotes,
        missedNotes = missedNotes,
        earlyNotes = earlyNotes,
        lateNotes = lateNotes,
        accuracy = accuracy
    )
}
