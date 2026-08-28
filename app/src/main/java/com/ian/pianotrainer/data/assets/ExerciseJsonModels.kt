package com.ian.pianotrainer.data.assets

import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.FingerExercise
import com.ian.pianotrainer.domain.model.HandMode
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FingerExercisesJsonResponse(
    val schemaVersion: Int = 1,
    val exercises: List<FingerExerciseJsonModel> = emptyList()
)

@JsonClass(generateAdapter = true)
data class FingerExerciseJsonModel(
    val id: String,
    val title: String,
    val description: String,
    val difficulty: String,
    val category: String,
    val recommendedBpm: Int,
    val minBpm: Int = 40,
    val maxBpm: Int = 140,
    val handMode: String = "RIGHT",
    val noteCount: Int,
    val targetDurationSeconds: Int = 300,
    val notes: List<ExerciseNoteJsonModel> = emptyList()
)

fun FingerExerciseJsonModel.toDomain(): FingerExercise {
    val msPerBeat = (60000.0 / recommendedBpm.coerceIn(30, 240))
    var currentStartMs = 0L
    val domainNotes = notes.map { jsonNote ->
        val durationMs = (jsonNote.durationBeats * msPerBeat).toLong().coerceAtLeast(150L)
        val note = ExerciseNote(
            midiNote = jsonNote.midiNote,
            noteName = jsonNote.noteName,
            durationBeats = jsonNote.durationBeats,
            fingerNumber = jsonNote.fingerNumber,
            hand = runCatching { HandMode.valueOf(jsonNote.hand) }.getOrDefault(HandMode.RIGHT),
            startMs = currentStartMs,
            durationMs = durationMs
        )
        currentStartMs += durationMs
        note
    }
    return FingerExercise(
        id = id,
        title = title,
        description = description,
        difficulty = difficulty,
        category = category,
        recommendedBpm = recommendedBpm,
        minBpm = minBpm,
        maxBpm = maxBpm,
        handMode = runCatching { HandMode.valueOf(handMode) }.getOrDefault(HandMode.RIGHT),
        noteCount = noteCount,
        targetDurationSeconds = targetDurationSeconds,
        notes = domainNotes
    )
}
