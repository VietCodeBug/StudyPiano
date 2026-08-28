package com.ian.pianotrainer.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.FingerExercise
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.PracticeMode
import com.ian.pianotrainer.domain.model.UserSettings
import com.ian.pianotrainer.domain.repository.ExerciseRepository
import com.ian.pianotrainer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PracticeUiState(
    val selectedMode: PracticeMode = PracticeMode.WAIT_FOR_NOTE,
    val selectedHand: HandMode = HandMode.RIGHT,
    val selectedDisplayMode: DisplayMode = DisplayMode.FALLING_NOTES,
    val bpm: Int = 60,
    val exercises: List<FingerExercise> = emptyList(),
    val selectedCategory: String = "ALL",
    val userSettings: UserSettings = UserSettings(),
    val isLoading: Boolean = false
)

class PracticeViewModel(
    private val settingsRepository: SettingsRepository,
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val _selectedMode = MutableStateFlow(PracticeMode.WAIT_FOR_NOTE)
    private val _selectedHand = MutableStateFlow(HandMode.RIGHT)
    private val _selectedDisplayMode = MutableStateFlow(DisplayMode.FALLING_NOTES)
    private val _bpm = MutableStateFlow(60)
    private val _selectedCategory = MutableStateFlow("ALL")

    private val _configState = combine(
        _selectedMode,
        _selectedHand,
        _selectedDisplayMode,
        _bpm,
        _selectedCategory
    ) { mode, hand, display, bpm, category ->
        object {
            val mode = mode
            val hand = hand
            val display = display
            val bpm = bpm
            val category = category
        }
    }

    val uiState: StateFlow<PracticeUiState> = combine(
        _configState,
        exerciseRepository.getFingerExercises(),
        settingsRepository.userSettings
    ) { config, exerciseList, settings ->
        val filtered = if (config.category == "ALL") {
            exerciseList
        } else {
            exerciseList.filter { it.category == config.category }
        }

        PracticeUiState(
            selectedMode = config.mode,
            selectedHand = config.hand,
            selectedDisplayMode = config.display,
            bpm = config.bpm,
            exercises = filtered,
            selectedCategory = config.category,
            userSettings = settings,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PracticeUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            settingsRepository.userSettings.collect { settings ->
                _selectedDisplayMode.value = settings.defaultDisplayMode
                _selectedHand.value = settings.lastSelectedHandMode
                _bpm.value = settings.defaultBpm
            }
        }
    }

    fun setPracticeMode(mode: PracticeMode) {
        _selectedMode.value = mode
    }

    fun setHandMode(hand: HandMode) {
        _selectedHand.value = hand
        viewModelScope.launch {
            settingsRepository.setLastSelectedHandMode(hand)
        }
    }

    fun setDisplayMode(mode: DisplayMode) {
        _selectedDisplayMode.value = mode
        viewModelScope.launch {
            settingsRepository.setDefaultDisplayMode(mode)
        }
    }

    fun setBpm(newBpm: Int) {
        _bpm.value = newBpm.coerceIn(40, 200)
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val exerciseRepository: ExerciseRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PracticeViewModel(settingsRepository, exerciseRepository) as T
        }
    }
}
