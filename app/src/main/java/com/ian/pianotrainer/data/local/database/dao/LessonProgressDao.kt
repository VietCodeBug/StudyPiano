package com.ian.pianotrainer.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ian.pianotrainer.data.local.database.entity.LessonProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonProgressDao {
    @Query("SELECT * FROM lesson_progress")
    fun getAllProgress(): Flow<List<LessonProgressEntity>>

    @Query("SELECT * FROM lesson_progress WHERE lessonId = :lessonId LIMIT 1")
    suspend fun getProgressForLesson(lessonId: String): LessonProgressEntity?

    @Query("SELECT * FROM lesson_progress WHERE lessonId = :lessonId LIMIT 1")
    fun observeProgressForLesson(lessonId: String): Flow<LessonProgressEntity?>

    @Query("SELECT COUNT(*) FROM lesson_progress WHERE isCompleted = 1")
    fun getCompletedLessonCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProgress(progress: LessonProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProgressList(progressList: List<LessonProgressEntity>)

    @Query("DELETE FROM lesson_progress")
    suspend fun clearAll()
}
