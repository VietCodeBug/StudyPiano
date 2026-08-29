package com.ian.pianotrainer

import com.ian.pianotrainer.core.music.VisibleNoteWindowSelector
import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.HandMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VisibleNoteWindowUnitTest {

    @Test
    fun emptyNotesList_returnsEmptyRange() {
        val selector = VisibleNoteWindowSelector(emptyList())
        val range = selector.getVisibleNoteRange(1000L, 5000L)
        assertTrue(range.isEmpty())
    }

    @Test
    fun singleNote_insideWindow_returnsValidRange() {
        val note = ExerciseNote(midiNote = 60, startMs = 2000L, durationMs = 500L, hand = HandMode.RIGHT)
        val selector = VisibleNoteWindowSelector(listOf(note))

        val insideRange = selector.getVisibleNoteRange(1000L, 3000L)
        assertEquals(0..0, insideRange)

        val outsideRange = selector.getVisibleNoteRange(4000L, 6000L)
        assertTrue(outsideRange.isEmpty())
    }

    @Test
    fun longNote_startingBeforeWindow_isCorrectlyIncluded() {
        // A chord note with huge duration (e.g. whole note held 8000ms starting at 0ms)
        val longNote = ExerciseNote(midiNote = 48, startMs = 0L, durationMs = 8000L, hand = HandMode.LEFT)
        val shortNote1 = ExerciseNote(midiNote = 60, startMs = 1000L, durationMs = 200L, hand = HandMode.RIGHT)
        val shortNote2 = ExerciseNote(midiNote = 64, startMs = 2000L, durationMs = 200L, hand = HandMode.RIGHT)

        val notes = listOf(longNote, shortNote1, shortNote2)
        val selector = VisibleNoteWindowSelector(notes)

        // Query window from 3000ms to 4000ms.
        // Even though longNote started at 0ms, it is still active until 8000ms!
        val range = selector.getVisibleNoteRange(3000L, 4000L)
        assertFalse(range.isEmpty())
        assertTrue("Index 0 (longNote) must be within search range", 0 in range)
    }

    @Test
    fun largeScaleNotes_100kNotes_binarySearchCompletesInstantly() {
        val count = 100_000
        val notes = ArrayList<ExerciseNote>(count)
        val rnd = Random(42)

        var timeMs = 0L
        for (i in 0 until count) {
            timeMs += rnd.nextLong(10L, 100L)
            val dur = rnd.nextLong(100L, 2000L)
            notes.add(
                ExerciseNote(
                    midiNote = 60 + (i % 24),
                    startMs = timeMs,
                    durationMs = dur,
                    hand = if (i % 2 == 0) HandMode.RIGHT else HandMode.LEFT
                )
            )
        }

        val selector = VisibleNoteWindowSelector(notes)

        val startBenchmark = System.nanoTime()
        // Query multiple visible windows
        for (q in 0..1000) {
            val windowStart = (q * 1000L)
            val windowEnd = windowStart + 4000L
            val range = selector.getVisibleNoteRange(windowStart, windowEnd)
            assertNotNull(range)
        }
        val elapsedMs = (System.nanoTime() - startBenchmark) / 1_000_000L
        assertTrue("1000 binary search queries on 100k notes must finish in under 50ms (took ${elapsedMs}ms)", elapsedMs < 50)
    }

    private fun assertNotNull(obj: Any?) {
        org.junit.Assert.assertNotNull(obj)
    }
}
