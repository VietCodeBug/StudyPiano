package com.ian.pianotrainer

import com.ian.pianotrainer.core.music.midi.MidiFileParser
import com.ian.pianotrainer.core.music.midi.MidiRecordingWriter
import com.ian.pianotrainer.domain.model.RecordedMidiEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class MidiRecordingWriterUnitTest {

    private val writer = MidiRecordingWriter(ppq = 480)

    @Test
    fun write_and_parse_roundtrip_validatesNotesAndTimings() {
        val events = listOf(
            RecordedMidiEvent(timestampMs = 0L, isNoteOn = true, note = 60, velocity = 80, channel = 0),
            RecordedMidiEvent(timestampMs = 500L, isNoteOn = false, note = 60, velocity = 0, channel = 0),
            RecordedMidiEvent(timestampMs = 500L, isNoteOn = true, note = 64, velocity = 90, channel = 0),
            RecordedMidiEvent(timestampMs = 1000L, isNoteOn = false, note = 64, velocity = 0, channel = 0),
            RecordedMidiEvent(timestampMs = 1000L, isNoteOn = true, note = 67, velocity = 100, channel = 0),
            RecordedMidiEvent(timestampMs = 1500L, isNoteOn = false, note = 67, velocity = 0, channel = 0)
        )

        val output = ByteArrayOutputStream()
        writer.write(
            events = events,
            bpm = 120,
            trackName = "Test Performance",
            outputStream = output
        )

        val bytes = output.toByteArray()
        assertTrue("MIDI file bytes must be greater than header size", bytes.size > 20)

        // Parse generated standard MIDI stream
        val parseResult = MidiFileParser.parse(ByteArrayInputStream(bytes))

        assertEquals(120, parseResult.defaultBpm)
        val allNotes = parseResult.tracks.flatMap { it.notes }
        assertEquals(3, allNotes.size)

        val c4 = allNotes[0]
        assertEquals(60, c4.midiNote)
        assertEquals(0L, c4.startMs)
        assertEquals(500L, c4.durationMs)
        assertEquals(80, c4.velocity)

        val e4 = allNotes[1]
        assertEquals(64, e4.midiNote)
        assertEquals(500L, e4.startMs)
        assertEquals(500L, e4.durationMs)
        assertEquals(90, e4.velocity)

        val g4 = allNotes[2]
        assertEquals(67, g4.midiNote)
        assertEquals(1000L, g4.startMs)
        assertEquals(500L, g4.durationMs)
        assertEquals(100, g4.velocity)
    }

    @Test
    fun write_closesDanglingOpenNotesAtEnd() {
        val events = listOf(
            RecordedMidiEvent(timestampMs = 100L, isNoteOn = true, note = 72, velocity = 85, channel = 0)
            // No Note Off recorded
        )

        val output = ByteArrayOutputStream()
        writer.write(
            events = events,
            bpm = 60,
            trackName = "Unclosed Note Test",
            outputStream = output
        )

        val bytes = output.toByteArray()
        val parseResult = MidiFileParser.parse(ByteArrayInputStream(bytes))

        val allNotes = parseResult.tracks.flatMap { it.notes }
        assertEquals(1, allNotes.size)
        val note = allNotes[0]
        assertEquals(72, note.midiNote)
        assertEquals(100L, note.startMs)
        assertTrue("Dangling note must have positive duration after writer auto-close", note.durationMs > 0L)
    }
}
