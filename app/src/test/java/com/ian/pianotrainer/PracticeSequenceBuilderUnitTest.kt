package com.ian.pianotrainer

import com.ian.pianotrainer.core.music.PracticeSequenceBuilder
import com.ian.pianotrainer.domain.model.ExerciseNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeSequenceBuilderUnitTest {
    @Test
    fun `short phrase is repeated into a useful practice session`() {
        val result = PracticeSequenceBuilder.repeatToMinimumDuration(
            notes = listOf(
                ExerciseNote(midiNote = 60, durationBeats = 1.0),
                ExerciseNote(midiNote = 62, durationBeats = 1.0),
                ExerciseNote(midiNote = 64, durationBeats = 2.0)
            ),
            bpm = 60,
            minimumDurationMs = 30_000L
        )

        assertTrue(result.size > 3)
        assertTrue(result.maxOf { it.startMs + it.durationMs } >= 29_000L)
        assertEquals(listOf(60, 62, 64), result.take(3).map { it.midiNote })
    }

    @Test
    fun `selected bpm controls generated exercise timing`() {
        val note = ExerciseNote(midiNote = 60, durationBeats = 1.0)

        val slow = PracticeSequenceBuilder.repeatToMinimumDuration(listOf(note), 60, 1_000L)
        val fast = PracticeSequenceBuilder.repeatToMinimumDuration(listOf(note), 120, 500L)

        assertEquals(1_000L, slow.first().durationMs)
        assertEquals(500L, fast.first().durationMs)
    }
}
