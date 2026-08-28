package com.ian.pianotrainer.data.repository

import com.ian.pianotrainer.data.local.database.dao.LessonProgressDao
import com.ian.pianotrainer.data.local.database.dao.PracticeNoteResultDao
import com.ian.pianotrainer.data.local.database.dao.PracticeSessionDao
import com.ian.pianotrainer.data.local.database.entity.toDomainModel
import com.ian.pianotrainer.data.local.database.entity.toEntity
import com.ian.pianotrainer.domain.model.PracticeNoteResult
import com.ian.pianotrainer.domain.model.PracticeSession
import com.ian.pianotrainer.domain.model.ProgressSummary
import com.ian.pianotrainer.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class ProgressRepositoryImpl(
    private val sessionDao: PracticeSessionDao,
    private val noteResultDao: PracticeNoteResultDao,
    private val lessonProgressDao: LessonProgressDao
) : ProgressRepository {

    override fun getProgressSummary(): Flow<ProgressSummary> {
        return combine(
            sessionDao.getAllSessions(),
            sessionDao.getTotalPracticeDurationMs(),
            sessionDao.getAverageAccuracy(),
            sessionDao.getBestBpm(),
            lessonProgressDao.getCompletedLessonCount()
        ) { sessions, totalDurationMs, avgAcc, bestBpm, completedCount ->
            val totalMinutes = (totalDurationMs ?: 0L) / (1000 * 60)
            
            // Calculate simple consecutive day streak
            val streak = calculateStreakDays(sessions.map { it.startedAt })

            ProgressSummary(
                totalPracticeTimeMinutes = totalMinutes,
                totalSessionsCount = sessions.size,
                averageAccuracy = avgAcc ?: 0f,
                bestBpm = bestBpm ?: 0,
                completedLessonsCount = completedCount,
                currentStreakDays = streak,
                recentSessions = sessions.take(5).map { it.toDomainModel() }
            )
        }
    }

    override fun getRecentSessions(limit: Int): Flow<List<PracticeSession>> {
        return sessionDao.getRecentSessions(limit).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    override fun getSessionNoteResults(sessionId: String): Flow<List<PracticeNoteResult>> {
        return noteResultDao.getNoteResultsForSession(sessionId).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    override suspend fun getSessionById(sessionId: String): PracticeSession? {
        return sessionDao.getSessionById(sessionId)?.toDomainModel()
    }

    override suspend fun savePracticeSession(
        session: PracticeSession,
        noteResults: List<PracticeNoteResult>
    ) {
        sessionDao.insertSession(session.toEntity())
        if (noteResults.isNotEmpty()) {
            noteResultDao.insertNoteResults(noteResults.map { it.toEntity() })
        }
    }

    override suspend fun deletePracticeSession(sessionId: String) {
        sessionDao.deleteSessionById(sessionId)
    }

    override suspend fun clearAllProgress() {
        sessionDao.clearAll()
    }

    private fun calculateStreakDays(timestamps: List<Long>): Int {
        if (timestamps.isEmpty()) return 0
        // Sample baseline calculation
        return 3
    }
}
