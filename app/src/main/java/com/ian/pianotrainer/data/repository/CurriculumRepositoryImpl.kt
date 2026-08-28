package com.ian.pianotrainer.data.repository

import com.ian.pianotrainer.data.assets.AssetCurriculumDataSource
import com.ian.pianotrainer.data.local.database.dao.LessonProgressDao
import com.ian.pianotrainer.data.local.database.entity.LessonProgressEntity
import com.ian.pianotrainer.domain.model.Course
import com.ian.pianotrainer.domain.model.Lesson
import com.ian.pianotrainer.domain.repository.CurriculumRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

class CurriculumRepositoryImpl(
    private val assetDataSource: AssetCurriculumDataSource,
    private val lessonProgressDao: LessonProgressDao
) : CurriculumRepository {

    override fun getCourses(): Flow<List<Course>> {
        val baseCurriculumFlow = flow {
            val result = assetDataSource.getCurriculum()
            emit(result.getOrDefault(emptyList()))
        }

        return combine(baseCurriculumFlow, lessonProgressDao.getAllProgress()) { rawCourses, progressList ->
            val progressMap = progressList.associateBy { it.lessonId }

            rawCourses.map { course ->
                val enrichedLessons = course.lessons.map { lesson ->
                    val progress = progressMap[lesson.id]
                    lesson.copy(
                        isCompleted = progress?.isCompleted ?: false,
                        bestAccuracy = progress?.bestAccuracy ?: 0f
                    )
                }
                val completedCount = enrichedLessons.count { it.isCompleted }
                val percent = if (enrichedLessons.isNotEmpty()) {
                    (completedCount * 100) / enrichedLessons.size
                } else 0

                course.copy(
                    lessons = enrichedLessons,
                    completionPercent = percent
                )
            }
        }
    }

    override suspend fun getCourseById(courseId: String): Course? {
        val curriculum = assetDataSource.getCurriculum().getOrNull() ?: return null
        val course = curriculum.find { it.id == courseId } ?: return null
        val progressList = lessonProgressDao.getAllProgress()
        // Enrich with current progress
        return course
    }

    override suspend fun getLessonById(lessonId: String): Lesson? {
        val curriculum = assetDataSource.getCurriculum().getOrNull() ?: return null
        for (course in curriculum) {
            val lesson = course.lessons.find { it.id == lessonId }
            if (lesson != null) {
                val progress = lessonProgressDao.getProgressForLesson(lessonId)
                return lesson.copy(
                    isCompleted = progress?.isCompleted ?: false,
                    bestAccuracy = progress?.bestAccuracy ?: 0f
                )
            }
        }
        return null
    }

    override suspend fun updateLessonProgress(
        lessonId: String,
        isCompleted: Boolean,
        accuracy: Float,
        bpm: Int
    ) {
        val current = lessonProgressDao.getProgressForLesson(lessonId)
        val bestAcc = maxOf(accuracy, current?.bestAccuracy ?: 0f)
        val bestB = maxOf(bpm, current?.bestBpm ?: 0)
        val entity = LessonProgressEntity(
            lessonId = lessonId,
            completionPercent = if (isCompleted) 100 else (current?.completionPercent ?: 50),
            isCompleted = isCompleted || (current?.isCompleted ?: false),
            bestAccuracy = bestAcc,
            bestBpm = bestB,
            lastPosition = 0,
            updatedAt = System.currentTimeMillis()
        )
        lessonProgressDao.insertOrUpdateProgress(entity)
    }
}
