package com.ian.pianotrainer.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ian.pianotrainer.data.local.database.entity.SongTimeSignatureEntity

@Dao
interface SongTimeSignatureDao {
    @Query("SELECT * FROM song_time_signatures WHERE songId = :songId ORDER BY startTick ASC")
    suspend fun getTimeSignaturesForSong(songId: String): List<SongTimeSignatureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeSignatures(timeSignatures: List<SongTimeSignatureEntity>)
}
