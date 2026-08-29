package com.ian.pianotrainer.core.midi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioRecordOwner {
    NONE,
    PITCH_DETECTOR,
    FREE_PLAY_RECORDER
}

/**
 * Coordinates AudioRecord hardware usage across the app to prevent conflicting AudioRecord / MediaRecorder instances.
 */
object AudioInputCoordinator {
    private val lock = Any()
    private val _currentOwner = MutableStateFlow(AudioRecordOwner.NONE)
    val currentOwner: StateFlow<AudioRecordOwner> = _currentOwner.asStateFlow()

    fun requestAccess(owner: AudioRecordOwner, force: Boolean = false): Boolean = synchronized(lock) {
        if (_currentOwner.value == AudioRecordOwner.NONE || _currentOwner.value == owner || force) {
            _currentOwner.value = owner
            return true
        }
        return false
    }

    fun releaseAccess(owner: AudioRecordOwner) = synchronized(lock) {
        if (_currentOwner.value == owner) {
            _currentOwner.value = AudioRecordOwner.NONE
        }
    }

    fun isAvailable(owner: AudioRecordOwner): Boolean = synchronized(lock) {
        _currentOwner.value == AudioRecordOwner.NONE || _currentOwner.value == owner
    }

    fun forceReleaseAll() = synchronized(lock) {
        _currentOwner.value = AudioRecordOwner.NONE
    }
}
