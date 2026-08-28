package com.ian.pianotrainer.feature.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.Course
import com.ian.pianotrainer.domain.repository.CurriculumRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LearnUiState(
    val courses: List<Course> = emptyList(),
    val totalLessonsCount: Int = 0,
    val completedLessonsCount: Int = 0,
    val overallProgressPercent: Int = 0,
    val isLoading: Boolean = false
)

class LearnViewModel(
    private val curriculumRepository: CurriculumRepository
) : ViewModel() {

    val uiState: StateFlow<LearnUiState> = curriculumRepository.getCourses()
        .map { courses ->
            val allLessons = courses.flatMap { it.lessons }
            val completed = allLessons.count { it.isCompleted }
            val percent = if (allLessons.isNotEmpty()) (completed * 100) / allLessons.size else 0

            LearnUiState(
                courses = courses,
                totalLessonsCount = allLessons.size,
                completedLessonsCount = completed,
                overallProgressPercent = percent,
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LearnUiState(isLoading = true)
        )

    class Factory(
        private val curriculumRepository: CurriculumRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LearnViewModel(curriculumRepository) as T
        }
    }
}
