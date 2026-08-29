package com.ian.pianotrainer

import com.ian.pianotrainer.core.music.SectionSlicer
import com.ian.pianotrainer.data.practice.RealPracticeEngine
import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.PracticeConfiguration
import com.ian.pianotrainer.domain.model.PracticeMode
import com.ian.pianotrainer.domain.model.SectionPracticeStep
import com.ian.pianotrainer.domain.model.SongTempoInfo
import com.ian.pianotrainer.domain.model.SongTimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionPracticeIntegrationTest {

    private class TestPracticeClock(var currentTimeMs: Long = 0L) : com.ian.pianotrainer.core.music.PracticeClock {
        override fun elapsedRealtime(): Long = currentTimeMs
        override fun currentTimeMillis(): Long = currentTimeMs
    }

    @Test
    fun testSectionSlicerPreservesChordsAndGeneratesPedagogicalSections() {
        // Create synthetic MIDI notes spanning 8 measures at 60 BPM (1 measure = 4000ms in 4/4)
        val notes = listOf(
            // Measure 1
            ExerciseNote(midiNote = 60, noteName = "C4", startMs = 0L, durationMs = 500L, hand = HandMode.RIGHT),
            ExerciseNote(midiNote = 64, noteName = "E4", startMs = 1000L, durationMs = 500L, hand = HandMode.RIGHT),
            ExerciseNote(midiNote = 67, noteName = "G4", startMs = 2000L, durationMs = 500L, hand = HandMode.RIGHT),
            // Measure 2 (Chord)
            ExerciseNote(midiNote = 60, noteName = "C4", startMs = 4000L, durationMs = 1000L, hand = HandMode.RIGHT),
            ExerciseNote(midiNote = 64, noteName = "E4", startMs = 4000L, durationMs = 1000L, hand = HandMode.RIGHT),
            ExerciseNote(midiNote = 67, noteName = "G4", startMs = 4000L, durationMs = 1000L, hand = HandMode.RIGHT),
            // Measure 3
            ExerciseNote(midiNote = 48, noteName = "C3", startMs = 8000L, durationMs = 2000L, hand = HandMode.LEFT),
            // Measure 4
            ExerciseNote(midiNote = 72, noteName = "C5", startMs = 12000L, durationMs = 1000L, hand = HandMode.RIGHT),
            // Measure 5-8
            ExerciseNote(midiNote = 60, noteName = "C4", startMs = 16000L, durationMs = 1000L, hand = HandMode.RIGHT),
            ExerciseNote(midiNote = 62, noteName = "D4", startMs = 20000L, durationMs = 1000L, hand = HandMode.RIGHT),
            ExerciseNote(midiNote = 64, noteName = "E4", startMs = 24000L, durationMs = 1000L, hand = HandMode.RIGHT)
        )

        val tempos = listOf(SongTempoInfo(0L, 0L, 60, 1_000_000))
        val timeSignatures = listOf(SongTimeSignature(0L, 0L, 4, 4))

        val sections = SectionSlicer.sliceSong(
            songId = "song_fixture_1",
            notes = notes,
            tempos = tempos,
            timeSignatures = timeSignatures,
            defaultBpm = 60
        )

        assertTrue(sections.isNotEmpty())
        assertEquals("song_fixture_1_sec_1", sections[0].id)
        assertTrue(sections[0].endMs > sections[0].startMs)
        sections.forEach { section ->
            assertTrue(section.noteCount >= 0)
        }
    }

    @Test
    fun testPracticeEngineDynamicBpmToleranceCalculations() {
        val clock = TestPracticeClock(0L)
        val engine = RealPracticeEngine(clock)

        val notes = listOf(
            ExerciseNote(midiNote = 60, noteName = "C4", startMs = 1000L, durationMs = 500L, hand = HandMode.RIGHT)
        )

        // Test at 60 BPM
        val config60 = PracticeConfiguration(
            title = "Test",
            sourceId = "1",
            sourceType = "SONG",
            notes = notes,
            practiceMode = PracticeMode.RHYTHM,
            handMode = HandMode.RIGHT,
            bpm = 60
        )
        engine.startPractice(config60)
        assertEquals(250L, engine.getDynamicToleranceMs()) // 0.3 * 1000ms = 300ms -> clamped to 250ms

        // Test at 120 BPM
        val config120 = PracticeConfiguration(
            title = "Test",
            sourceId = "1",
            sourceType = "SONG",
            notes = notes,
            practiceMode = PracticeMode.RHYTHM,
            handMode = HandMode.RIGHT,
            bpm = 120
        )
        engine.startPractice(config120)
        assertEquals(150L, engine.getDynamicToleranceMs()) // 0.3 * 500ms = 150ms

        // Test at 240 BPM
        val config240 = PracticeConfiguration(
            title = "Test",
            sourceId = "1",
            sourceType = "SONG",
            notes = notes,
            practiceMode = PracticeMode.RHYTHM,
            handMode = HandMode.RIGHT,
            bpm = 240
        )
        engine.startPractice(config240)
        assertEquals(80L, engine.getDynamicToleranceMs()) // 0.3 * 250ms = 75ms -> clamped to 80ms
    }

    @Test
    fun testPracticeEngineWaitModeChordSatisfaction() {
        val clock = TestPracticeClock(0L)
        val engine = RealPracticeEngine(clock)

        // C Major Chord: C4 (60), E4 (64), G4 (67)
        val chordNotes = listOf(
            ExerciseNote(midiNote = 60, noteName = "C4", startMs = 0L, durationMs = 1000L, hand = HandMode.RIGHT),
            ExerciseNote(midiNote = 64, noteName = "E4", startMs = 0L, durationMs = 1000L, hand = HandMode.RIGHT),
            ExerciseNote(midiNote = 67, noteName = "G4", startMs = 0L, durationMs = 1000L, hand = HandMode.RIGHT),
            // Next note
            ExerciseNote(midiNote = 72, noteName = "C5", startMs = 2000L, durationMs = 1000L, hand = HandMode.RIGHT)
        )

        val config = PracticeConfiguration(
            title = "Test",
            sourceId = "1",
            sourceType = "SONG",
            notes = chordNotes,
            practiceMode = PracticeMode.WAIT_FOR_NOTE,
            handMode = HandMode.RIGHT,
            bpm = 60
        )

        engine.startPractice(config)

        // 1. Play first note in chord (64)
        engine.processPlayedNote(64, 80)
        var state = engine.state.value
        assertEquals(1, state.correctNotesCount)
        assertEquals(0, state.currentNoteIndex) // Still on chord

        // 2. Play wrong note (65) - should record wrong but not clear already hit note 64
        engine.processPlayedNote(65, 80)
        state = engine.state.value
        assertEquals(1, state.wrongNotesCount)
        assertEquals(0, state.currentNoteIndex)

        // 3. Play second note in chord (60)
        engine.processPlayedNote(60, 80)
        state = engine.state.value
        assertEquals(2, state.correctNotesCount)
        assertEquals(0, state.currentNoteIndex)

        // 4. Play third note in chord (67) -> chord complete, advances to C5
        engine.processPlayedNote(67, 80)
        state = engine.state.value
        assertEquals(3, state.correctNotesCount)
        assertEquals(3, state.currentNoteIndex) // Advanced to note index 3 (C5)
        assertEquals(72, state.currentExpectedNote?.midiNote)
    }

    @Test
    fun testSectionPracticeStepLadderProgression() {
        val steps = SectionPracticeStep.values()
        assertEquals(8, steps.size)
        assertEquals("Nghe mẫu", steps[0].title)
        assertTrue(steps[0].isDemo)

        assertEquals("Tay phải — Chờ nốt", steps[1].title)
        assertEquals(HandMode.RIGHT, steps[1].handMode)
        assertEquals(PracticeMode.WAIT_FOR_NOTE, steps[1].practiceMode)

        assertEquals("Tay trái — Chờ nốt", steps[2].title)
        assertEquals(HandMode.LEFT, steps[2].handMode)
        assertEquals(PracticeMode.WAIT_FOR_NOTE, steps[2].practiceMode)

        assertEquals("Hai tay — Chờ nốt", steps[3].title)
        assertEquals(HandMode.BOTH, steps[3].handMode)
        assertEquals(PracticeMode.WAIT_FOR_NOTE, steps[3].practiceMode)

        assertEquals("Hai tay — 50% tốc độ", steps[4].title)
        assertEquals(0.5f, steps[4].speedMultiplier)

        assertEquals("Hai tay — 100% tốc độ", steps[7].title)
        assertEquals(1.0f, steps[7].speedMultiplier)
    }
}
