package com.ian.pianotrainer.feature.freeplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.UserSettings
import com.ian.pianotrainer.domain.repository.SettingsRepository
import com.ian.pianotrainer.domain.service.MetronomeController
import com.ian.pianotrainer.domain.service.MidiInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FreePlayUiState(
    val activePressedNotes: Set<Int> = emptySet(),
    val lastPressedNote: Int? = null,
    val isMetronomeRunning: Boolean = false,
    val bpm: Int = 80,
    val currentBeat: Int = 1,
    val userSettings: UserSettings = UserSettings()
)

class FreePlayViewModel(
    private val midiInput: MidiInput,
    private val metronomeController: MetronomeController,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _activeNotes = MutableStateFlow<Set<Int>>(emptySet())
    private val _lastNote = MutableStateFlow<Int?>(null)
    private val _bpm = MutableStateFlow(80)

    val uiState: StateFlow<FreePlayUiState> = combine(
        _activeNotes,
        _lastNote,
        metronomeController.isRunning,
        combine(
            metronomeController.currentBeat,
            _bpm,
            settingsRepository.userSettings
        ) { beat, currentBpm, settings ->
            Triple(beat, currentBpm, settings)
        }
    ) { active, last, isMetroRunning, (beat, currentBpm, settings) ->
        FreePlayUiState(
            activePressedNotes = active,
            lastPressedNote = last,
            isMetronomeRunning = isMetroRunning,
            bpm = currentBpm,
            currentBeat = beat,
            userSettings = settings
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FreePlayUiState()
    )

    init {
        observeMidi()
    }

    private fun observeMidi() {
        viewModelScope.launch {
            midiInput.noteEvents.collect { event ->
                if (event.isNoteOn && event.velocity > 0) {
                    _activeNotes.value = _activeNotes.value + event.note
                    _lastNote.value = event.note
                } else {
                    _activeNotes.value = _activeNotes.value - event.note
                }
            }
        }
    }

    fun onVirtualKeyPressed(midiNote: Int) {
        midiInput.onVirtualKeyPressed(midiNote, 90)
    }

    fun onVirtualKeyReleased(midiNote: Int) {
        midiInput.onVirtualKeyReleased(midiNote)
    }

    fun toggleMetronome() {
        if (metronomeController.isRunning.value) {
            metronomeController.stop()
        } else {
            metronomeController.start(_bpm.value)
        }
    }

    fun setBpm(newBpm: Int) {
        _bpm.value = newBpm.coerceIn(40, 200)
        if (metronomeController.isRunning.value) {
            metronomeController.setBpm(newBpm)
        }
    }

    override fun onCleared() {
        super.onCleared()
        metronomeController.stop()
    }

    class Factory(
        private val midiInput: MidiInput,
        private val metronomeController: MetronomeController,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FreePlayViewModel(midiInput, metronomeController, settingsRepository) as T
        }
    }
}
