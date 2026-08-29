package com.ian.pianotrainer.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.NoteDisplaySize
import com.ian.pianotrainer.domain.model.PracticeMode
import com.ian.pianotrainer.domain.model.SongPracticePreset
import com.ian.pianotrainer.domain.model.VisualLookAhead

@Entity(
    tableName = "song_practice_presets",
    foreignKeys = [
        ForeignKey(
            entity = ImportedSongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["songId"])
    ]
)
data class SongPracticePresetEntity(
    @PrimaryKey val id: String,
    val songId: String,
    val name: String,
    val loopStartMs: Long?,
    val loopEndMs: Long?,
    val handMode: String = "BOTH",
    val practiceMode: String = "WAIT_FOR_NOTE",
    val targetBpm: Int = 120,
    val speedMultiplier: Float = 1.0f,
    val lookAhead: Int = 4000,
    val noteDisplaySize: String = "AUTO",
    val createdAt: Long,
    val updatedAt: Long
)

fun SongPracticePresetEntity.toDomain(): SongPracticePreset {
    val lookAheadEnum = VisualLookAhead.values().find { it.lookAheadMs.toInt() == lookAhead } ?: VisualLookAhead.MEDIUM
    val noteSizeEnum = runCatching { NoteDisplaySize.valueOf(noteDisplaySize) }.getOrDefault(NoteDisplaySize.AUTO)

    return SongPracticePreset(
        id = id,
        songId = songId,
        name = name,
        loopStartMs = loopStartMs,
        loopEndMs = loopEndMs,
        handMode = runCatching { HandMode.valueOf(handMode) }.getOrDefault(HandMode.BOTH),
        practiceMode = runCatching { PracticeMode.valueOf(practiceMode) }.getOrDefault(PracticeMode.WAIT_FOR_NOTE),
        targetBpm = targetBpm,
        speedMultiplier = speedMultiplier,
        lookAhead = lookAheadEnum,
        noteDisplaySize = noteSizeEnum,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun SongPracticePreset.toEntity(): SongPracticePresetEntity {
    return SongPracticePresetEntity(
        id = id,
        songId = songId,
        name = name,
        loopStartMs = loopStartMs,
        loopEndMs = loopEndMs,
        handMode = handMode.name,
        practiceMode = practiceMode.name,
        targetBpm = targetBpm,
        speedMultiplier = speedMultiplier,
        lookAhead = lookAhead.lookAheadMs.toInt(),
        noteDisplaySize = noteDisplaySize.name,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
