package com.ian.pianotrainer.domain.repository

import com.ian.pianotrainer.domain.model.ImportedSong
import kotlinx.coroutines.flow.Flow

interface SongRepository {
    fun getAllSongs(): Flow<List<ImportedSong>>
    fun getFavoriteSongs(): Flow<List<ImportedSong>>
    suspend fun getSongById(id: String): ImportedSong?
    suspend fun insertSong(song: ImportedSong)
    suspend fun toggleFavorite(id: String)
    suspend fun deleteSong(id: String)
    suspend fun updateLastPracticed(id: String)
}
