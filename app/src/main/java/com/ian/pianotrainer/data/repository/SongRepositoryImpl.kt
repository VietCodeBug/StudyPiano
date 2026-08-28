package com.ian.pianotrainer.data.repository

import com.ian.pianotrainer.data.local.database.dao.ImportedSongDao
import com.ian.pianotrainer.data.local.database.entity.toDomainModel
import com.ian.pianotrainer.data.local.database.entity.toEntity
import com.ian.pianotrainer.domain.model.ImportedSong
import com.ian.pianotrainer.domain.repository.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SongRepositoryImpl(
    private val importedSongDao: ImportedSongDao
) : SongRepository {

    override fun getAllSongs(): Flow<List<ImportedSong>> {
        return importedSongDao.getAllSongs().map { list ->
            list.map { it.toDomainModel() }
        }
    }

    override fun getFavoriteSongs(): Flow<List<ImportedSong>> {
        return importedSongDao.getFavoriteSongs().map { list ->
            list.map { it.toDomainModel() }
        }
    }

    override suspend fun getSongById(id: String): ImportedSong? {
        return importedSongDao.getSongById(id)?.toDomainModel()
    }

    override suspend fun insertSong(song: ImportedSong) {
        importedSongDao.insertSong(song.toEntity())
    }

    override suspend fun toggleFavorite(id: String) {
        val song = importedSongDao.getSongById(id) ?: return
        val updated = song.copy(isFavorite = !song.isFavorite)
        importedSongDao.updateSong(updated)
    }

    override suspend fun deleteSong(id: String) {
        importedSongDao.deleteSongById(id)
    }

    override suspend fun updateLastPracticed(id: String) {
        val song = importedSongDao.getSongById(id) ?: return
        val updated = song.copy(lastPracticedAt = System.currentTimeMillis())
        importedSongDao.updateSong(updated)
    }
}
