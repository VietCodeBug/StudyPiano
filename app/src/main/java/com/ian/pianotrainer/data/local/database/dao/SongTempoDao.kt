package com.ian.pianotrainer.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ian.pianotrainer.data.local.database.entity.SongTempoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongTempoDao {
    @Query("SELECT * FROM song_tempos WHERE songId = :songId ORDER BY startTick ASC")
    fun getTemposForSongFlow(songId: String): Flow<List<SongTempoEntity>>

    @Query("SELECT * FROM song_tempos WHERE songId = :songId ORDER BY startTick ASC")
    suspend fun getTemposForSong(songId: String): List<SongTempoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTempos(tempos: List<SongTempoEntity>)

    @Query("DELETE FROM song_tempos WHERE songId = :songId")
    suspend fun deleteTemposForSong(songId: String)
}
