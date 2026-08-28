package com.ian.pianotrainer.domain.service

import com.ian.pianotrainer.domain.model.MidiControlEvent
import com.ian.pianotrainer.domain.model.MidiNoteEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Common abstraction for receiving MIDI events from any source:
 * - MockMidiInput (Phase 1)
 * - AndroidBluetoothMidiInput (Phase 2 TODO)
 * - AndroidUsbMidiInput (Phase 2 TODO)
 */
interface MidiInput {
    val noteEvents: SharedFlow<MidiNoteEvent>
    val controlEvents: SharedFlow<MidiControlEvent>

    fun onVirtualKeyPressed(midiNote: Int, velocity: Int = 80)
    fun onVirtualKeyReleased(midiNote: Int)
}
