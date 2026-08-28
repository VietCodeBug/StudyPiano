package com.ian.pianotrainer.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ian.pianotrainer.data.local.database.entity.SongTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongTrackDao {
    @Query("SELECT * FROM song_tracks WHERE songId = :songId ORDER BY trackIndex ASC")
    fun getTracksForSongFlow(songId: String): Flow<List<SongTrackEntity>>

    @Query("SELECT * FROM song_tracks WHERE songId = :songId ORDER BY trackIndex ASC")
    suspend fun getTracksForSong(songId: String): List<SongTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<SongTrackEntity>)

    @Query("DELETE FROM song_tracks WHERE songId = :songId")
    suspend fun deleteTracksForSong(songId: String)
}
