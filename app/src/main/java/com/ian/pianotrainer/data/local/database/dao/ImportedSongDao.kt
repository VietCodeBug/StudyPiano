package com.ian.pianotrainer.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ian.pianotrainer.data.local.database.entity.ImportedSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportedSongDao {
    @Query("SELECT * FROM imported_songs ORDER BY importedAt DESC")
    fun getAllSongs(): Flow<List<ImportedSongEntity>>

    @Query("SELECT * FROM imported_songs WHERE isFavorite = 1 ORDER BY importedAt DESC")
    fun getFavoriteSongs(): Flow<List<ImportedSongEntity>>

    @Query("SELECT * FROM imported_songs WHERE id = :id LIMIT 1")
    suspend fun getSongById(id: String): ImportedSongEntity?

    @Query("SELECT * FROM imported_songs WHERE fileHashSha256 = :hash LIMIT 1")
    suspend fun getSongByHash(hash: String): ImportedSongEntity?

    @Query("SELECT COUNT(*) FROM imported_songs")
    suspend fun getSongCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: ImportedSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<ImportedSongEntity>)

    @Update
    suspend fun updateSong(song: ImportedSongEntity)

    @Query("DELETE FROM imported_songs WHERE id = :id")
    suspend fun deleteSongById(id: String)

    @Query("DELETE FROM imported_songs WHERE id LIKE 'song_demo_%'")
    suspend fun deleteDemoSongs()

    @Query("DELETE FROM imported_songs")
    suspend fun clearAll()
}
