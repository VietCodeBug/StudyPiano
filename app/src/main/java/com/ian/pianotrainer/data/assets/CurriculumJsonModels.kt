package com.ian.pianotrainer.data.assets

import com.ian.pianotrainer.domain.model.Course
import com.ian.pianotrainer.domain.model.Exercise
import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.Lesson
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CurriculumJsonResponse(
    val courses: List<CourseJsonModel> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CourseJsonModel(
    val id: String,
    val title: String,
    val description: String,
    val difficulty: String,
    val lessons: List<LessonJsonModel> = emptyList()
)

@JsonClass(generateAdapter = true)
data class LessonJsonModel(
    val id: String,
    val title: String,
    val objective: String,
    val description: String,
    val estimatedDurationMinutes: Int,
    val handMode: String = "RIGHT",
    val targetMidiNotes: List<Int> = emptyList(),
    val exercise: ExerciseJsonModel? = null
)

@JsonClass(generateAdapter = true)
data class ExerciseJsonModel(
    val id: String,
    val title: String,
    val defaultBpm: Int = 60,
    val notes: List<ExerciseNoteJsonModel> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ExerciseNoteJsonModel(
    val midiNote: Int,
    val noteName: String = "",
    val durationBeats: Double = 1.0,
    val fingerNumber: Int = 1,
    val hand: String = "RIGHT"
)

fun CourseJsonModel.toDomain(): Course {
    return Course(
        id = id,
        title = title,
        description = description,
        difficulty = difficulty,
        lessons = lessons.map { it.toDomain(id) }
    )
}

fun LessonJsonModel.toDomain(courseId: String): Lesson {
    return Lesson(
        id = id,
        courseId = courseId,
        title = title,
        objective = objective,
        description = description,
        estimatedDurationMinutes = estimatedDurationMinutes,
        handMode = runCatching { HandMode.valueOf(handMode) }.getOrDefault(HandMode.RIGHT),
        targetMidiNotes = targetMidiNotes,
        exercise = exercise?.toDomain()
    )
}

fun ExerciseJsonModel.toDomain(): Exercise {
    return Exercise(
        id = id,
        title = title,
        defaultBpm = defaultBpm,
        notes = notes.map { it.toDomain() }
    )
}

fun ExerciseNoteJsonModel.toDomain(): ExerciseNote {
    return ExerciseNote(
        midiNote = midiNote,
        noteName = noteName,
        durationBeats = durationBeats,
        fingerNumber = fingerNumber,
        hand = runCatching { HandMode.valueOf(hand) }.getOrDefault(HandMode.RIGHT)
    )
}
