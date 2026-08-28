package com.ian.pianotrainer

import com.ian.pianotrainer.core.music.NoteHelper
import com.ian.pianotrainer.core.music.PracticeClock
import com.ian.pianotrainer.core.music.midi.MidiFileParser
import com.ian.pianotrainer.data.practice.RealPracticeEngine
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.domain.model.PracticeConfiguration
import com.ian.pianotrainer.domain.model.PracticeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class PianoTrainerUnitTest {

    class MockPracticeClock(var currentTime: Long = 1000L) : PracticeClock {
        override fun now(): Long = currentTime
    }

    @Test
    fun noteHelper_correctMidiToNoteName() {
        assertEquals("C4", NoteHelper.formatNoteName(60, NoteNamingMode.CDE))
        assertEquals("Đô4", NoteHelper.formatNoteName(60, NoteNamingMode.DOREMI))
        assertEquals("A4", NoteHelper.formatNoteName(69, NoteNamingMode.CDE))
        assertEquals("La4", NoteHelper.formatNoteName(69, NoteNamingMode.DOREMI))
    }

    @Test
    fun noteHelper_detectsBlackKeys() {
        assertFalse(NoteHelper.isBlackKey(60)) // C4
        assertTrue(NoteHelper.isBlackKey(61))  // C#4
        assertFalse(NoteHelper.isBlackKey(62)) // D4
        assertTrue(NoteHelper.isBlackKey(63))  // D#4
        assertFalse(NoteHelper.isBlackKey(64)) // E4
        assertFalse(NoteHelper.isBlackKey(65)) // F4
        assertTrue(NoteHelper.isBlackKey(66))  // F#4
    }

    @Test
    fun noteHelper_detectsMiddleC() {
        assertTrue(NoteHelper.isMiddleC(60))
        assertFalse(NoteHelper.isMiddleC(61))
    }

    @Test
    fun midiParser_parsesValidMinimalMidiStream() {
        // Construct a binary MIDI standard file in memory
        val byteStream = ByteArrayOutputStream()
        val dos = DataOutputStream(byteStream)

        // 1. MThd Header
        dos.writeBytes("MThd")
        dos.writeInt(6) // Header length
        dos.writeShort(1) // Format 1 (multi-track)
        dos.writeShort(1) // 1 track
        dos.writeShort(480) // 480 TPQN

        // 2. MTrk Track
        val trackStream = ByteArrayOutputStream()
        val trackDos = DataOutputStream(trackStream)

        // Delta 0, Note On: Ch 0, Note 60 (C4), Vel 80
        trackDos.writeByte(0x00) // delta 0
        trackDos.writeByte(0x90) // note on ch 0
        trackDos.writeByte(60)   // note C4
        trackDos.writeByte(80)   // velocity 80

        // Delta 480 (1 beat), Note Off: Ch 0, Note 60, Vel 0
        trackDos.writeByte(0x83) // variable-length 480 (0x83 0x60)
        trackDos.writeByte(0x60)
        trackDos.writeByte(0x80) // note off ch 0
        trackDos.writeByte(60)
        trackDos.writeByte(0)

        // End of Track event: Delta 0, FF 2F 00
        trackDos.writeByte(0x00)
        trackDos.writeByte(0xFF)
        trackDos.writeByte(0x2F)
        trackDos.writeByte(0x00)

        val trackBytes = trackStream.toByteArray()

        dos.writeBytes("MTrk")
        dos.writeInt(trackBytes.size)
        dos.write(trackBytes)

        val rawMidi = byteStream.toByteArray()
        val parsed = MidiFileParser.parse(rawMidi)

        assertEquals(1, parsed.format)
        assertEquals(480, parsed.ticksPerQuarterNote)
        assertEquals(1, parsed.tracks.size)
        assertEquals(1, parsed.tracks[0].noteCount)
        assertEquals(60, parsed.tracks[0].notes[0].midiNote)
        assertEquals(80, parsed.tracks[0].notes[0].velocity)
    }

    @Test
    fun practiceEngine_waitForNote_handlesCorrectAndWrongHits() {
        val clock = MockPracticeClock(1000L)
        val engine = RealPracticeEngine(clock)

        val notes = listOf(
            ExerciseNote(60, "C4", 1.0, 1, HandMode.RIGHT),
            ExerciseNote(62, "D4", 1.0, 2, HandMode.RIGHT),
            ExerciseNote(64, "E4", 1.0, 3, HandMode.RIGHT)
        )

        val config = PracticeConfiguration(
            title = "Test C Major",
            sourceId = "test_1",
            sourceType = "DRILL",
            practiceMode = PracticeMode.WAIT_FOR_NOTE,
            handMode = HandMode.RIGHT,
            displayMode = DisplayMode.FALLING_NOTES,
            bpm = 60,
            notes = notes
        )

        engine.startPractice(config)
        assertEquals(0, engine.state.value.currentNoteIndex)
        assertEquals(60, engine.state.value.currentExpectedNote?.midiNote)

        // 1. Player hits WRONG key (65 instead of 60)
        engine.processPlayedNote(65, 80)
        assertEquals(0, engine.state.value.currentNoteIndex) // Doesn't advance
        assertEquals(1, engine.state.value.wrongNotesCount)
        assertEquals(0, engine.state.value.currentStreak)

        // 2. Player hits CORRECT key (60)
        engine.processPlayedNote(60, 80)
        assertEquals(1, engine.state.value.currentNoteIndex) // Advances to note 2 (D4)
        assertEquals(1, engine.state.value.correctNotesCount)
        assertEquals(1, engine.state.value.currentStreak)
        assertEquals(62, engine.state.value.currentExpectedNote?.midiNote)

        // 3. Player hits next CORRECT key (62)
        engine.processPlayedNote(62, 85)
        assertEquals(2, engine.state.value.currentNoteIndex) // Advances to note 3 (E4)
        assertEquals(2, engine.state.value.correctNotesCount)
        assertEquals(2, engine.state.value.currentStreak)

        // 4. Player hits final note (64)
        engine.processPlayedNote(64, 90)
        assertTrue(engine.state.value.isFinished)

        val result = engine.stop()
        assertNotNull(result.session)
        assertEquals(3, result.session?.correctNotes)
        assertEquals(1, result.session?.wrongNotes)
        assertTrue(result.accuracy > 70f)
    }

    @Test
    fun practiceEngine_loopingSupport() {
        val clock = MockPracticeClock(1000L)
        val engine = RealPracticeEngine(clock)

        val notes = listOf(
            ExerciseNote(60, "C4", 1.0, 1, HandMode.RIGHT),
            ExerciseNote(62, "D4", 1.0, 2, HandMode.RIGHT),
            ExerciseNote(64, "E4", 1.0, 3, HandMode.RIGHT),
            ExerciseNote(65, "F4", 1.0, 4, HandMode.RIGHT)
        )

        val config = PracticeConfiguration(
            title = "Test Loop",
            sourceId = "test_loop",
            sourceType = "DRILL",
            practiceMode = PracticeMode.WAIT_FOR_NOTE,
            handMode = HandMode.RIGHT,
            displayMode = DisplayMode.FALLING_NOTES,
            bpm = 60,
            notes = notes
        )

        engine.startPractice(config)
        // Set loop range between index 1 (D4) and 2 (E4)
        engine.setLoopRange(1, 2)

        // Advance to index 2
        engine.processPlayedNote(60, 80) // at index 0 -> moves to 1
        engine.processPlayedNote(62, 80) // at index 1 -> moves to 2
        engine.processPlayedNote(64, 80) // at index 2 (end of loop) -> wraps back to start of loop (1)

        assertEquals(1, engine.state.value.currentNoteIndex)
        assertFalse(engine.state.value.isFinished)
    }
}
