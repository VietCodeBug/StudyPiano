package com.ian.pianotrainer.data.repository

import android.content.Context
import com.ian.pianotrainer.core.music.midi.MidiFileParser
import com.ian.pianotrainer.data.local.database.dao.ImportedSongDao
import com.ian.pianotrainer.data.local.database.dao.SongNoteDao
import com.ian.pianotrainer.data.local.database.dao.SongTempoDao
import com.ian.pianotrainer.data.local.database.dao.SongTrackDao
import com.ian.pianotrainer.data.local.database.entity.ImportedSongEntity
import com.ian.pianotrainer.data.local.database.entity.SongNoteEntity
import com.ian.pianotrainer.data.local.database.entity.SongTempoEntity
import com.ian.pianotrainer.data.local.database.entity.SongTrackEntity
import com.ian.pianotrainer.data.local.database.entity.toDomainModel
import com.ian.pianotrainer.data.local.database.entity.toEntity
import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.ImportedSong
import com.ian.pianotrainer.domain.repository.SongRepository
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SongRepositoryImpl(
    private val context: Context,
    private val importedSongDao: ImportedSongDao,
    private val songTrackDao: SongTrackDao,
    private val songNoteDao: SongNoteDao,
    private val songTempoDao: SongTempoDao
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

    override suspend fun getSongById(id: String): ImportedSong? = withContext(Dispatchers.IO) {
        val songEntity = importedSongDao.getSongById(id) ?: return@withContext null
        val songNotes = songNoteDao.getNotesForSong(id)
        val selectedTracks = songTrackDao.getTracksForSong(id).filter { it.isSelectedForPractice }.map { it.trackIndex }.toSet()

        val activeNotes = songNotes.filter { it.trackIndex in selectedTracks }.sortedWith(compareBy({ it.startMs }, { it.midiNote }))
        val exerciseNotes = activeNotes.map { sn ->
            ExerciseNote(
                midiNote = sn.midiNote,
                noteName = midiNoteToName(sn.midiNote),
                durationBeats = (sn.durationMs / 500.0).coerceAtLeast(0.25),
                fingerNumber = 1,
                hand = runCatching { HandMode.valueOf(sn.assignedHand) }.getOrDefault(HandMode.RIGHT),
                startMs = sn.startMs,
                durationMs = sn.durationMs,
                trackIndex = sn.trackIndex,
                velocity = sn.velocity,
                chordId = sn.chordId
            )
        }

        songEntity.toDomainModel().copy(notes = exerciseNotes)
    }

    override suspend fun getSongTracks(songId: String): List<SongTrackEntity> = withContext(Dispatchers.IO) {
        songTrackDao.getTracksForSong(songId)
    }

    override suspend fun getSongNotes(songId: String): List<SongNoteEntity> = withContext(Dispatchers.IO) {
        songNoteDao.getNotesForSong(songId)
    }

    override suspend fun importMidiFile(
        inputStream: InputStream,
        originalFileName: String,
        fileSize: Long,
        customTitle: String?
    ): Result<ImportedSong> = withContext(Dispatchers.IO) {
        var createdFileDir: File? = null
        try {
            val bytes = inputStream.readBytes()
            if (bytes.size < 14) {
                return@withContext Result.failure(IllegalArgumentException("File MIDI quá nhỏ hoặc không hợp lệ."))
            }

            // Calculate SHA-256
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(bytes)
            val fileHash = hashBytes.joinToString("") { "%02x".format(it) }

            // Check if duplicate hash exists
            val existing = importedSongDao.getSongByHash(fileHash)
            if (existing != null) {
                return@withContext Result.failure(DuplicateMidiException(existing.id, existing.displayName))
            }

            // Parse MIDI bytes
            val parsedMidi = MidiFileParser.parse(bytes)

            val songId = "song_" + UUID.randomUUID().toString().replace("-", "").take(12)
            val songsDir = File(context.filesDir, "songs/$songId")
            if (!songsDir.exists()) {
                songsDir.mkdirs()
            }
            createdFileDir = songsDir

            val destinationFile = File(songsDir, "source.mid")
            FileOutputStream(destinationFile).use { it.write(bytes) }

            val totalNotesCount = parsedMidi.tracks.sumOf { it.noteCount }
            val cleanTitle = customTitle?.takeIf { it.isNotBlank() }
                ?: originalFileName.substringBeforeLast(".").replace("_", " ").trim()

            val difficulty = when {
                totalNotesCount < 80 -> "Cơ bản"
                totalNotesCount < 250 -> "Dễ"
                totalNotesCount < 600 -> "Trung bình"
                else -> "Nâng cao"
            }

            val songEntity = ImportedSongEntity(
                id = songId,
                displayName = cleanTitle,
                originalFileName = originalFileName,
                localFilePath = destinationFile.absolutePath,
                fileHashSha256 = fileHash,
                fileSizeBytes = bytes.size.toLong(),
                midiFormatType = parsedMidi.format,
                ticksPerQuarterNote = parsedMidi.ticksPerQuarterNote,
                trackCount = parsedMidi.tracks.size,
                durationMs = parsedMidi.durationMs,
                defaultBpm = parsedMidi.defaultBpm,
                difficulty = difficulty,
                importedAt = System.currentTimeMillis(),
                lastPracticedAt = null,
                isFavorite = false,
                parseStatus = "READY",
                parseErrorMessage = null
            )

            val trackEntities = parsedMidi.tracks.map { pt ->
                SongTrackEntity(
                    songId = songId,
                    trackIndex = pt.trackIndex,
                    trackName = pt.trackName,
                    channelSummary = pt.channelSummary,
                    instrumentNumber = pt.instrumentNumber,
                    noteCount = pt.noteCount,
                    minMidiNote = pt.minMidiNote,
                    maxMidiNote = pt.maxMidiNote,
                    isSelectedForPractice = true,
                    assignedHand = pt.defaultHand
                )
            }

            val noteEntities = mutableListOf<SongNoteEntity>()
            for (track in parsedMidi.tracks) {
                for (n in track.notes) {
                    noteEntities.add(
                        SongNoteEntity(
                            songId = songId,
                            trackIndex = n.trackIndex,
                            channel = n.channel,
                            midiNote = n.midiNote,
                            velocity = n.velocity,
                            startTick = n.startTick,
                            endTick = n.endTick,
                            startMs = n.startMs,
                            durationMs = n.durationMs,
                            assignedHand = n.assignedHand,
                            chordId = n.chordId
                        )
                    )
                }
            }

            val tempoEntities = parsedMidi.tempos.map { te ->
                SongTempoEntity(
                    songId = songId,
                    startTick = te.tick,
                    startMs = te.startMs,
                    microsecondsPerQuarterNote = te.microsecondsPerQuarterNote,
                    bpm = te.bpm
                )
            }

            // Save to database
            importedSongDao.insertSong(songEntity)
            songTrackDao.insertTracks(trackEntities)
            songNoteDao.insertNotes(noteEntities)
            songTempoDao.insertTempos(tempoEntities)

            Result.success(songEntity.toDomainModel())
        } catch (e: Exception) {
            createdFileDir?.deleteRecursively()
            Result.failure(e)
        }
    }

    override suspend fun updateTrackConfigurations(songId: String, tracks: List<SongTrackEntity>) = withContext(Dispatchers.IO) {
        songTrackDao.insertTracks(tracks)
        // Also update notes assignedHand according to track assignment
        val notes = songNoteDao.getNotesForSong(songId)
        val trackMap = tracks.associateBy { it.trackIndex }
        val updatedNotes = notes.map { n ->
            val assigned = trackMap[n.trackIndex]?.assignedHand ?: n.assignedHand
            n.copy(assignedHand = assigned)
        }
        songNoteDao.insertNotes(updatedNotes)
    }

    override suspend fun renameSong(id: String, newName: String) = withContext(Dispatchers.IO) {
        val song = importedSongDao.getSongById(id) ?: return@withContext
        val updated = song.copy(displayName = newName.trim())
        importedSongDao.updateSong(updated)
    }

    override suspend fun toggleFavorite(id: String) = withContext(Dispatchers.IO) {
        val song = importedSongDao.getSongById(id) ?: return@withContext
        val updated = song.copy(isFavorite = !song.isFavorite)
        importedSongDao.updateSong(updated)
    }

    override suspend fun deleteSong(id: String) = withContext(Dispatchers.IO) {
        importedSongDao.deleteSongById(id)
        val songsDir = File(context.filesDir, "songs/$id")
        if (songsDir.exists()) {
            songsDir.deleteRecursively()
        }
    }

    override suspend fun updateLastPracticed(id: String) = withContext(Dispatchers.IO) {
        val song = importedSongDao.getSongById(id) ?: return@withContext
        val updated = song.copy(lastPracticedAt = System.currentTimeMillis())
        importedSongDao.updateSong(updated)
    }

    private fun midiNoteToName(midiNote: Int): String {
        val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val octave = (midiNote / 12) - 1
        val noteIndex = midiNote % 12
        return "${noteNames[noteIndex]}$octave"
    }
}

class DuplicateMidiException(val existingSongId: String, val songTitle: String) :
    Exception("Bản nhạc này đã tồn tại trong thư viện với tên: \"$songTitle\"")
