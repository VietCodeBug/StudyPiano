package com.ian.pianotrainer.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ian.pianotrainer.data.local.database.entity.PracticeSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeSessionDao {
    @Query("SELECT * FROM practice_sessions ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<PracticeSessionEntity>>

    @Query("SELECT * FROM practice_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): Flow<List<PracticeSessionEntity>>

    @Query("SELECT * FROM practice_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): PracticeSessionEntity?

    @Query("SELECT COUNT(*) FROM practice_sessions")
    fun getSessionCount(): Flow<Int>

    @Query("SELECT SUM(durationMs) FROM practice_sessions")
    fun getTotalPracticeDurationMs(): Flow<Long?>

    @Query("SELECT AVG(accuracy) FROM practice_sessions")
    fun getAverageAccuracy(): Flow<Float?>

    @Query("SELECT MAX(bpm) FROM practice_sessions")
    fun getBestBpm(): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PracticeSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<PracticeSessionEntity>)

    @Query("DELETE FROM practice_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: String)

    @Query("DELETE FROM practice_sessions")
    suspend fun clearAll()
}
