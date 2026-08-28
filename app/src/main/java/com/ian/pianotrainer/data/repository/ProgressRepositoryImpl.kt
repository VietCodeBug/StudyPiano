package com.ian.pianotrainer.data.repository

import com.ian.pianotrainer.data.local.database.dao.LessonProgressDao
import com.ian.pianotrainer.data.local.database.dao.PracticeNoteResultDao
import com.ian.pianotrainer.data.local.database.dao.PracticeSessionDao
import com.ian.pianotrainer.data.local.database.entity.PracticeSessionEntity
import com.ian.pianotrainer.data.local.database.entity.toDomainModel
import com.ian.pianotrainer.data.local.database.entity.toEntity
import com.ian.pianotrainer.domain.model.DailyPracticeStat
import com.ian.pianotrainer.domain.model.PracticeNoteResult
import com.ian.pianotrainer.domain.model.PracticeSession
import com.ian.pianotrainer.domain.model.ProgressSummary
import com.ian.pianotrainer.domain.repository.ProgressRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class ProgressRepositoryImpl(
    private val sessionDao: PracticeSessionDao,
    private val noteResultDao: PracticeNoteResultDao,
    private val lessonProgressDao: LessonProgressDao
) : ProgressRepository {

    override fun getProgressSummary(): Flow<ProgressSummary> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfTodayMs = today.atStartOfDay(zone).toInstant().toEpochMilli()

        val statsFlow = combine(
            sessionDao.getTotalPracticeDurationMs(),
            sessionDao.getTodayPracticeDurationMs(startOfTodayMs),
            sessionDao.getAverageAccuracy(),
            sessionDao.getBestBpm(),
            lessonProgressDao.getCompletedLessonCount()
        ) { totalDurationMs, todayDurationMs, avgAcc, bestBpm, completedCount ->
            object {
                val totalDurationMs = totalDurationMs
                val todayDurationMs = todayDurationMs
                val avgAcc = avgAcc
                val bestBpm = bestBpm
                val completedCount = completedCount
            }
        }

        return combine(
            sessionDao.getAllSessions(),
            statsFlow
        ) { sessions, stats ->
            val totalMinutes = (stats.totalDurationMs ?: 0L) / (1000 * 60)
            val todayMinutes = (stats.todayDurationMs ?: 0L) / (1000 * 60)

            val validSessions = sessions.filter { it.durationMs > 0 }
            val streak = calculateStreakDays(validSessions, zone)
            val weeklyHistory = calculateWeeklyHistory(validSessions, zone)

            ProgressSummary(
                totalPracticeTimeMinutes = totalMinutes,
                todayPracticeTimeMinutes = todayMinutes,
                totalSessionsCount = validSessions.size,
                averageAccuracy = stats.avgAcc ?: 0f,
                bestBpm = stats.bestBpm ?: 0,
                completedLessonsCount = stats.completedCount,
                currentStreakDays = streak,
                weeklyHistory = weeklyHistory,
                recentSessions = validSessions.take(10).map { it.toDomainModel() }
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

    private fun calculateStreakDays(
        sessions: List<PracticeSessionEntity>,
        zone: ZoneId
    ): Int {
        if (sessions.isEmpty()) return 0

        val practicedDates = sessions
            .map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            .toSet()

        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)

        var checkDate = if (practicedDates.contains(today)) {
            today
        } else if (practicedDates.contains(yesterday)) {
            yesterday
        } else {
            return 0
        }

        var streak = 0
        while (practicedDates.contains(checkDate)) {
            streak++
            checkDate = checkDate.minusDays(1)
        }
        return streak
    }

    private fun calculateWeeklyHistory(
        sessions: List<PracticeSessionEntity>,
        zone: ZoneId
    ): List<DailyPracticeStat> {
        val today = LocalDate.now(zone)
        val stats = mutableListOf<DailyPracticeStat>()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val sessionsByDate = sessions.groupBy {
            Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()
        }

        for (i in 6 downTo 0) {
            val date = today.minusDays(i.toLong())
            val dateSessions = sessionsByDate[date] ?: emptyList()
            val totalMinutes = dateSessions.sumOf { it.durationMs } / (1000 * 60)

            val label = when (date.dayOfWeek) {
                DayOfWeek.MONDAY -> "T2"
                DayOfWeek.TUESDAY -> "T3"
                DayOfWeek.WEDNESDAY -> "T4"
                DayOfWeek.THURSDAY -> "T5"
                DayOfWeek.FRIDAY -> "T6"
                DayOfWeek.SATURDAY -> "T7"
                DayOfWeek.SUNDAY -> "CN"
                else -> "T2"
            }

            stats.add(
                DailyPracticeStat(
                    dayLabel = label,
                    dateIso = date.format(dateFormatter),
                    durationMinutes = totalMinutes
                )
            )
        }
        return stats
    }
}
