package com.ian.pianotrainer.domain.model

data class ImportedSong(
    val id: String,
    val displayName: String,
    val originalFileName: String,
    val localFilePath: String? = null,
    val durationMs: Long? = null,
    val defaultBpm: Int = 60,
    val difficulty: String = "Cơ bản",
    val importedAt: Long = System.currentTimeMillis(),
    val lastPracticedAt: Long? = null,
    val isFavorite: Boolean = false,
    val notes: List<ExerciseNote> = emptyList()
)
