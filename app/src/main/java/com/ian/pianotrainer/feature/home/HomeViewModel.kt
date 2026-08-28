package com.ian.pianotrainer.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.Course
import com.ian.pianotrainer.domain.model.DeviceConnectionState
import com.ian.pianotrainer.domain.model.Lesson
import com.ian.pianotrainer.domain.model.PianoDevice
import com.ian.pianotrainer.domain.model.ProgressSummary
import com.ian.pianotrainer.domain.repository.CurriculumRepository
import com.ian.pianotrainer.domain.repository.ProgressRepository
import com.ian.pianotrainer.domain.service.PianoDeviceManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val recommendedLesson: Lesson? = null,
    val recommendedCourse: Course? = null,
    val connectionState: DeviceConnectionState = DeviceConnectionState.DISCONNECTED,
    val connectedDevice: PianoDevice? = null,
    val progressSummary: ProgressSummary = ProgressSummary(),
    val isLoading: Boolean = false
)

class HomeViewModel(
    private val curriculumRepository: CurriculumRepository,
    private val progressRepository: ProgressRepository,
    private val deviceManager: PianoDeviceManager
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        curriculumRepository.getCourses(),
        progressRepository.getProgressSummary(),
        deviceManager.connectionState,
        deviceManager.connectedDevice
    ) { courses, summary, connState, device ->
        // Find next incomplete lesson
        var nextLesson: Lesson? = null
        var nextCourse: Course? = null

        for (course in courses) {
            val incomplete = course.lessons.find { !it.isCompleted }
            if (incomplete != null) {
                nextLesson = incomplete
                nextCourse = course
                break
            }
        }

        // Fallback to first lesson if all complete or empty
        if (nextLesson == null && courses.isNotEmpty() && courses[0].lessons.isNotEmpty()) {
            nextLesson = courses[0].lessons[0]
            nextCourse = courses[0]
        }

        HomeUiState(
            recommendedLesson = nextLesson,
            recommendedCourse = nextCourse,
            connectionState = connState,
            connectedDevice = device,
            progressSummary = summary,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    class Factory(
        private val curriculumRepository: CurriculumRepository,
        private val progressRepository: ProgressRepository,
        private val deviceManager: PianoDeviceManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(curriculumRepository, progressRepository, deviceManager) as T
        }
    }
}
