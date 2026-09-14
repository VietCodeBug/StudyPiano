package com.ian.pianotrainer.domain.service

import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class MetronomeSound(val displayName: String) {
    WOOD("G\u00f5 g\u1ed7"),
    MECHANICAL("M\u00e1y c\u01a1"),
    SOFT("Nh\u1eb9"),
    DIGITAL("\u0110i\u1ec7n t\u1eed"),
    CUSTOM("\u00c2m t\u00f9y ch\u1ec9nh");

    companion object {
        val builtIns = listOf(MECHANICAL, WOOD)
    }
}

interface MetronomeController {
    val isRunning: StateFlow<Boolean>
    val currentBeat: StateFlow<Int>
    val bpm: StateFlow<Int>
    val beatsPerBar: StateFlow<Int> get() = MutableStateFlow(4)
    val accentEnabled: StateFlow<Boolean> get() = MutableStateFlow(true)
    val volume: StateFlow<Float> get() = MutableStateFlow(0.8f)

    fun start(bpm: Int)
    fun startTimeline() = Unit
    fun resetTimeline(beat: Int, bpm: Int) { setBpm(bpm) }
    fun playTimelineBeat(beat: Int, bpm: Int, isMeasureStart: Boolean) = Unit
    fun stop()
    fun setBpm(bpm: Int)
    fun setBeatsPerBar(beats: Int) = Unit
    fun setAccentEnabled(enabled: Boolean) = Unit

    fun getSound(): MetronomeSound = MetronomeSound.WOOD
    fun getCustomSoundName(): String? = null
    fun setSound(sound: MetronomeSound) = Unit
    fun setVolume(volume: Float) = Unit
    fun preview() = Unit
    suspend fun importCustomSound(input: InputStream, fileName: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Custom metronome sounds are unavailable"))
}

/**
 * Interface prepared for Phase 2 MIDI file importation.
 */
interface MidiFileImporter {
    suspend fun importMidiFile(displayName: String, uriString: String): Result<String>
}
