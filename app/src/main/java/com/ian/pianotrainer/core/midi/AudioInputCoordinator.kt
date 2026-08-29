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
 * Coordinates AudioRecord hardware usage across the app to prevent conflicting AudioRecord instances.
 */
object AudioInputCoordinator {
    private val _currentOwner = MutableStateFlow(AudioRecordOwner.NONE)
    val currentOwner: StateFlow<AudioRecordOwner> = _currentOwner.asStateFlow()

    fun requestAccess(owner: AudioRecordOwner): Boolean {
        if (_currentOwner.value == AudioRecordOwner.NONE || _currentOwner.value == owner) {
            _currentOwner.value = owner
            return true
        }
        return false
    }

    fun releaseAccess(owner: AudioRecordOwner) {
        if (_currentOwner.value == owner) {
            _currentOwner.value = AudioRecordOwner.NONE
        }
    }
}
