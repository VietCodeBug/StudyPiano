package com.ian.pianotrainer.domain.service

import kotlinx.coroutines.flow.StateFlow

interface MetronomeController {
    val isRunning: StateFlow<Boolean>
    val currentBeat: StateFlow<Int>
    val bpm: StateFlow<Int>

    fun start(bpm: Int)
    fun stop()
    fun setBpm(bpm: Int)
}

/**
 * Interface prepared for Phase 2 MIDI file importation.
 */
interface MidiFileImporter {
    suspend fun importMidiFile(displayName: String, uriString: String): Result<String>
}
