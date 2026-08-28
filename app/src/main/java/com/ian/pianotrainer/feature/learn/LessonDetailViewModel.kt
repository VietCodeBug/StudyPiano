package com.ian.pianotrainer.feature.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.Lesson
import com.ian.pianotrainer.domain.model.UserSettings
import com.ian.pianotrainer.domain.repository.CurriculumRepository
import com.ian.pianotrainer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LessonDetailUiState(
    val lesson: Lesson? = null,
    val userSettings: UserSettings = UserSettings(),
    val isLoading: Boolean = true
)

class LessonDetailViewModel(
    private val lessonId: String,
    private val curriculumRepository: CurriculumRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _lessonFlow = MutableStateFlow<Lesson?>(null)

    val uiState: StateFlow<LessonDetailUiState> = combine(
        _lessonFlow,
        settingsRepository.userSettings
    ) { lesson, settings ->
        LessonDetailUiState(
            lesson = lesson,
            userSettings = settings,
            isLoading = lesson == null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LessonDetailUiState(isLoading = true)
    )

    init {
        loadLesson()
    }

    private fun loadLesson() {
        viewModelScope.launch {
            val lesson = curriculumRepository.getLessonById(lessonId)
            _lessonFlow.value = lesson
        }
    }

    class Factory(
        private val lessonId: String,
        private val curriculumRepository: CurriculumRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LessonDetailViewModel(lessonId, curriculumRepository, settingsRepository) as T
        }
    }
}
