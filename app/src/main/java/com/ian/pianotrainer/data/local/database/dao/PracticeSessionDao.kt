package com.ian.pianotrainer.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ian.pianotrainer.data.local.database.entity.PracticeSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeSessionDao {
    @Query("SELECT * FROM practice_sessions WHERE sessionStatus = 'COMPLETED' ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<PracticeSessionEntity>>

    @Query("SELECT * FROM practice_sessions ORDER BY startedAt DESC")
    suspend fun getAllSessionsList(): List<PracticeSessionEntity>

    @Query("SELECT * FROM practice_sessions WHERE sessionStatus = 'COMPLETED' ORDER BY startedAt DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): Flow<List<PracticeSessionEntity>>

    @Query("SELECT * FROM practice_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): PracticeSessionEntity?

    @Query("SELECT COUNT(*) FROM practice_sessions WHERE sessionStatus = 'COMPLETED'")
    fun getSessionCount(): Flow<Int>

    @Query("SELECT SUM(durationMs) FROM practice_sessions WHERE sessionStatus = 'COMPLETED'")
    fun getTotalPracticeDurationMs(): Flow<Long?>

    @Query("SELECT SUM(durationMs) FROM practice_sessions WHERE sourceId = :sourceId AND sessionStatus = 'COMPLETED'")
    fun getTotalDurationForSource(sourceId: String): Flow<Long?>

    @Query("SELECT SUM(durationMs) FROM practice_sessions WHERE startedAt >= :startOfDayMs AND sessionStatus = 'COMPLETED'")
    fun getTodayPracticeDurationMs(startOfDayMs: Long): Flow<Long?>

    @Query("SELECT SUM(durationMs) FROM practice_sessions WHERE sourceId = :sourceId AND startedAt >= :startOfDayMs AND sessionStatus = 'COMPLETED'")
    fun getTodayDurationForSource(sourceId: String, startOfDayMs: Long): Flow<Long?>

    @Query("SELECT MAX(startedAt) FROM practice_sessions WHERE sourceId = :sourceId AND sessionStatus = 'COMPLETED'")
    fun getLastPracticedTimeForSource(sourceId: String): Flow<Long?>

    @Query("SELECT * FROM practice_sessions WHERE startedAt >= :sinceTime AND sessionStatus = 'COMPLETED' ORDER BY startedAt ASC")
    fun getSessionsSince(sinceTime: Long): Flow<List<PracticeSessionEntity>>

    @Query("SELECT AVG(accuracy) FROM practice_sessions WHERE sessionStatus = 'COMPLETED'")
    fun getAverageAccuracy(): Flow<Float?>

    @Query("SELECT MAX(bpm) FROM practice_sessions WHERE sessionStatus = 'COMPLETED'")
    fun getBestBpm(): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PracticeSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<PracticeSessionEntity>)

    @Query("DELETE FROM practice_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: String)

    @Query("DELETE FROM practice_sessions WHERE id LIKE 'session_demo_%'")
    suspend fun deleteDemoSessions()

    @Query("DELETE FROM practice_sessions")
    suspend fun clearAll()
}
