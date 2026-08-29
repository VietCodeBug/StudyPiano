package com.ian.pianotrainer.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ian.pianotrainer.data.local.database.entity.SongPracticePresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongPracticePresetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(preset: SongPracticePresetEntity)

    @Query("SELECT * FROM song_practice_presets WHERE songId = :songId ORDER BY updatedAt DESC")
    fun getPresetsForSong(songId: String): Flow<List<SongPracticePresetEntity>>

    @Query("SELECT * FROM song_practice_presets WHERE id = :id")
    suspend fun getPresetById(id: String): SongPracticePresetEntity?

    @Query("DELETE FROM song_practice_presets WHERE id = :id")
    suspend fun deletePresetById(id: String)

    @Query("DELETE FROM song_practice_presets WHERE songId = :songId")
    suspend fun deletePresetsBySongId(songId: String)

    @Query("SELECT * FROM song_practice_presets")
    suspend fun getAllPresets(): List<SongPracticePresetEntity>

    @Query("DELETE FROM song_practice_presets")
    suspend fun clearAll()
}
