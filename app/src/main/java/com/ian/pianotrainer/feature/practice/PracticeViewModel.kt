package com.ian.pianotrainer.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.PracticeMode
import com.ian.pianotrainer.domain.model.UserSettings
import com.ian.pianotrainer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PracticeQuickDrill(
    val id: String,
    val title: String,
    val description: String,
    val handMode: HandMode,
    val defaultBpm: Int,
    val noteCount: Int
)

data class PracticeUiState(
    val selectedMode: PracticeMode = PracticeMode.WAIT_FOR_NOTE,
    val selectedHand: HandMode = HandMode.RIGHT,
    val selectedDisplayMode: DisplayMode = DisplayMode.FALLING_NOTES,
    val bpm: Int = 60,
    val quickDrills: List<PracticeQuickDrill> = emptyList(),
    val userSettings: UserSettings = UserSettings()
)

class PracticeViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _selectedMode = MutableStateFlow(PracticeMode.WAIT_FOR_NOTE)
    private val _selectedHand = MutableStateFlow(HandMode.RIGHT)
    private val _selectedDisplayMode = MutableStateFlow(DisplayMode.FALLING_NOTES)
    private val _bpm = MutableStateFlow(60)

    private val drills = listOf(
        PracticeQuickDrill(
            id = "drill_c_major_5finger",
            title = "Luyện 5 ngón C-D-E-F-G",
            description = "Tập độc lập 5 ngón tay phải liền bậc",
            handMode = HandMode.RIGHT,
            defaultBpm = 60,
            noteCount = 5
        ),
        PracticeQuickDrill(
            id = "drill_thirds_rh",
            title = "Nhảy quãng 3 tay phải (C-E-D-F-E-G)",
            description = "Mở rộng độ linh hoạt ngón 1-3 và 2-4",
            handMode = HandMode.RIGHT,
            defaultBpm = 65,
            noteCount = 6
        ),
        PracticeQuickDrill(
            id = "drill_lh_bass",
            title = "Đi bè trầm tay trái (C3 - G3)",
            description = "Tăng cường lực bấm cho ngón 4 và 5 tay trái",
            handMode = HandMode.LEFT,
            defaultBpm = 55,
            noteCount = 5
        ),
        PracticeQuickDrill(
            id = "drill_ode_to_joy",
            title = "Khải hoàn ca (Ode to Joy - Beethoven)",
            description = "Giai điệu kinh điển cho người mới bắt đầu",
            handMode = HandMode.RIGHT,
            defaultBpm = 65,
            noteCount = 14
        )
    )

    val uiState: StateFlow<PracticeUiState> = combine(
        _selectedMode,
        _selectedHand,
        _selectedDisplayMode,
        _bpm,
        settingsRepository.userSettings
    ) { mode, hand, display, currentBpm, settings ->
        PracticeUiState(
            selectedMode = mode,
            selectedHand = hand,
            selectedDisplayMode = display,
            bpm = currentBpm,
            quickDrills = drills,
            userSettings = settings
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PracticeUiState(quickDrills = drills)
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
    }

    fun setBpm(newBpm: Int) {
        _bpm.value = newBpm.coerceIn(40, 140)
    }

    class Factory(
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PracticeViewModel(settingsRepository) as T
        }
    }
}
