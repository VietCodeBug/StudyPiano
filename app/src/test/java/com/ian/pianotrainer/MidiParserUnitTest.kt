package com.ian.pianotrainer

import com.ian.pianotrainer.core.music.midi.MidiFileParser
import com.ian.pianotrainer.core.music.midi.MidiParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class MidiParserUnitTest {

    @Test
    fun parse_format0_singleTrack() {
        val stream = ByteArrayOutputStream()
        val dos = DataOutputStream(stream)

        // MThd: Format 0, 1 track, 480 PPQ
        dos.writeBytes("MThd")
        dos.writeInt(6)
        dos.writeShort(0)
        dos.writeShort(1)
        dos.writeShort(480)

        // MTrk Track
        val trackStream = ByteArrayOutputStream()
        val trackDos = DataOutputStream(trackStream)

        // Delta 0, Note On: Ch 0, Note 60 (C4), Vel 90
        trackDos.writeByte(0x00)
        trackDos.writeByte(0x90)
        trackDos.writeByte(60)
        trackDos.writeByte(90)

        // Delta 480, Note Off: Ch 0, Note 60, Vel 0
        trackDos.writeByte(0x83)
        trackDos.writeByte(0x60)
        trackDos.writeByte(0x80)
        trackDos.writeByte(60)
        trackDos.writeByte(0)

        // End of Track: Delta 0, FF 2F 00
        trackDos.writeByte(0x00)
        trackDos.writeByte(0xFF)
        trackDos.writeByte(0x2F)
        trackDos.writeByte(0x00)

        val trackBytes = trackStream.toByteArray()
        dos.writeBytes("MTrk")
        dos.writeInt(trackBytes.size)
        dos.write(trackBytes)

        val parsed = MidiFileParser.parse(stream.toByteArray())
        assertEquals(0, parsed.format)
        assertEquals(480, parsed.ticksPerQuarterNote)
        assertEquals(1, parsed.tracks.size)
        assertEquals(1, parsed.tracks[0].notes.size)
        assertEquals(60, parsed.tracks[0].notes[0].midiNote)
        assertEquals(90, parsed.tracks[0].notes[0].velocity)
    }

    @Test
    fun parse_runningStatus_and_velocity0NoteOff() {
        val stream = ByteArrayOutputStream()
        val dos = DataOutputStream(stream)

        dos.writeBytes("MThd")
        dos.writeInt(6)
        dos.writeShort(1)
        dos.writeShort(1)
        dos.writeShort(480)

        val trackStream = ByteArrayOutputStream()
        val trackDos = DataOutputStream(trackStream)

        // Event 1: Note On C4 vel 80 (delta 0)
        trackDos.writeByte(0x00)
        trackDos.writeByte(0x90)
        trackDos.writeByte(60)
        trackDos.writeByte(80)

        // Event 2: Running status Note On D4 vel 85 (delta 240)
        trackDos.writeByte(0x81)
        trackDos.writeByte(0x70) // 240 ticks
        trackDos.writeByte(62)
        trackDos.writeByte(85)

        // Event 3: Running status Note On C4 vel 0 -> treated as Note Off (delta 240)
        trackDos.writeByte(0x81)
        trackDos.writeByte(0x70) // 240 ticks
        trackDos.writeByte(60)
        trackDos.writeByte(0)

        // Event 4: Running status Note On D4 vel 0 -> treated as Note Off (delta 240)
        trackDos.writeByte(0x81)
        trackDos.writeByte(0x70) // 240 ticks
        trackDos.writeByte(62)
        trackDos.writeByte(0)

        // End of Track
        trackDos.writeByte(0x00)
        trackDos.writeByte(0xFF)
        trackDos.writeByte(0x2F)
        trackDos.writeByte(0x00)

        val trackBytes = trackStream.toByteArray()
        dos.writeBytes("MTrk")
        dos.writeInt(trackBytes.size)
        dos.write(trackBytes)

        val parsed = MidiFileParser.parse(stream.toByteArray())
        assertEquals(1, parsed.tracks.size)
        val notes = parsed.tracks[0].notes
        assertEquals(2, notes.size)

        // Note 0: C4 from 0 to 480 ticks
        assertEquals(60, notes[0].midiNote)
        assertEquals(0L, notes[0].startTick)
        assertEquals(480L, notes[0].endTick)

        // Note 1: D4 from 240 to 720 ticks
        assertEquals(62, notes[1].midiNote)
        assertEquals(240L, notes[1].startTick)
        assertEquals(720L, notes[1].endTick)
    }

    @Test
    fun parse_chord_threeNotesSimultaneous() {
        val stream = ByteArrayOutputStream()
        val dos = DataOutputStream(stream)

        dos.writeBytes("MThd")
        dos.writeInt(6)
        dos.writeShort(1)
        dos.writeShort(1)
        dos.writeShort(480)

        val trackStream = ByteArrayOutputStream()
        val trackDos = DataOutputStream(trackStream)

        // C Major Chord: C4 (60), E4 (64), G4 (67) at tick 0
        trackDos.writeByte(0x00); trackDos.writeByte(0x90); trackDos.writeByte(60); trackDos.writeByte(80)
        trackDos.writeByte(0x00); trackDos.writeByte(0x90); trackDos.writeByte(64); trackDos.writeByte(80)
        trackDos.writeByte(0x00); trackDos.writeByte(0x90); trackDos.writeByte(67); trackDos.writeByte(80)

        // Release all three at delta 480 (tick 480)
        trackDos.writeByte(0x83); trackDos.writeByte(0x60); trackDos.writeByte(0x80); trackDos.writeByte(60); trackDos.writeByte(0)
        trackDos.writeByte(0x00); trackDos.writeByte(0x80); trackDos.writeByte(64); trackDos.writeByte(0)
        trackDos.writeByte(0x00); trackDos.writeByte(0x80); trackDos.writeByte(67); trackDos.writeByte(0)

        // End of track
        trackDos.writeByte(0x00); trackDos.writeByte(0xFF); trackDos.writeByte(0x2F); trackDos.writeByte(0x00)

        val trackBytes = trackStream.toByteArray()
        dos.writeBytes("MTrk")
        dos.writeInt(trackBytes.size)
        dos.write(trackBytes)

        val parsed = MidiFileParser.parse(stream.toByteArray())
        assertEquals(3, parsed.tracks[0].notes.size)
        val pitches = parsed.tracks[0].notes.map { it.midiNote }.toSet()
        assertEquals(setOf(60, 64, 67), pitches)
        parsed.tracks[0].notes.forEach {
            assertEquals(0L, it.startTick)
            assertEquals(480L, it.endTick)
            assertEquals("chord_1", it.chordId)
        }
    }

    @Test
    fun parse_multipleTempoChanges() {
        val stream = ByteArrayOutputStream()
        val dos = DataOutputStream(stream)

        dos.writeBytes("MThd")
        dos.writeInt(6)
        dos.writeShort(1)
        dos.writeShort(1)
        dos.writeShort(480)

        val trackStream = ByteArrayOutputStream()
        val trackDos = DataOutputStream(trackStream)

        // Tempo 1: at tick 0, set tempo to 500,000 µs/qn (120 BPM)
        trackDos.writeByte(0x00)
        trackDos.writeByte(0xFF); trackDos.writeByte(0x51); trackDos.writeByte(0x03)
        trackDos.writeByte(0x07); trackDos.writeByte(0xA1); trackDos.writeByte(0x20) // 500,000 (0x07A120)

        // Note 1: at tick 0 to tick 480 (duration = 500ms)
        trackDos.writeByte(0x00); trackDos.writeByte(0x90); trackDos.writeByte(60); trackDos.writeByte(80)
        trackDos.writeByte(0x83); trackDos.writeByte(0x60); trackDos.writeByte(0x80); trackDos.writeByte(60); trackDos.writeByte(0)

        // Tempo 2: at tick 480, set tempo to 1,000,000 µs/qn (60 BPM) -> 0x0F4240
        trackDos.writeByte(0x00)
        trackDos.writeByte(0xFF); trackDos.writeByte(0x51); trackDos.writeByte(0x03)
        trackDos.writeByte(0x0F); trackDos.writeByte(0x42); trackDos.writeByte(0x40)

        // Note 2: at tick 480 to tick 960 (delta 480 ticks at 60 BPM = 1000ms duration)
        trackDos.writeByte(0x00); trackDos.writeByte(0x90); trackDos.writeByte(64); trackDos.writeByte(80)
        trackDos.writeByte(0x83); trackDos.writeByte(0x60); trackDos.writeByte(0x80); trackDos.writeByte(64); trackDos.writeByte(0)

        // End of track
        trackDos.writeByte(0x00); trackDos.writeByte(0xFF); trackDos.writeByte(0x2F); trackDos.writeByte(0x00)

        val trackBytes = trackStream.toByteArray()
        dos.writeBytes("MTrk")
        dos.writeInt(trackBytes.size)
        dos.write(trackBytes)

        val parsed = MidiFileParser.parse(stream.toByteArray())
        assertEquals(2, parsed.tempos.size)
        assertEquals(120, parsed.tempos[0].bpm)
        assertEquals(60, parsed.tempos[1].bpm)

        val notes = parsed.tracks[0].notes
        assertEquals(2, notes.size)
        assertEquals(0L, notes[0].startMs)
        assertEquals(500L, notes[0].startMs + notes[0].durationMs)
        assertEquals(500L, notes[1].startMs)
        assertEquals(1500L, notes[1].startMs + notes[1].durationMs)
    }

    @Test
    fun parse_sustainPedal_extendsNoteDuration() {
        val stream = ByteArrayOutputStream()
        val dos = DataOutputStream(stream)

        dos.writeBytes("MThd")
        dos.writeInt(6)
        dos.writeShort(1)
        dos.writeShort(1)
        dos.writeShort(480)

        val trackStream = ByteArrayOutputStream()
        val trackDos = DataOutputStream(trackStream)

        // Tick 0: Pedal Down (CC64 = 127)
        trackDos.writeByte(0x00)
        trackDos.writeByte(0xB0); trackDos.writeByte(64); trackDos.writeByte(127)

        // Tick 0: Note On C4
        trackDos.writeByte(0x00)
        trackDos.writeByte(0x90); trackDos.writeByte(60); trackDos.writeByte(80)

        // Tick 240: Key Released (Note Off C4), but pedal is still held!
        trackDos.writeByte(0x81); trackDos.writeByte(0x70) // 240 ticks
        trackDos.writeByte(0x80); trackDos.writeByte(60); trackDos.writeByte(0)

        // Tick 480: Pedal Released (CC64 = 0) -> note should end at tick 480
        trackDos.writeByte(0x81); trackDos.writeByte(0x70) // 240 ticks (total 480)
        trackDos.writeByte(0xB0); trackDos.writeByte(64); trackDos.writeByte(0)

        // End of track
        trackDos.writeByte(0x00); trackDos.writeByte(0xFF); trackDos.writeByte(0x2F); trackDos.writeByte(0x00)

        val trackBytes = trackStream.toByteArray()
        dos.writeBytes("MTrk")
        dos.writeInt(trackBytes.size)
        dos.write(trackBytes)

        val parsed = MidiFileParser.parse(stream.toByteArray())
        val notes = parsed.tracks[0].notes
        assertEquals(1, notes.size)
        assertEquals(0L, notes[0].startTick)
        assertEquals(480L, notes[0].endTick)
    }

    @Test
    fun parse_unclosedNote_autoClosedAtEndOfTrack() {
        val stream = ByteArrayOutputStream()
        val dos = DataOutputStream(stream)

        dos.writeBytes("MThd")
        dos.writeInt(6)
        dos.writeShort(1)
        dos.writeShort(1)
        dos.writeShort(480)

        val trackStream = ByteArrayOutputStream()
        val trackDos = DataOutputStream(trackStream)

        // Tick 0: Note On C4 (Never explicitly closed)
        trackDos.writeByte(0x00)
        trackDos.writeByte(0x90); trackDos.writeByte(60); trackDos.writeByte(80)

        // Tick 480: End of track
        trackDos.writeByte(0x83); trackDos.writeByte(0x60)
        trackDos.writeByte(0xFF); trackDos.writeByte(0x2F); trackDos.writeByte(0x00)

        val trackBytes = trackStream.toByteArray()
        dos.writeBytes("MTrk")
        dos.writeInt(trackBytes.size)
        dos.write(trackBytes)

        val parsed = MidiFileParser.parse(stream.toByteArray())
        assertEquals(1, parsed.tracks[0].notes.size)
        assertEquals(60, parsed.tracks[0].notes[0].midiNote)
        assertEquals(0L, parsed.tracks[0].notes[0].startTick)
        assertEquals(480L, parsed.tracks[0].notes[0].endTick)
    }

    @Test
    fun parse_twoTracks_handHeuristics() {
        val stream = ByteArrayOutputStream()
        val dos = DataOutputStream(stream)

        dos.writeBytes("MThd")
        dos.writeInt(6)
        dos.writeShort(1)
        dos.writeShort(2) // 2 tracks
        dos.writeShort(480)

        // Track 0: Treble notes (High pitch: C5 = 72, E5 = 76) -> RIGHT hand
        val t0Stream = ByteArrayOutputStream()
        val t0Dos = DataOutputStream(t0Stream)
        t0Dos.writeByte(0x00); t0Dos.writeByte(0x90); t0Dos.writeByte(72); t0Dos.writeByte(80)
        t0Dos.writeByte(0x83); t0Dos.writeByte(0x60); t0Dos.writeByte(0x80); t0Dos.writeByte(72); t0Dos.writeByte(0)
        t0Dos.writeByte(0x00); t0Dos.writeByte(0xFF); t0Dos.writeByte(0x2F); t0Dos.writeByte(0x00)
        val t0Bytes = t0Stream.toByteArray()
        dos.writeBytes("MTrk"); dos.writeInt(t0Bytes.size); dos.write(t0Bytes)

        // Track 1: Bass notes (Low pitch: C3 = 48, G2 = 43) -> LEFT hand
        val t1Stream = ByteArrayOutputStream()
        val t1Dos = DataOutputStream(t1Stream)
        t1Dos.writeByte(0x00); t1Dos.writeByte(0x90); t1Dos.writeByte(48); t1Dos.writeByte(80)
        t1Dos.writeByte(0x83); t1Dos.writeByte(0x60); t1Dos.writeByte(0x80); t1Dos.writeByte(48); t1Dos.writeByte(0)
        t1Dos.writeByte(0x00); t1Dos.writeByte(0xFF); t1Dos.writeByte(0x2F); t1Dos.writeByte(0x00)
        val t1Bytes = t1Stream.toByteArray()
        dos.writeBytes("MTrk"); dos.writeInt(t1Bytes.size); dos.write(t1Bytes)

        val parsed = MidiFileParser.parse(stream.toByteArray())
        assertEquals(2, parsed.tracks.size)
        assertEquals("RIGHT", parsed.tracks[0].defaultHand)
        assertEquals("RIGHT", parsed.tracks[0].notes[0].assignedHand)
        assertEquals("LEFT", parsed.tracks[1].defaultHand)
        assertEquals("LEFT", parsed.tracks[1].notes[0].assignedHand)
    }

    @Test
    fun parse_malformedMidi_throwsMidiParseException() {
        // 1. Completely empty or too small
        try {
            MidiFileParser.parse(ByteArray(4))
            fail("Expected MidiParseException for short data")
        } catch (e: MidiParseException) {
            assertNotNull(e.message)
        }

        // 2. Invalid header tag
        val invalidHeader = "RIFF\u0000\u0000\u0000\u0006\u0000\u0001\u0000\u0001\u0001\u00e0".toByteArray(Charsets.ISO_8859_1)
        try {
            MidiFileParser.parse(invalidHeader)
            fail("Expected MidiParseException for non-MThd header")
        } catch (e: MidiParseException) {
            assertTrue(e.message?.contains("MThd") == true)
        }
    }
}
