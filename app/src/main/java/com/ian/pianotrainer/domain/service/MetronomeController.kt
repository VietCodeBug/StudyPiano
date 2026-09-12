package com.ian.pianotrainer.domain.service

import java.io.InputStream
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

enum class MetronomeSound(val displayName: String) {
    WOOD("Gõ gỗ"),
    MECHANICAL("Máy cơ"),
    SOFT("Nhẹ"),
    DIGITAL("Điện tử"),
    CUSTOM("Âm tùy chỉnh");

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
