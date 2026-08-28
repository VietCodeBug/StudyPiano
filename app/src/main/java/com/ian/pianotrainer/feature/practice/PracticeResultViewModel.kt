package com.ian.pianotrainer.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.PracticeSession
import com.ian.pianotrainer.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PracticeResultUiState(
    val session: PracticeSession? = null,
    val isLoading: Boolean = true
)

class PracticeResultViewModel(
    private val sessionId: String,
    private val progressRepository: ProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PracticeResultUiState())
    val uiState: StateFlow<PracticeResultUiState> = _uiState.asStateFlow()

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            _uiState.value = PracticeResultUiState(isLoading = true)
            val session = progressRepository.getSessionById(sessionId)
            _uiState.value = PracticeResultUiState(session = session, isLoading = false)
        }
    }

    class Factory(
        private val sessionId: String,
        private val progressRepository: ProgressRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PracticeResultViewModel(sessionId, progressRepository) as T
        }
    }
}
