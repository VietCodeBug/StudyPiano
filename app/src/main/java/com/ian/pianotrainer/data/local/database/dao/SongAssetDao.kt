package com.ian.pianotrainer.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ian.pianotrainer.data.local.database.entity.SongAssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongAssetDao {
    @Query("SELECT * FROM song_assets ORDER BY songId, type")
    fun getAllAssets(): Flow<List<SongAssetEntity>>

    @Query("SELECT * FROM song_assets WHERE songId = :songId ORDER BY type")
    suspend fun getAssetsForSong(songId: String): List<SongAssetEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAssets(assets: List<SongAssetEntity>)
}
