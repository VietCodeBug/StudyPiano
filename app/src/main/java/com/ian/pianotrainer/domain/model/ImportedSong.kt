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
    val trackCount: Int = 1,
    val noteCount: Int = 0,
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

data class SongTimeSignature(
    val startTick: Long,
    val startMs: Long,
    val numerator: Int,
    val denominator: Int
)

data class SongPlaybackData(
    val song: ImportedSong,
    val notes: List<ExerciseNote>,
    val tracks: List<SongTrackInfo>,
    val tempos: List<SongTempoInfo>,
    val timeSignatures: List<SongTimeSignature>
)

data class SongTrackInfo(
    val trackIndex: Int,
    val trackName: String,
    val channelSummary: String,
    val instrumentNumber: Int?,
    val noteCount: Int,
    val minMidiNote: Int,
    val maxMidiNote: Int,
    val isSelectedForPractice: Boolean,
    val assignedHand: String
)

data class SongTempoInfo(
    val startTick: Long,
    val startMs: Long,
    val microsecondsPerQuarterNote: Long,
    val bpm: Int
)
