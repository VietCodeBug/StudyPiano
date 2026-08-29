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
    fun waitChord_orderIndependent() {
        val clock = MockPracticeClock(1000L)
        val engine = RealPracticeEngine(clock)

        // Chord: C4 (60), E4 (64), G4 (67) at startMs = 0L, followed by C5 (72) at 500L
        val notes = listOf(
            ExerciseNote(60, "C4", 1.0, 1, HandMode.RIGHT, startMs = 0L, durationMs = 400L, chordId = "c1"),
            ExerciseNote(64, "E4", 1.0, 3, HandMode.RIGHT, startMs = 0L, durationMs = 400L, chordId = "c1"),
            ExerciseNote(67, "G4", 1.0, 5, HandMode.RIGHT, startMs = 0L, durationMs = 400L, chordId = "c1"),
            ExerciseNote(72, "C5", 1.0, 5, HandMode.RIGHT, startMs = 500L, durationMs = 400L, chordId = "c2")
        )

        val config = PracticeConfiguration(
            title = "Wait Chord Test",
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

        // 1. Play G4 first (reverse order)
        engine.processPlayedNote(67, 80)
        assertEquals(0, engine.state.value.currentNoteIndex)
        assertEquals(1, engine.state.value.correctNotesCount)

        // 2. Play extra wrong note (55) -> should be counted wrong, but G4 remains hit
        engine.processPlayedNote(55, 80)
        assertEquals(0, engine.state.value.currentNoteIndex)
        assertEquals(1, engine.state.value.wrongNotesCount)
        assertEquals(1, engine.state.value.correctNotesCount)

        // 3. Play C4 second
        engine.processPlayedNote(60, 80)
        assertEquals(0, engine.state.value.currentNoteIndex)
        assertEquals(2, engine.state.value.correctNotesCount)

        // 4. Play E4 third -> full chord satisfied, advances to C5
        engine.processPlayedNote(64, 80)
        assertEquals(3, engine.state.value.currentNoteIndex)
        assertEquals(3, engine.state.value.correctNotesCount)
        assertEquals(72, engine.state.value.currentExpectedNote?.midiNote)

        // 5. Play C5 -> finishes
        engine.processPlayedNote(72, 80)
        assertTrue(engine.state.value.isFinished)
    }

    @Test
    fun rhythmChord_orderIndependent_and_partialHit() {
        val clock = MockPracticeClock(1000L)
        val engine = RealPracticeEngine(clock)

        // Chord 1: C4 (60), E4 (64), G4 (67) at 1000ms
        // Chord 2: D4 (62), F4 (65) at 2000ms
        val notes = listOf(
            ExerciseNote(60, "C4", 1.0, 1, HandMode.RIGHT, startMs = 1000L, durationMs = 400L, chordId = "ch1"),
            ExerciseNote(64, "E4", 1.0, 3, HandMode.RIGHT, startMs = 1000L, durationMs = 400L, chordId = "ch1"),
            ExerciseNote(67, "G4", 1.0, 5, HandMode.RIGHT, startMs = 1000L, durationMs = 400L, chordId = "ch1"),
            ExerciseNote(62, "D4", 1.0, 1, HandMode.RIGHT, startMs = 2000L, durationMs = 400L, chordId = "ch2"),
            ExerciseNote(65, "F4", 1.0, 3, HandMode.RIGHT, startMs = 2000L, durationMs = 400L, chordId = "ch2")
        )

        val config = PracticeConfiguration(
            title = "Rhythm Chord Test",
            sourceId = "rhythm_chords",
            sourceType = "TEST",
            practiceMode = PracticeMode.RHYTHM,
            handMode = HandMode.RIGHT,
            displayMode = DisplayMode.FALLING_NOTES,
            bpm = 120,
            notes = notes
        )

        engine.startPractice(config)

        // Advance to 1010ms (in timing window of Chord 1)
        clock.monotonicTime = 1000L + 1010L
        engine.tickTimer()

        // 1. Play G4 first (reverse order)
        engine.processPlayedNote(67, 80)
        assertEquals(1, engine.state.value.correctNotesCount)
        assertEquals(NoteResultType.CORRECT, engine.state.value.lastEvaluatedResult)

        // 2. Play C4 second
        engine.processPlayedNote(60, 80)
        assertEquals(2, engine.state.value.correctNotesCount)

        // 3. Play E4 third -> chord completed!
        engine.processPlayedNote(64, 80)
        assertEquals(3, engine.state.value.correctNotesCount)
        assertEquals(3, engine.state.value.currentNoteIndex) // Advances to next chord (index 3, D4)

        // Advance to 2000ms (Chord 2)
        clock.monotonicTime = 1000L + 2000L
        engine.tickTimer()

        // For Chord 2 (D4, F4): User only hits D4, misses F4
        engine.processPlayedNote(62, 80)
        assertEquals(4, engine.state.value.correctNotesCount)

        // Advance past Chord 2 expiration window (2000 + 180 + 50 = 2230ms)
        clock.monotonicTime = 1000L + 2250L
        engine.tickTimer()

        // F4 was not hit, so it should be counted as missed
        assertEquals(1, engine.state.value.missedNotesCount)
    }

    @Test
    fun speedChange_timelineAnchor_noPlayheadJumps() {
        val clock = MockPracticeClock(1000L)
        val engine = RealPracticeEngine(clock)

        val notes = listOf(
            ExerciseNote(60, "C4", 1.0, 1, HandMode.RIGHT, startMs = 0L, durationMs = 20000L)
        )

        val config = PracticeConfiguration(
            title = "Speed Anchor Test",
            sourceId = "speed_anchor",
            sourceType = "TEST",
            practiceMode = PracticeMode.RHYTHM,
            handMode = HandMode.RIGHT,
            displayMode = DisplayMode.FALLING_NOTES,
            bpm = 120,
            notes = notes
        )

        engine.startPractice(config)

        // 1. Run 10 seconds at 1.0x speed
        clock.monotonicTime = 1000L + 10000L
        engine.tickTimer()
        assertEquals(10000L, engine.state.value.currentPositionMs)
        assertEquals(10L, engine.state.value.elapsedActiveSeconds)

        // 2. Change speed to 0.5x at 10 seconds
        engine.setPlaybackSpeed(0.5f)
        assertEquals(0.5f, engine.state.value.speedMultiplier)

        // 3. Immediately tick -> playhead should remain at 10000ms without jumping backwards!
        engine.tickTimer()
        assertEquals(10000L, engine.state.value.currentPositionMs)

        // 4. Run 4 more seconds of real time at 0.5x speed -> song position advances by 2000ms (10000 + 2000 = 12000ms)
        clock.monotonicTime = 1000L + 14000L
        engine.tickTimer()
        assertEquals(12000L, engine.state.value.currentPositionMs)
        assertEquals(14L, engine.state.value.elapsedActiveSeconds) // Active practice time is real elapsed (14s)

        // 5. Change speed to 1.5x at 14 seconds
        engine.setPlaybackSpeed(1.5f)
        assertEquals(1.5f, engine.state.value.speedMultiplier)

        // 6. Immediately tick -> playhead should remain at 12000ms without jumping forwards!
        engine.tickTimer()
        assertEquals(12000L, engine.state.value.currentPositionMs)

        // 7. Run 2 more seconds of real time at 1.5x speed -> song position advances by 3000ms (12000 + 3000 = 15000ms)
        clock.monotonicTime = 1000L + 16000L
        engine.tickTimer()
        assertEquals(15000L, engine.state.value.currentPositionMs)
        assertEquals(16L, engine.state.value.elapsedActiveSeconds)
    }

    @Test
    fun seekTo_resetsChordHitState_and_preservesAccumulatedTime() {
        val clock = MockPracticeClock(1000L)
        val engine = RealPracticeEngine(clock)

        val notes = listOf(
            ExerciseNote(60, "C4", 1.0, 1, HandMode.RIGHT, startMs = 0L, durationMs = 400L, chordId = "c1"),
            ExerciseNote(64, "E4", 1.0, 3, HandMode.RIGHT, startMs = 0L, durationMs = 400L, chordId = "c1"),
            ExerciseNote(62, "D4", 1.0, 1, HandMode.RIGHT, startMs = 5000L, durationMs = 400L, chordId = "c2"),
            ExerciseNote(65, "F4", 1.0, 3, HandMode.RIGHT, startMs = 5000L, durationMs = 400L, chordId = "c2")
        )

        val config = PracticeConfiguration(
            title = "Seek Chord Reset",
            sourceId = "seek_reset",
            sourceType = "TEST",
            practiceMode = PracticeMode.WAIT_FOR_NOTE,
            handMode = HandMode.RIGHT,
            displayMode = DisplayMode.FALLING_NOTES,
            bpm = 120,
            notes = notes
        )

        engine.startPractice(config)

        // Run for 3 seconds
        clock.monotonicTime = 1000L + 3000L
        engine.tickTimer()
        assertEquals(3L, engine.state.value.elapsedActiveSeconds)

        // Partially hit first note of chord 1 (C4)
        engine.processPlayedNote(60, 80)
        assertEquals(1, engine.state.value.correctNotesCount)

        // Seek to 5000ms (Chord 2: D4, F4)
        engine.seekTo(5000L)
        assertEquals(2, engine.state.value.currentNoteIndex) // Points to D4
        assertEquals(62, engine.state.value.currentExpectedNote?.midiNote)
        assertEquals(3L, engine.state.value.elapsedActiveSeconds) // Accumulated time preserved

        // User now plays D4 -> correct for Chord 2
        engine.processPlayedNote(62, 80)
        assertEquals(2, engine.state.value.correctNotesCount)

        // User plays F4 -> Chord 2 complete
        engine.processPlayedNote(65, 80)
        assertEquals(3, engine.state.value.correctNotesCount)
        assertTrue(engine.state.value.isFinished)
    }

    @Test
    fun speedMultiplier_clampedBetween025and15() {
        val clock = MockPracticeClock(1000L)
        val engine = RealPracticeEngine(clock)

        val config = PracticeConfiguration(
            title = "Speed Clamp",
            sourceId = "clamp",
            sourceType = "TEST",
            practiceMode = PracticeMode.RHYTHM,
            handMode = HandMode.RIGHT,
            displayMode = DisplayMode.FALLING_NOTES,
            bpm = 120,
            notes = listOf(ExerciseNote(60, "C4", 1.0, 1, HandMode.RIGHT, startMs = 0L, durationMs = 1000L))
        )

        engine.startPractice(config)

        engine.setPlaybackSpeed(0.1f)
        assertEquals(0.25f, engine.state.value.speedMultiplier, 0.001f)

        engine.setPlaybackSpeed(2.5f)
        assertEquals(1.5f, engine.state.value.speedMultiplier, 0.001f)
    }
}
