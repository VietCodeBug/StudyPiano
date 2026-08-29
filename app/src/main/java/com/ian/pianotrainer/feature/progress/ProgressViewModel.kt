package com.ian.pianotrainer.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.PracticeSession
import com.ian.pianotrainer.domain.model.ProgressSummary
import com.ian.pianotrainer.domain.model.UserSettings
import com.ian.pianotrainer.domain.repository.ProgressRepository
import com.ian.pianotrainer.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProgressUiState(
    val summary: ProgressSummary = ProgressSummary(),
    val recentSessions: List<PracticeSession> = emptyList(),
    val userSettings: UserSettings = UserSettings(),
    val selectedDaysFilter: Int? = 7, // 7, 30, null (all)
    val isLoading: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    private val progressRepository: ProgressRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _selectedDaysFilter = MutableStateFlow<Int?>(7)
    val selectedDaysFilter: StateFlow<Int?> = _selectedDaysFilter.asStateFlow()

    val uiState: StateFlow<ProgressUiState> = _selectedDaysFilter.flatMapLatest { filter ->
        combine(
            progressRepository.getProgressSummary(filter),
            progressRepository.getRecentSessions(30),
            settingsRepository.userSettings
        ) { summary, sessions, settings ->
            ProgressUiState(
                summary = summary,
                recentSessions = sessions,
                userSettings = settings,
                selectedDaysFilter = filter,
                isLoading = false
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProgressUiState(isLoading = true)
    )

    fun setDaysFilter(days: Int?) {
        _selectedDaysFilter.value = days
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            progressRepository.deletePracticeSession(sessionId)
        }
    }

    class Factory(
        private val progressRepository: ProgressRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProgressViewModel(progressRepository, settingsRepository) as T
        }
    }
}
