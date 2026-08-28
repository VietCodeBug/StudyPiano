package com.ian.pianotrainer.data.repository

import com.ian.pianotrainer.data.assets.AssetExerciseDataSource
import com.ian.pianotrainer.data.local.database.dao.PracticeSessionDao
import com.ian.pianotrainer.domain.model.FingerExercise
import com.ian.pianotrainer.domain.repository.ExerciseRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class ExerciseRepositoryImpl(
    private val assetExerciseDataSource: AssetExerciseDataSource,
    private val practiceSessionDao: PracticeSessionDao
) : ExerciseRepository {

    override fun getFingerExercises(): Flow<List<FingerExercise>> = flow {
        val result = assetExerciseDataSource.getExercises()
        val exercises = result.getOrDefault(emptyList())

        val startOfDayMs = LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // Combine session stats for each exercise
        practiceSessionDao.getAllSessions().collect { sessions ->
            val updated = exercises.map { ex ->
                val exSessions = sessions.filter { it.sourceId == ex.id }
                val totalDurationMs = exSessions.sumOf { it.durationMs }
                val todayDurationMs = exSessions.filter { it.startedAt >= startOfDayMs }.sumOf { it.durationMs }
                val lastPracticed = exSessions.maxOfOrNull { it.startedAt }

                ex.copy(
                    totalPracticedSeconds = totalDurationMs / 1000L,
                    todayPracticedSeconds = todayDurationMs / 1000L,
                    lastPracticedAt = lastPracticed
                )
            }
            emit(updated)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getExerciseById(id: String): FingerExercise? = withContext(Dispatchers.IO) {
        val result = assetExerciseDataSource.getExercises()
        val exercises = result.getOrDefault(emptyList())
        val found = exercises.firstOrNull { it.id == id } ?: return@withContext null

        val startOfDayMs = LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val sessions = practiceSessionDao.getSessionById(id)
        found
    }
}
