package com.ian.pianotrainer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.domain.model.UserSettings
import com.ian.pianotrainer.domain.repository.ProgressRepository
import com.ian.pianotrainer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val progressRepository: ProgressRepository
) : ViewModel() {

    val userSettings: StateFlow<UserSettings> = settingsRepository.userSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    fun setNoteNamingMode(mode: NoteNamingMode) {
        viewModelScope.launch {
            settingsRepository.setNoteNamingMode(mode)
        }
    }

    fun setDefaultDisplayMode(mode: DisplayMode) {
        viewModelScope.launch {
            settingsRepository.setDefaultDisplayMode(mode)
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setVirtualPianoSoundEnabled(enabled)
        }
    }

    fun setDefaultBpm(bpm: Int) {
        viewModelScope.launch {
            settingsRepository.setDefaultBpm(bpm)
        }
    }

    fun setMetronomeVolume(volume: Float) {
        viewModelScope.launch {
            settingsRepository.setMetronomeVolume(volume)
        }
    }

    fun resetData() {
        viewModelScope.launch {
            progressRepository.clearAllProgress()
            settingsRepository.resetDemoData()
        }
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val progressRepository: ProgressRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsRepository, progressRepository) as T
        }
    }
}
