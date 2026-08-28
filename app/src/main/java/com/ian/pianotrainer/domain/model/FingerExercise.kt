package com.ian.pianotrainer.domain.model

data class FingerExercise(
    val id: String,
    val title: String,
    val description: String,
    val difficulty: String,
    val category: String,
    val recommendedBpm: Int,
    val minBpm: Int,
    val maxBpm: Int,
    val handMode: HandMode,
    val noteCount: Int,
    val targetDurationSeconds: Int = 300,
    val notes: List<ExerciseNote> = emptyList(),
    val totalPracticedSeconds: Long = 0L,
    val todayPracticedSeconds: Long = 0L,
    val lastPracticedAt: Long? = null
)
