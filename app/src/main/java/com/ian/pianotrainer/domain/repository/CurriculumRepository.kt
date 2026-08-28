package com.ian.pianotrainer.domain.repository

import com.ian.pianotrainer.domain.model.Course
import com.ian.pianotrainer.domain.model.Lesson
import kotlinx.coroutines.flow.Flow

interface CurriculumRepository {
    fun getCourses(): Flow<List<Course>>
    suspend fun getCourseById(courseId: String): Course?
    suspend fun getLessonById(lessonId: String): Lesson?
    suspend fun updateLessonProgress(lessonId: String, isCompleted: Boolean, accuracy: Float, bpm: Int)
}
