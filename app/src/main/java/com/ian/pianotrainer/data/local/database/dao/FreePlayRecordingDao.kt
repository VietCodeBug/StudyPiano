package com.ian.pianotrainer.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ian.pianotrainer.data.local.database.entity.FreePlayRecordedEventEntity
import com.ian.pianotrainer.data.local.database.entity.FreePlayRecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FreePlayRecordingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: FreePlayRecordingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<FreePlayRecordedEventEntity>)

    @Query("SELECT * FROM freeplay_recordings ORDER BY createdAt DESC")
    fun getAllRecordings(): Flow<List<FreePlayRecordingEntity>>

    @Query("SELECT * FROM freeplay_recordings")
    suspend fun getAllRecordingsList(): List<FreePlayRecordingEntity>

    @Query("SELECT * FROM freeplay_recordings WHERE id = :id")
    suspend fun getRecordingById(id: String): FreePlayRecordingEntity?

    @Query("SELECT * FROM freeplay_recorded_events WHERE recordingId = :recordingId ORDER BY timestampMs ASC")
    suspend fun getEventsForRecording(recordingId: String): List<FreePlayRecordedEventEntity>

    @Query("DELETE FROM freeplay_recordings WHERE id = :id")
    suspend fun deleteRecording(id: String)

    @Query("UPDATE freeplay_recordings SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: String, title: String)

    @Query("DELETE FROM freeplay_recordings")
    suspend fun clearAll()
}
