package com.ian.pianotrainer.domain.service

import java.io.InputStream
import kotlinx.coroutines.flow.StateFlow

enum class MetronomeSound(val displayName: String) {
    WOOD("Gõ gỗ"),
    MECHANICAL("Cơ học"),
    SOFT("Nhẹ"),
    DIGITAL("Điện tử"),
    CUSTOM("Âm tùy chỉnh");

    companion object {
        val builtIns = entries.filterNot { it == CUSTOM }
    }
}

interface MetronomeController {
    val isRunning: StateFlow<Boolean>
    val currentBeat: StateFlow<Int>
    val bpm: StateFlow<Int>

    fun start(bpm: Int)
    fun stop()
    fun setBpm(bpm: Int)

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
