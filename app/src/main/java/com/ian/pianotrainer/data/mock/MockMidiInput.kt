package com.ian.pianotrainer.data.mock

import com.ian.pianotrainer.domain.model.MidiControlEvent
import com.ian.pianotrainer.domain.model.MidiNoteEvent
import com.ian.pianotrainer.domain.service.MidiInput
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MockMidiInput : MidiInput {

    private val _noteEvents = MutableSharedFlow<MidiNoteEvent>(
        replay = 1,
        extraBufferCapacity = 64
    )
    override val noteEvents: SharedFlow<MidiNoteEvent> = _noteEvents.asSharedFlow()

    private val _controlEvents = MutableSharedFlow<MidiControlEvent>(
        replay = 1,
        extraBufferCapacity = 64
    )
    override val controlEvents: SharedFlow<MidiControlEvent> = _controlEvents.asSharedFlow()

    override fun onVirtualKeyPressed(midiNote: Int, velocity: Int) {
        val event = MidiNoteEvent(
            channel = 0,
            note = midiNote,
            velocity = velocity.coerceIn(1, 127),
            isNoteOn = true,
            timestampMs = System.currentTimeMillis()
        )
        _noteEvents.tryEmit(event)
    }

    override fun onVirtualKeyReleased(midiNote: Int) {
        val event = MidiNoteEvent(
            channel = 0,
            note = midiNote,
            velocity = 0,
            isNoteOn = false,
            timestampMs = System.currentTimeMillis()
        )
        _noteEvents.tryEmit(event)
    }

    fun emitControlChange(controllerNumber: Int, value: Int) {
        val event = MidiControlEvent(
            channel = 0,
            controllerNumber = controllerNumber,
            value = value,
            timestampMs = System.currentTimeMillis()
        )
        _controlEvents.tryEmit(event)
    }
}
