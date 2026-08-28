package com.ian.pianotrainer.domain.model

data class Course(
    val id: String,
    val title: String,
    val description: String,
    val difficulty: String,
    val lessons: List<Lesson> = emptyList(),
    val isLocked: Boolean = false,
    val completionPercent: Int = 0
)

data class Lesson(
    val id: String,
    val courseId: String = "",
    val title: String,
    val objective: String,
    val description: String,
    val estimatedDurationMinutes: Int,
    val handMode: HandMode = HandMode.RIGHT,
    val targetMidiNotes: List<Int> = emptyList(),
    val exercise: Exercise? = null,
    val isCompleted: Boolean = false,
    val bestAccuracy: Float = 0f
)

data class Exercise(
    val id: String,
    val title: String,
    val defaultBpm: Int = 60,
    val notes: List<ExerciseNote> = emptyList()
)

data class ExerciseNote(
    val midiNote: Int,
    val noteName: String = "",
    val durationBeats: Double = 1.0,
    val fingerNumber: Int = 1,
    val hand: HandMode = HandMode.RIGHT
)
