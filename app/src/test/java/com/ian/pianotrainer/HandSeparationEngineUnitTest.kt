package com.ian.pianotrainer

import com.ian.pianotrainer.core.music.AssignedHand
import com.ian.pianotrainer.core.music.DefaultHandSeparationEngine
import com.ian.pianotrainer.core.music.midi.ParsedMidiNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandSeparationEngineUnitTest {

    private val engine = DefaultHandSeparationEngine()

    private fun note(
        midi: Int,
        startMs: Long,
        durationMs: Long = 500L,
        trackIndex: Int = 0
    ): ParsedMidiNote = ParsedMidiNote(
        trackIndex = trackIndex,
        channel = 0,
        midiNote = midi,
        velocity = 80,
        startTick = startMs, // simplified
        endTick = startMs + durationMs,
        startMs = startMs,
        durationMs = durationMs,
        assignedHand = "BOTH",
        chordId = null
    )

    @Test
    fun `empty input returns empty result`() {
        val result = engine.separate(emptyList())
        assertEquals(0, result.leftHandNoteCount)
        assertEquals(0, result.rightHandNoteCount)
        assertEquals(0, result.notes.size)
    }

    @Test
    fun `no notes are lost or duplicated after separation`() {
        // Create a mixed set of notes spanning bass to treble
        val notes = listOf(
            note(36, 0),     // C2 - bass
            note(48, 0),     // C3 - bass
            note(60, 0),     // C4 - middle
            note(72, 0),     // C5 - treble
            note(84, 0),     // C6 - high treble
            note(40, 500),   // E2
            note(52, 500),   // E3
            note(64, 500),   // E4
            note(76, 500),   // E5
            note(45, 1000),  // A2
            note(57, 1000),  // A3
            note(69, 1000),  // A4
            note(81, 1000)   // A5
        )

        val result = engine.separate(notes)
        assertEquals("Total note count must be preserved", notes.size, result.notes.size)
        assertEquals(
            "No duplicate notes",
            result.notes.size,
            result.leftHandNoteCount + result.rightHandNoteCount
        )
    }

    @Test
    fun `single track with two hands produces both LEFT and RIGHT`() {
        // Simulate a simple piano piece: bass in left, melody in right
        val notes = listOf(
            // Left hand bass pattern
            note(36, 0),     // C2
            note(43, 0),     // G2
            note(36, 500),
            note(43, 500),
            // Right hand melody
            note(72, 0),     // C5
            note(74, 250),   // D5
            note(76, 500),   // E5
            note(77, 750),   // F5
            note(79, 1000),  // G5
        )

        val result = engine.separate(notes)
        assertTrue("Should have LEFT notes", result.leftHandNoteCount > 0)
        assertTrue("Should have RIGHT notes", result.rightHandNoteCount > 0)

        // Bass notes (C2, G2) should be LEFT
        val bassNotes = result.notes.filter { it.midiNote <= 48 }
        assertTrue(
            "Bass notes (<=48) should all be LEFT",
            bassNotes.all { it.assignedHand == AssignedHand.LEFT.name }
        )

        // High melody (>=72) should be RIGHT
        val melodyNotes = result.notes.filter { it.midiNote >= 72 }
        assertTrue(
            "High melody notes (>=72) should all be RIGHT",
            melodyNotes.all { it.assignedHand == AssignedHand.RIGHT.name }
        )
    }

    @Test
    fun `wide chord gets split between hands`() {
        // A chord spanning more than one octave should be split
        val notes = listOf(
            note(40, 0),   // E2
            note(47, 0),   // B2
            note(64, 0),   // E4
            note(67, 0),   // G4
            note(71, 0),   // B4
        )

        val result = engine.separate(notes)

        // The low notes should be LEFT, high notes RIGHT
        val leftPitches = result.notes.filter { it.assignedHand == AssignedHand.LEFT.name }
            .map { it.midiNote }
        val rightPitches = result.notes.filter { it.assignedHand == AssignedHand.RIGHT.name }
            .map { it.midiNote }

        assertTrue("Should have LEFT notes in wide chord", leftPitches.isNotEmpty())
        assertTrue("Should have RIGHT notes in wide chord", rightPitches.isNotEmpty())

        if (leftPitches.isNotEmpty() && rightPitches.isNotEmpty()) {
            assertTrue(
                "LEFT max should not exceed RIGHT min (no crossing)",
                leftPitches.max() <= rightPitches.min()
            )
        }
    }

    @Test
    fun `continuity - hand stays in register across consecutive chords`() {
        // A sequence where left hand stays in bass, right hand stays in treble
        val notes = mutableListOf<ParsedMidiNote>()
        for (i in 0 until 8) {
            val time = i * 500L
            // Left hand: ascending bass line C2-G2
            notes.add(note(36 + i, time))
            // Right hand: descending melody C5-F4
            notes.add(note(72 - i, time))
        }

        val result = engine.separate(notes)

        // Verify left stays in low register, right in high register
        for (n in result.notes) {
            if (n.midiNote <= 48) {
                assertEquals(
                    "Low notes should be LEFT",
                    AssignedHand.LEFT.name,
                    n.assignedHand
                )
            }
            if (n.midiNote >= 65) {
                assertEquals(
                    "High notes should be RIGHT",
                    AssignedHand.RIGHT.name,
                    n.assignedHand
                )
            }
        }
    }

    @Test
    fun `melody crossing - handles register overlap gracefully`() {
        // Right hand dips below middle C, left hand stays put
        val notes = listOf(
            // Left hand holds a bass note
            note(36, 0),     // C2
            note(36, 1000),  // C2 again
            // Right hand melody crosses down
            note(72, 0),     // C5
            note(67, 250),   // G4
            note(62, 500),   // D4
            note(58, 750),   // Bb3 — below middle C!
            note(62, 1000),  // D4 — back up
        )

        val result = engine.separate(notes)
        assertEquals("No notes lost", notes.size, result.notes.size)
        assertTrue("Has LEFT", result.leftHandNoteCount > 0)
        assertTrue("Has RIGHT", result.rightHandNoteCount > 0)
    }

    @Test
    fun `all notes same pitch get assigned to one hand`() {
        val notes = List(5) { i -> note(60, i * 500L) }
        val result = engine.separate(notes)
        assertEquals("No notes lost", 5, result.notes.size)
        // All same pitch - should all be same hand
        val hands = result.notes.map { it.assignedHand }.toSet()
        assertEquals("All same pitch notes should have same hand", 1, hands.size)
    }
}
