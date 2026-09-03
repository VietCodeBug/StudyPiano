package com.ian.pianotrainer.core.audio

import kotlinx.coroutines.flow.StateFlow

data class PianoAudioState(
    val isReady: Boolean = false,
    val masterVolume: Float = 1.0f,
    val activeVoiceCount: Int = 0,
    val isSustainPedalDown: Boolean = false,
    val loadedSampleCount: Int = 0
)

sealed interface PianoAudioAvailability {
    data object Loading : PianoAudioAvailability
    data class Ready(val loadedSamples: Int) : PianoAudioAvailability
    data class Unavailable(val reason: String) : PianoAudioAvailability
    data class Error(val message: String) : PianoAudioAvailability
}
/**
 * Sample-backed real-time piano audio synthesis engine.
 * Supports velocity curves, CC64 sustain pedal, polyphony voice management,
 * and pitch shifting across the full 88-key piano keyboard (A0 - C8 / MIDI 21 - 108).
 */
interface PianoAudioEngine {
    val state: StateFlow<PianoAudioState>
    val availability: StateFlow<PianoAudioAvailability>

    suspend fun prepare()
    fun noteOn(midiNote: Int, velocity: Int, channel: Int = 0)
    fun noteOff(midiNote: Int, channel: Int = 0)
    fun sustainPedal(isDown: Boolean)
    fun allNotesOff()
    fun setMasterVolume(volume: Float)
    suspend fun release()
}
