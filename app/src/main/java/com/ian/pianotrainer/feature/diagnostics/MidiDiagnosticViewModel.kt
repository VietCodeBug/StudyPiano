package com.ian.pianotrainer.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.MidiNoteEvent
import com.ian.pianotrainer.domain.model.UserSettings
import com.ian.pianotrainer.domain.repository.SettingsRepository
import com.ian.pianotrainer.domain.service.MidiInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MidiDiagnosticUiState(
    val lastEvent: MidiNoteEvent? = null,
    val eventLogs: List<MidiNoteEvent> = emptyList(),
    val activePressedNotes: Set<Int> = emptySet(),
    val userSettings: UserSettings = UserSettings()
)

class MidiDiagnosticViewModel(
    private val midiInput: MidiInput,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _eventLogs = MutableStateFlow<List<MidiNoteEvent>>(emptyList())
    private val _lastEvent = MutableStateFlow<MidiNoteEvent?>(null)
    private val _activeNotes = MutableStateFlow<Set<Int>>(emptySet())

    val uiState: StateFlow<MidiDiagnosticUiState> = combine(
        _lastEvent,
        _eventLogs,
        _activeNotes,
        settingsRepository.userSettings
    ) { last, logs, active, settings ->
        MidiDiagnosticUiState(
            lastEvent = last,
            eventLogs = logs,
            activePressedNotes = active,
            userSettings = settings
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MidiDiagnosticUiState()
    )

    init {
        observeMidi()
    }

    private fun observeMidi() {
        viewModelScope.launch {
            midiInput.noteEvents.collect { event ->
                _lastEvent.value = event
                _eventLogs.value = (listOf(event) + _eventLogs.value).take(50)

                if (event.isNoteOn && event.velocity > 0) {
                    _activeNotes.value = _activeNotes.value + event.note
                } else {
                    _activeNotes.value = _activeNotes.value - event.note
                }
            }
        }
    }

    fun onVirtualKeyPressed(midiNote: Int) {
        midiInput.onVirtualKeyPressed(midiNote, 85)
    }

    fun onVirtualKeyReleased(midiNote: Int) {
        midiInput.onVirtualKeyReleased(midiNote)
    }

    fun clearLogs() {
        _eventLogs.value = emptyList()
        _lastEvent.value = null
    }

    class Factory(
        private val midiInput: MidiInput,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MidiDiagnosticViewModel(midiInput, settingsRepository) as T
        }
    }
}
