package com.ian.pianotrainer.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lesson_progress")
data class LessonProgressEntity(
    @PrimaryKey val lessonId: String,
    val completionPercent: Int,
    val isCompleted: Boolean,
    val bestAccuracy: Float,
    val bestBpm: Int,
    val lastPosition: Int,
    val updatedAt: Long
)
