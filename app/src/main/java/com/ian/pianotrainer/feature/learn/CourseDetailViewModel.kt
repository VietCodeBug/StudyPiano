package com.ian.pianotrainer.feature.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.Course
import com.ian.pianotrainer.domain.repository.CurriculumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CourseDetailUiState(
    val course: Course? = null,
    val isLoading: Boolean = true
)

class CourseDetailViewModel(
    private val courseId: String,
    private val curriculumRepository: CurriculumRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CourseDetailUiState())
    val uiState: StateFlow<CourseDetailUiState> = _uiState.asStateFlow()

    init {
        loadCourse()
    }

    private fun loadCourse() {
        viewModelScope.launch {
            _uiState.value = CourseDetailUiState(isLoading = true)
            curriculumRepository.getCourses().collect { courses ->
                val course = courses.find { it.id == courseId }
                _uiState.value = CourseDetailUiState(course = course, isLoading = false)
            }
        }
    }

    class Factory(
        private val courseId: String,
        private val curriculumRepository: CurriculumRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CourseDetailViewModel(courseId, curriculumRepository) as T
        }
    }
}
