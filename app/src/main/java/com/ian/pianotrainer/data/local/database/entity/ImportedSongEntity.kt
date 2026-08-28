package com.ian.pianotrainer.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ian.pianotrainer.domain.model.ImportedSong

@Entity(tableName = "imported_songs")
data class ImportedSongEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val originalFileName: String,
    val localFilePath: String?,
    val durationMs: Long?,
    val defaultBpm: Int,
    val difficulty: String,
    val importedAt: Long,
    val lastPracticedAt: Long?,
    val isFavorite: Boolean
)

fun ImportedSongEntity.toDomainModel(): ImportedSong {
    return ImportedSong(
        id = id,
        displayName = displayName,
        originalFileName = originalFileName,
        localFilePath = localFilePath,
        durationMs = durationMs,
        defaultBpm = defaultBpm,
        difficulty = difficulty,
        importedAt = importedAt,
        lastPracticedAt = lastPracticedAt,
        isFavorite = isFavorite
    )
}

fun ImportedSong.toEntity(): ImportedSongEntity {
    return ImportedSongEntity(
        id = id,
        displayName = displayName,
        originalFileName = originalFileName,
        localFilePath = localFilePath,
        durationMs = durationMs,
        defaultBpm = defaultBpm,
        difficulty = difficulty,
        importedAt = importedAt,
        lastPracticedAt = lastPracticedAt,
        isFavorite = isFavorite
    )
}
