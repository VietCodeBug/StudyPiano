package com.ian.pianotrainer.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.PracticeSession
import com.ian.pianotrainer.domain.model.ProgressSummary
import com.ian.pianotrainer.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProgressUiState(
    val summary: ProgressSummary = ProgressSummary(),
    val recentSessions: List<PracticeSession> = emptyList(),
    val isLoading: Boolean = false
)

class ProgressViewModel(
    private val progressRepository: ProgressRepository
) : ViewModel() {

    val uiState: StateFlow<ProgressUiState> = combine(
        progressRepository.getProgressSummary(),
        progressRepository.getRecentSessions(20)
    ) { summary, sessions ->
        ProgressUiState(
            summary = summary,
            recentSessions = sessions,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProgressUiState(isLoading = true)
    )

    class Factory(
        private val progressRepository: ProgressRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProgressViewModel(progressRepository) as T
        }
    }
}
