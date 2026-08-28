package com.ian.pianotrainer.core.music

object MidiConstants {
    const val MIN_MIDI_NOTE = 21   // A0
    const val MAX_MIDI_NOTE = 108  // C8
    const val MIDDLE_C_MIDI_NOTE = 60 // C4 (Đô 4)
    const val TOTAL_PIANO_KEYS = 88

    val NOTE_NAMES_CDE = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val NOTE_NAMES_DOREMI = arrayOf("Đô", "Đô#", "Rê", "Rê#", "Mi", "Pha", "Pha#", "Son", "Son#", "La", "La#", "Si")

    fun isBlackKey(midiNote: Int): Boolean {
        val noteInOctave = midiNote % 12
        return noteInOctave == 1 || noteInOctave == 3 || noteInOctave == 6 || noteInOctave == 8 || noteInOctave == 10
    }
}
