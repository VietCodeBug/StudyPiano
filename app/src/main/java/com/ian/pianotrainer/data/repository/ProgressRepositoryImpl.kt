package com.ian.pianotrainer.data.repository

import com.ian.pianotrainer.core.music.NoteHelper
import com.ian.pianotrainer.data.local.database.dao.LessonProgressDao
import com.ian.pianotrainer.data.local.database.dao.PracticeNoteResultDao
import com.ian.pianotrainer.data.local.database.dao.PracticeSessionDao
import com.ian.pianotrainer.data.local.database.entity.PracticeSessionEntity
import com.ian.pianotrainer.data.local.database.entity.toDomainModel
import com.ian.pianotrainer.data.local.database.entity.toEntity
import com.ian.pianotrainer.domain.model.DailyPracticeStat
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.domain.model.PracticeNoteResult
import com.ian.pianotrainer.domain.model.PracticeSession
import com.ian.pianotrainer.domain.model.ProgressSummary
import com.ian.pianotrainer.domain.model.WeakPitchStat
import com.ian.pianotrainer.domain.repository.ProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ProgressRepositoryImpl(
    private val sessionDao: PracticeSessionDao,
    private val noteResultDao: PracticeNoteResultDao,
    private val lessonProgressDao: LessonProgressDao
) : ProgressRepository {

    override fun getProgressSummary(daysFilter: Int?): Flow<ProgressSummary> {
        return combine(
            sessionDao.getAllSessions(),
            noteResultDao.getAllNoteResults(),
            lessonProgressDao.getCompletedLessonCount()
        ) { sessions, noteResults, completedCount ->
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val startOfTodayMs = today.atStartOfDay(zone).toInstant().toEpochMilli()

            val filteredSessions = if (daysFilter != null && daysFilter > 0) {
                val cutoffMs = today.minusDays(daysFilter.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
                sessions.filter { it.startedAt >= cutoffMs }
            } else {
                sessions
            }

            val validSessions = filteredSessions.filter { it.durationMs > 0 }
            val totalMinutes = validSessions.sumOf { it.durationMs } / (1000 * 60)

            val todayMinutes = validSessions
                .filter { it.startedAt >= startOfTodayMs }
                .sumOf { it.durationMs } / (1000 * 60)

            // Weighted Accuracy: totalCorrect / totalExpected across valid sessions
            val totalExpected = validSessions.sumOf { it.totalExpectedNotes }
            val totalCorrect = validSessions.sumOf { it.correctNotes }
            val weightedAcc = if (totalExpected > 0) totalCorrect.toFloat() / totalExpected.toFloat() else 0f

            val avgAcc = if (validSessions.isNotEmpty()) {
                validSessions.map { it.accuracy }.average().toFloat()
            } else 0f

            val bestBpm = validSessions.maxOfOrNull { it.bpm } ?: 0

            val (currentStreak, longestStreak) = calculateStreaks(sessions.filter { it.durationMs > 0 }, zone)
            val chartDays = daysFilter ?: 7
            val history = calculateDailyHistory(validSessions, zone, chartDays)

            // Weak Pitches Analysis
            val weakPitches = calculateWeakPitches(noteResults)

            // Most Practiced Song
            val songSessions = validSessions.filter { it.sourceTitleSnapshot != null }
            val mostPracticed = songSessions.groupBy { it.sourceTitleSnapshot }
                .maxByOrNull { it.value.size }
            val mostPracticedTitle = mostPracticed?.key
            val mostPracticedCount = mostPracticed?.value?.size ?: 0

            ProgressSummary(
                totalPracticeTimeMinutes = totalMinutes,
                todayPracticeTimeMinutes = todayMinutes,
                totalSessionsCount = validSessions.size,
                averageAccuracy = avgAcc,
                weightedAccuracy = weightedAcc,
                bestBpm = bestBpm,
                completedLessonsCount = completedCount,
                currentStreakDays = currentStreak,
                longestStreakDays = longestStreak,
                weeklyHistory = history,
                recentSessions = validSessions.take(15).map { it.toDomainModel() },
                weakPitches = weakPitches,
                mostPracticedSongTitle = mostPracticedTitle,
                mostPracticedSongCount = mostPracticedCount
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

    override suspend fun getAllSessionsList(): List<PracticeSession> = withContext(Dispatchers.IO) {
        sessionDao.getAllSessionsList().map { it.toDomainModel() }
    }

    override suspend fun getAllNoteResultsList(): List<PracticeNoteResult> = withContext(Dispatchers.IO) {
        noteResultDao.getAllNoteResultsList().map { it.toDomainModel() }
    }

    override suspend fun getSessionById(sessionId: String): PracticeSession? = withContext(Dispatchers.IO) {
        sessionDao.getSessionById(sessionId)?.toDomainModel()
    }

    override suspend fun savePracticeSession(
        session: PracticeSession,
        noteResults: List<PracticeNoteResult>
    ) = withContext(Dispatchers.IO) {
        sessionDao.insertSession(session.toEntity())
        if (noteResults.isNotEmpty()) {
            noteResultDao.insertNoteResults(noteResults.map { it.toEntity() })
        }
    }

    override suspend fun deletePracticeSession(sessionId: String) = withContext(Dispatchers.IO) {
        sessionDao.deleteSessionById(sessionId)
    }

    override suspend fun clearAllProgress() = withContext(Dispatchers.IO) {
        noteResultDao.clearAll()
        sessionDao.clearAll()
    }

    private fun calculateStreaks(
        sessions: List<PracticeSessionEntity>,
        zone: ZoneId
    ): Pair<Int, Int> {
        if (sessions.isEmpty()) return 0 to 0

        val practicedDates = sessions
            .map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            .toSet()

        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)

        var currentStreak = 0
        var checkDate = if (practicedDates.contains(today)) {
            today
        } else if (practicedDates.contains(yesterday)) {
            yesterday
        } else {
            null
        }

        while (checkDate != null && practicedDates.contains(checkDate)) {
            currentStreak++
            checkDate = checkDate.minusDays(1)
        }

        // Longest Streak across all dates
        val sortedDates = practicedDates.sorted()
        var longestStreak = 0
        var tempStreak = 0
        var prevDate: LocalDate? = null

        for (date in sortedDates) {
            if (prevDate == null || date == prevDate.plusDays(1)) {
                tempStreak++
            } else {
                tempStreak = 1
            }
            if (tempStreak > longestStreak) {
                longestStreak = tempStreak
            }
            prevDate = date
        }

        return currentStreak to maxOf(currentStreak, longestStreak)
    }

    private fun calculateDailyHistory(
        sessions: List<PracticeSessionEntity>,
        zone: ZoneId,
        days: Int
    ): List<DailyPracticeStat> {
        val today = LocalDate.now(zone)
        val stats = mutableListOf<DailyPracticeStat>()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val sessionsByDate = sessions.groupBy {
            Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()
        }

        val count = days.coerceIn(7, 30)
        for (i in (count - 1) downTo 0) {
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
                    dayLabel = if (count <= 7) label else "${date.dayOfMonth}/${date.monthValue}",
                    dateIso = date.format(dateFormatter),
                    durationMinutes = totalMinutes
                )
            )
        }
        return stats
    }

    private fun calculateWeakPitches(
        noteResults: List<com.ian.pianotrainer.data.local.database.entity.PracticeNoteResultEntity>
    ): List<WeakPitchStat> {
        val mistakeResults = noteResults.filter { it.resultType == "WRONG" || it.resultType == "MISSED" }
        if (mistakeResults.isEmpty()) return emptyList()

        val grouped = mistakeResults.groupBy { it.expectedMidiNote ?: it.playedMidiNote ?: 0 }
            .filter { it.key in 21..108 }

        return grouped.map { (pitch, results) ->
            val wrongCount = results.count { it.resultType == "WRONG" }
            val missedCount = results.count { it.resultType == "MISSED" }
            WeakPitchStat(
                midiNote = pitch,
                noteName = NoteHelper.midiToNoteName(pitch, NoteNamingMode.CDE),
                wrongCount = wrongCount,
                missedCount = missedCount,
                totalMistakes = wrongCount + missedCount
            )
        }.sortedByDescending { it.totalMistakes }.take(5)
    }
}
