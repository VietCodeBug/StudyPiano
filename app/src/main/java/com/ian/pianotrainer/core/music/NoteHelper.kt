package com.ian.pianotrainer.core.music

import com.ian.pianotrainer.domain.model.NoteNamingMode

object NoteHelper {

    fun formatNoteName(midiNote: Int, mode: NoteNamingMode = NoteNamingMode.CDE): String {
        if (midiNote < 0 || midiNote > 127) return "?"
        val noteIndex = midiNote % 12
        val octave = (midiNote / 12) - 1
        return when (mode) {
            NoteNamingMode.CDE -> "${MidiConstants.NOTE_NAMES_CDE[noteIndex]}$octave"
            NoteNamingMode.DOREMI -> "${MidiConstants.NOTE_NAMES_DOREMI[noteIndex]}$octave"
        }
    }

    fun isMiddleC(midiNote: Int): Boolean = midiNote == MidiConstants.MIDDLE_C_MIDI_NOTE

    fun isBlackKey(midiNote: Int): Boolean = MidiConstants.isBlackKey(midiNote)

    fun isValidPianoMidi(midiNote: Int): Boolean =
        midiNote in MidiConstants.MIN_MIDI_NOTE..MidiConstants.MAX_MIDI_NOTE
}
