package com.ian.pianotrainer

import com.ian.pianotrainer.core.music.PracticeClock
import com.ian.pianotrainer.data.practice.RealPracticeEngine
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.NoteResultType
import com.ian.pianotrainer.domain.model.PracticeConfiguration
import com.ian.pianotrainer.domain.model.PracticeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeEngineUnitTest {

    class MockPracticeClock(
        var monotonicTime: Long = 1000L,
        var wallTime: Long = 1700000000000L
    ) : PracticeClock {
        override fun elapsedRealtime(): Long = monotonicTime
        override fun currentTimeMillis(): Long = wallTime
    }

    @Test
    fun chord_orderIndependent_waitForNote() {
        val clock = MockPracticeClock(1000L)
        val engine = RealPracticeEngine(clock)

        // Chord: C4 (60), E4 (64), G4 (67) at startMs = 0L, followed by C5 (72) at 500L
        val notes = listOf(
            ExerciseNote(60, "C4", 1.0, 1, HandMode.RIGHT, startMs = 0L, durationMs = 400L),
            ExerciseNote(64, "E4", 1.0, 3, HandMode.RIGHT, startMs = 0L, durationMs = 400L),
            ExerciseNote(67, "G4", 1.0, 5, HandMode.RIGHT, startMs = 0L, durationMs = 400L),
            ExerciseNote(72, "C5", 1.0, 5, HandMode.RIGHT, startMs = 500L, durationMs = 400L)
        )

        val config = PracticeConfiguration(
            title = "Chord Test",
            sourceId = "chord_1",
            sourceType = "TEST",
            practiceMode = PracticeMode.WAIT_FOR_NOTE,
            handMode = HandMode.RIGHT,
            displayMode = DisplayMode.FALLING_NOTES,
            bpm = 120,
            notes = notes
        )

        engine.startPractice(config)
        assertEquals(0, engine.state.value.currentNoteIndex)
        assertEquals(3, engine.state.value.currentExpectedNotes.size)

        // 1. Play G4 first (reverse order)
        engine.processPlayedNote(67, 80)
        assertEquals(0, engine.state.value.currentNoteIndex) // Still at chord
        assertEquals(1, engine.state.value.correctNotesCount)

        // 2. Play C4 second
        engine.processPlayedNote(60, 80)
        assertEquals(0, engine.state.value.currentNoteIndex) // Still at chord
        assertEquals(2, engine.state.value.correctNotesCount)

        // 3. Play E4 third -> chord completed!
        engine.processPlayedNote(64, 80)
        assertEquals(3, engine.state.value.currentNoteIndex) // Advanced past chord (3 notes) to index 3 (C5)
        assertEquals(3, engine.state.value.correctNotesCount)
        assertEquals(72, engine.state.value.currentExpectedNote?.midiNote)

        // 4. Play C5 -> Finish
        engine.processPlayedNote(72, 80)
        assertTrue(engine.state.value.isFinished)
    }

    @Test
    fun rhythmMode_scoringAccuracy_earlyLateCorrectMissed() {
        val clock = MockPracticeClock(1000L)
        val engine = RealPracticeEngine(clock)

        // 3 notes at 1000ms, 2000ms, 3000ms
        val notes = listOf(
            ExerciseNote(60, "C4", 1.0, 1, HandMode.RIGHT, startMs = 1000L, durationMs = 400L),
            ExerciseNote(62, "D4", 1.0, 2, HandMode.RIGHT, startMs = 2000L, durationMs = 400L),
            ExerciseNote(64, "E4", 1.0, 3, HandMode.RIGHT, startMs = 3000L, durationMs = 400L)
        )

        val config = PracticeConfiguration(
            title = "Rhythm Scoring",
            sourceId = "rhythm_1",
            sourceType = "TEST",
            practiceMode = PracticeMode.RHYTHM,
            handMode = HandMode.RIGHT,
            displayMode = DisplayMode.FALLING_NOTES,
            bpm = 120,
            notes = notes
        )

        engine.startPractice(config)

        // 1. Advance clock to 920ms (80ms early -> EARLY)
        clock.monotonicTime = 1000L + 920L
        engine.tickTimer()
        engine.processPlayedNote(60, 80)
        assertEquals(NoteResultType.EARLY, engine.state.value.lastEvaluatedResult)
        assertEquals(1, engine.state.value.earlyNotesCount)
        assertEquals(1, engine.state.value.currentNoteIndex)

        // 2. Advance clock to 2010ms (10ms diff -> CORRECT)
        clock.monotonicTime = 1000L + 2010L
        engine.tickTimer()
        engine.processPlayedNote(62, 80)
        assertEquals(NoteResultType.CORRECT, engine.state.value.lastEvaluatedResult)
        assertEquals(2, engine.state.value.correctNotesCount)

        // 3. Advance clock to 3400ms without playing -> Note 3 expired (MISSED)
        clock.monotonicTime = 1000L + 3400L
        engine.tickTimer()
        assertEquals(1, engine.state.value.missedNotesCount)

        val result = engine.stop()
        assertEquals(1, result.earlyNotes)
        assertEquals(1, result.missedNotes)
    }

    @Test
    fun seekTo_updatesExpectedNotesAndClearsHits() {
        val clock = MockPracticeClock(1000L)
        val engine = RealPracticeEngine(clock)

        val notes = listOf(
            ExerciseNote(60, "C4", 1.0, 1, HandMode.RIGHT, startMs = 0L, durationMs = 400L),
            ExerciseNote(62, "D4", 1.0, 2, HandMode.RIGHT, startMs = 1000L, durationMs = 400L),
            ExerciseNote(64, "E4", 1.0, 3, HandMode.RIGHT, startMs = 2000L, durationMs = 400L),
            ExerciseNote(65, "F4", 1.0, 4, HandMode.RIGHT, startMs = 3000L, durationMs = 400L)
        )

        val config = PracticeConfiguration(
            title = "Seek Test",
            sourceId = "seek_1",
            sourceType = "TEST",
            practiceMode = PracticeMode.WAIT_FOR_NOTE,
            handMode = HandMode.RIGHT,
            displayMode = DisplayMode.FALLING_NOTES,
            bpm = 120,
            notes = notes
        )

        engine.startPractice(config)
        assertEquals(0, engine.state.value.currentNoteIndex)

        // Seek forward to 1900ms -> should expect note at 2000ms (E4, index 2)
        engine.seekTo(1900L)
        assertEquals(2, engine.state.value.currentNoteIndex)
        assertEquals(64, engine.state.value.currentExpectedNote?.midiNote)

        // Play note E4
        engine.processPlayedNote(64, 80)
        assertEquals(3, engine.state.value.currentNoteIndex)
        assertEquals(65, engine.state.value.currentExpectedNote?.midiNote)

        // Seek backward to 500ms -> should expect note at 1000ms (D4, index 1)
        engine.seekTo(500L)
        assertEquals(1, engine.state.value.currentNoteIndex)
        assertEquals(62, engine.state.value.currentExpectedNote?.midiNote)
    }

    @Test
    fun loopAB_byMilliseconds_wrapAround() {
        val clock = MockPracticeClock(1000L)
        val engine = RealPracticeEngine(clock)

        val notes = listOf(
            ExerciseNote(60, "C4", 1.0, 1, HandMode.RIGHT, startMs = 0L, durationMs = 400L),
            ExerciseNote(62, "D4", 1.0, 2, HandMode.RIGHT, startMs = 1000L, durationMs = 400L),
            ExerciseNote(64, "E4", 1.0, 3, HandMode.RIGHT, startMs = 2000L, durationMs = 400L),
            ExerciseNote(65, "F4", 1.0, 4, HandMode.RIGHT, startMs = 3000L, durationMs = 400L)
        )

        val config = PracticeConfiguration(
            title = "Loop AB Test",
            sourceId = "loop_1",
            sourceType = "TEST",
            practiceMode = PracticeMode.RHYTHM,
            handMode = HandMode.RIGHT,
            displayMode = DisplayMode.FALLING_NOTES,
            bpm = 120,
            notes = notes
        )

        engine.startPractice(config)
        // Set Loop between 1000ms (Point A) and 2500ms (Point B)
        engine.setLoopRangeMs(1000L, 2500L)
        assertEquals(1000L, engine.state.value.loopStartMs)
        assertEquals(2500L, engine.state.value.loopEndMs)

        // Advance to 2600ms -> exceeds Loop B (2500ms) -> wraps to Loop A (1000ms) and increments lapCount
        clock.monotonicTime = 1000L + 2600L
        engine.tickTimer()

        assertEquals(2, engine.state.value.lapCount)
        assertEquals(1000L, engine.state.value.currentPositionMs)
        assertEquals(1, engine.state.value.currentNoteIndex) // Note D4 at 1000ms
        assertFalse(engine.state.value.isFinished)
    }

    @Test
    fun timer_pauseResume_noDoubleCounting() {
        val clock = MockPracticeClock(1000L)
        val engine = RealPracticeEngine(clock)

        val notes = listOf(
            ExerciseNote(60, "C4", 1.0, 1, HandMode.RIGHT, startMs = 0L, durationMs = 400L)
        )

        val config = PracticeConfiguration(
            title = "Timer Test",
            sourceId = "timer_1",
            sourceType = "TEST",
            practiceMode = PracticeMode.WAIT_FOR_NOTE,
            handMode = HandMode.RIGHT,
            displayMode = DisplayMode.FALLING_NOTES,
            bpm = 120,
            notes = notes
        )

        engine.startPractice(config)

        // Run for 3000ms
        clock.monotonicTime = 1000L + 3000L
        engine.tickTimer()
        assertEquals(3L, engine.state.value.elapsedActiveSeconds)

        // Pause at 3000ms
        engine.pause()
        assertTrue(engine.state.value.isPaused)

        // 5000ms pass while paused
        clock.monotonicTime = 1000L + 8000L
        engine.tickTimer()
        assertEquals(3L, engine.state.value.elapsedActiveSeconds) // Still 3 seconds!

        // Resume and run for another 2000ms
        engine.resume()
        clock.monotonicTime = 1000L + 10000L
        engine.tickTimer()
        assertEquals(5L, engine.state.value.elapsedActiveSeconds) // 3s + 2s = 5s total
    }

    @Test
    fun speedMultiplier_scalesRhythmPlayhead() {
        val clock = MockPracticeClock(1000L)
        val engine = RealPracticeEngine(clock)

        val notes = listOf(
            ExerciseNote(60, "C4", 1.0, 1, HandMode.RIGHT, startMs = 0L, durationMs = 400L),
            ExerciseNote(62, "D4", 1.0, 2, HandMode.RIGHT, startMs = 2000L, durationMs = 400L)
        )

        val config = PracticeConfiguration(
            title = "Speed Test",
            sourceId = "speed_1",
            sourceType = "TEST",
            practiceMode = PracticeMode.RHYTHM,
            handMode = HandMode.RIGHT,
            displayMode = DisplayMode.FALLING_NOTES,
            bpm = 120,
            notes = notes
        )

        engine.startPractice(config)
        // Set speed to 0.5x
        engine.setPlaybackSpeed(0.5f)

        // 1000ms real time passes -> playhead should advance by 500ms (0.5x speed)
        clock.monotonicTime = 1000L + 1000L
        engine.tickTimer()
        assertEquals(500L, engine.state.value.currentPositionMs)
    }
}
