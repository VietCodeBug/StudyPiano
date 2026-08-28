package com.ian.pianotrainer.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ian.pianotrainer.data.local.database.entity.SongNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongNoteDao {
    @Query("SELECT * FROM song_notes WHERE songId = :songId ORDER BY startMs ASC")
    fun getNotesForSongFlow(songId: String): Flow<List<SongNoteEntity>>

    @Query("SELECT * FROM song_notes WHERE songId = :songId ORDER BY startMs ASC")
    suspend fun getNotesForSong(songId: String): List<SongNoteEntity>

    @Query("SELECT * FROM song_notes WHERE songId = :songId AND startMs BETWEEN :fromMs AND :toMs ORDER BY startMs ASC")
    suspend fun getNotesInTimeWindow(songId: String, fromMs: Long, toMs: Long): List<SongNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<SongNoteEntity>)

    @Query("DELETE FROM song_notes WHERE songId = :songId")
    suspend fun deleteNotesForSong(songId: String)
}
