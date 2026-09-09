package com.ian.pianotrainer.core.music

import com.ian.pianotrainer.domain.model.ExerciseNote
import kotlin.math.ceil

/** Builds a real practice-length timeline from the short phrases stored in app assets. */
object PracticeSequenceBuilder {
    fun repeatToMinimumDuration(
        notes: List<ExerciseNote>,
        bpm: Int,
        minimumDurationMs: Long
    ): List<ExerciseNote> {
        if (notes.isEmpty()) return emptyList()

        val beatMs = (60_000.0 / bpm.coerceIn(30, 240)).toLong()
        var phraseCursorMs = 0L
        val phrase = notes.map { note ->
            val durationMs = (note.durationBeats * beatMs).toLong().coerceAtLeast(150L)
            note.copy(startMs = phraseCursorMs, durationMs = durationMs).also {
                phraseCursorMs += durationMs
            }
        }

        val restBetweenPhrasesMs = beatMs
        val cycleDurationMs = phraseCursorMs + restBetweenPhrasesMs
        val repeatCount = ceil(
            minimumDurationMs.coerceAtLeast(phraseCursorMs).toDouble() / cycleDurationMs.toDouble()
        ).toInt().coerceIn(1, 64)

        return buildList(phrase.size * repeatCount) {
            repeat(repeatCount) { repetition ->
                val offsetMs = repetition * cycleDurationMs
                phrase.forEach { note ->
                    add(
                        note.copy(
                            startMs = note.startMs + offsetMs,
                            chordId = note.chordId?.let { "repeat_${repetition}_$it" }
                        )
                    )
                }
            }
        }
    }
}
