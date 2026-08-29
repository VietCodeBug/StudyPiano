package com.ian.pianotrainer.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ian.pianotrainer.data.local.database.entity.PracticeNoteResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeNoteResultDao {
    @Query("SELECT * FROM practice_note_results WHERE sessionId = :sessionId ORDER BY occurredAtOffsetMs ASC")
    fun getNoteResultsForSession(sessionId: String): Flow<List<PracticeNoteResultEntity>>

    @Query("SELECT * FROM practice_note_results")
    fun getAllNoteResults(): Flow<List<PracticeNoteResultEntity>>

    @Query("SELECT * FROM practice_note_results")
    suspend fun getAllNoteResultsList(): List<PracticeNoteResultEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoteResults(results: List<PracticeNoteResultEntity>)

    @Query("DELETE FROM practice_note_results WHERE sessionId = :sessionId")
    suspend fun deleteResultsForSession(sessionId: String)

    @Query("DELETE FROM practice_note_results")
    suspend fun clearAll()
}
