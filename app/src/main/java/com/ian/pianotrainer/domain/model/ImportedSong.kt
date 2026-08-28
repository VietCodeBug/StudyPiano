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
) {
    fun formattedDuration(): String {
        val ms = durationMs ?: return "00:00"
        val totalSec = ms / 1000
        val mins = totalSec / 60
        val secs = totalSec % 60
        return "%02d:%02d".format(mins, secs)
    }
}
