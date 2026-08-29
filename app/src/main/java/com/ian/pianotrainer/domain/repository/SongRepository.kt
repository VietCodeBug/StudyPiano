package com.ian.pianotrainer.domain.repository

import com.ian.pianotrainer.data.local.database.entity.SongNoteEntity
import com.ian.pianotrainer.data.local.database.entity.SongTrackEntity
import com.ian.pianotrainer.domain.model.ImportedSong
import com.ian.pianotrainer.domain.model.SongPlaybackData
import com.ian.pianotrainer.domain.model.SongTimeSignature
import java.io.InputStream
import kotlinx.coroutines.flow.Flow

interface SongRepository {
    fun getAllSongs(): Flow<List<ImportedSong>>
    fun getFavoriteSongs(): Flow<List<ImportedSong>>
    suspend fun getSongById(id: String): ImportedSong?
    suspend fun getSongPlaybackData(id: String): SongPlaybackData?
    suspend fun getSongTracks(songId: String): List<SongTrackEntity>
    suspend fun getSongNotes(songId: String): List<SongNoteEntity>
    suspend fun getSongTimeSignatures(songId: String): List<SongTimeSignature>
    suspend fun importMidiFile(
        inputStream: InputStream,
        originalFileName: String,
        fileSize: Long,
        customTitle: String? = null
    ): Result<ImportedSong>
    suspend fun updateTrackConfigurations(songId: String, tracks: List<SongTrackEntity>)
    suspend fun renameSong(id: String, newName: String)
    suspend fun toggleFavorite(id: String)
    suspend fun deleteSong(id: String)
    suspend fun updateLastPracticed(id: String)
}
