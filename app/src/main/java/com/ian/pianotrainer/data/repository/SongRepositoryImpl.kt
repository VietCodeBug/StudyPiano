package com.ian.pianotrainer.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.ian.pianotrainer.core.music.midi.MidiFileParser
import com.ian.pianotrainer.core.music.midi.MidiParseException
import com.ian.pianotrainer.data.local.database.PianoTrainerDatabase
import com.ian.pianotrainer.data.local.database.dao.ImportedSongDao
import com.ian.pianotrainer.data.local.database.dao.SongNoteDao
import com.ian.pianotrainer.data.local.database.dao.SongTempoDao
import com.ian.pianotrainer.data.local.database.dao.SongTimeSignatureDao
import com.ian.pianotrainer.data.local.database.dao.SongTrackDao
import com.ian.pianotrainer.data.local.database.entity.ImportedSongEntity
import com.ian.pianotrainer.data.local.database.entity.SongNoteEntity
import com.ian.pianotrainer.data.local.database.entity.SongTempoEntity
import com.ian.pianotrainer.data.local.database.entity.SongTimeSignatureEntity
import com.ian.pianotrainer.data.local.database.entity.SongTrackEntity
import com.ian.pianotrainer.data.local.database.entity.toDomain
import com.ian.pianotrainer.data.local.database.entity.toDomainModel
import com.ian.pianotrainer.data.local.database.entity.toEntity
import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.ImportedSong
import com.ian.pianotrainer.domain.model.SongPlaybackData
import com.ian.pianotrainer.domain.model.SongPracticePreset
import com.ian.pianotrainer.domain.model.SongTempoInfo
import com.ian.pianotrainer.domain.model.SongTimeSignature
import com.ian.pianotrainer.domain.model.SongTrackInfo
import com.ian.pianotrainer.domain.repository.SongRepository
import java.io.File
import java.io.FileInputStream
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
    private val database: PianoTrainerDatabase,
    private val importedSongDao: ImportedSongDao,
    private val songTrackDao: SongTrackDao,
    private val songNoteDao: SongNoteDao,
    private val songTempoDao: SongTempoDao,
    private val songTimeSignatureDao: SongTimeSignatureDao
) : SongRepository {

    companion object {
        const val MAX_MIDI_FILE_SIZE_BYTES = 20L * 1024L * 1024L // 20 MB
    }

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
        val tempos = songTempoDao.getTemposForSong(id)
        val defaultBpm = songEntity.defaultBpm.coerceIn(30, 240)

        val activeNotes = songNotes.filter { it.trackIndex in selectedTracks }.sortedWith(compareBy({ it.startMs }, { it.midiNote }))
        val exerciseNotes = activeNotes.map { sn ->
            val noteTempoBpm = tempos.lastOrNull { it.startMs <= sn.startMs }?.bpm ?: defaultBpm
            val msPerBeat = 60000.0 / noteTempoBpm.coerceIn(30, 240)
            val durationBeats = (sn.durationMs / msPerBeat).coerceAtLeast(0.1)

            ExerciseNote(
                midiNote = sn.midiNote,
                noteName = midiNoteToName(sn.midiNote),
                durationBeats = durationBeats,
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

    override suspend fun getSongPlaybackData(id: String): SongPlaybackData? = withContext(Dispatchers.IO) {
        val song = getSongById(id) ?: return@withContext null
        val tracks = songTrackDao.getTracksForSong(id)
        val tempos = songTempoDao.getTemposForSong(id)
        val signatures = songTimeSignatureDao.getTimeSignaturesForSong(id)

        SongPlaybackData(
            song = song,
            notes = song.notes,
            tracks = tracks.map {
                SongTrackInfo(
                    trackIndex = it.trackIndex,
                    trackName = it.trackName,
                    channelSummary = it.channelSummary,
                    instrumentNumber = it.instrumentNumber,
                    noteCount = it.noteCount,
                    minMidiNote = it.minMidiNote,
                    maxMidiNote = it.maxMidiNote,
                    isSelectedForPractice = it.isSelectedForPractice,
                    assignedHand = it.assignedHand
                )
            },
            tempos = tempos.map {
                SongTempoInfo(
                    startTick = it.startTick,
                    startMs = it.startMs,
                    microsecondsPerQuarterNote = it.microsecondsPerQuarterNote,
                    bpm = it.bpm
                )
            },
            timeSignatures = signatures.map { it.toDomainModel() }
        )
    }

    override suspend fun getSongTracks(songId: String): List<SongTrackEntity> = withContext(Dispatchers.IO) {
        songTrackDao.getTracksForSong(songId)
    }

    override suspend fun getSongNotes(songId: String): List<SongNoteEntity> = withContext(Dispatchers.IO) {
        songNoteDao.getNotesForSong(songId)
    }

    override suspend fun getSongTimeSignatures(songId: String): List<SongTimeSignature> = withContext(Dispatchers.IO) {
        songTimeSignatureDao.getTimeSignaturesForSong(songId).map { it.toDomainModel() }
    }

    override suspend fun importMidiFile(
        inputStream: InputStream,
        originalFileName: String,
        fileSize: Long,
        customTitle: String?
    ): Result<ImportedSong> = withContext(Dispatchers.IO) {
        // 1. Initial size check from metadata provider
        if (fileSize > MAX_MIDI_FILE_SIZE_BYTES) {
            return@withContext Result.failure(
                MidiFileTooLargeException("Kích thước file vượt quá giới hạn 20MB (${fileSize / (1024 * 1024)}MB).")
            )
        }

        val tempFile = File(context.cacheDir, "midi_import_${UUID.randomUUID()}.tmp")
        var targetSongDir: File? = null

        try {
            // 2. Stream chunk-by-chunk to temporary file with size counting & SHA-256 hashing
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytesRead = 0L
            val buffer = ByteArray(64 * 1024)

            FileOutputStream(tempFile).use { fos ->
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    totalBytesRead += bytesRead
                    if (totalBytesRead > MAX_MIDI_FILE_SIZE_BYTES) {
                        throw MidiFileTooLargeException("Kích thước file vượt quá giới hạn 20MB.")
                    }
                    digest.update(buffer, 0, bytesRead)
                    fos.write(buffer, 0, bytesRead)
                }
            }

            if (totalBytesRead < 14) {
                throw InvalidMidiFileException("File quá nhỏ hoặc không phải định dạng MIDI chuẩn.")
            }

            val fileHash = digest.digest().joinToString("") { "%02x".format(it) }

            // 3. Check for duplicates in database
            val existing = importedSongDao.getSongByHash(fileHash)
            if (existing != null) {
                throw DuplicateMidiException(existing.id, existing.displayName)
            }

            // 4. Parse MIDI from temp file
            val parsedMidi = try {
                FileInputStream(tempFile).use { MidiFileParser.parse(it) }
            } catch (e: MidiParseException) {
                throw InvalidMidiFileException(e.message ?: "Lỗi phân tích file MIDI.")
            } catch (e: Exception) {
                throw InvalidMidiFileException("Không thể đọc cấu trúc file MIDI: ${e.message}")
            }

            val songId = "song_" + UUID.randomUUID().toString().replace("-", "").take(12)
            val songsDir = File(context.filesDir, "songs/$songId")
            if (!songsDir.exists()) {
                songsDir.mkdirs()
            }
            targetSongDir = songsDir

            val destinationFile = File(songsDir, "source.mid")
            tempFile.copyTo(destinationFile, overwrite = true)

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
                fileSizeBytes = totalBytesRead,
                midiFormatType = parsedMidi.format,
                ticksPerQuarterNote = parsedMidi.ticksPerQuarterNote,
                trackCount = parsedMidi.tracks.size,
                noteCount = totalNotesCount,
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

            val timeSignatureEntities = parsedMidi.timeSignatures.map { ts ->
                SongTimeSignatureEntity(
                    songId = songId,
                    startTick = ts.tick,
                    startMs = ts.startMs,
                    numerator = ts.numerator,
                    denominator = ts.denominator
                )
            }

            // 5. Atomic Room database transaction
            try {
                database.withTransaction {
                    importedSongDao.insertSong(songEntity)
                    songTrackDao.insertTracks(trackEntities)
                    songNoteDao.insertNotes(noteEntities)
                    songTempoDao.insertTempos(tempoEntities)
                    songTimeSignatureDao.insertTimeSignatures(timeSignatureEntities)
                }
            } catch (e: Exception) {
                throw MidiImportPersistenceException("Lỗi lưu trữ dữ liệu bài hát vào database: ${e.message}", e)
            }

            Result.success(songEntity.toDomainModel())
        } catch (e: Exception) {
            targetSongDir?.deleteRecursively()
            Result.failure(e)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    override suspend fun updateTrackConfigurations(songId: String, tracks: List<SongTrackEntity>) = withContext(Dispatchers.IO) {
        database.withTransaction {
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
    }

    override suspend fun renameSong(id: String, newName: String) = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return@withContext
        val song = importedSongDao.getSongById(id) ?: return@withContext
        val updated = song.copy(displayName = trimmed.take(100))
        importedSongDao.updateSong(updated)
    }

    override suspend fun toggleFavorite(id: String) = withContext(Dispatchers.IO) {
        val song = importedSongDao.getSongById(id) ?: return@withContext
        val updated = song.copy(isFavorite = !song.isFavorite)
        importedSongDao.updateSong(updated)
    }

    override suspend fun deleteSong(id: String) = withContext(Dispatchers.IO) {
        database.withTransaction {
            importedSongDao.deleteSongById(id)
        }
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

    override suspend fun getAllSongsList(): List<ImportedSong> = withContext(Dispatchers.IO) {
        importedSongDao.getAllSongsList().map { it.toDomainModel() }
    }

    override fun getPracticePresets(songId: String): Flow<List<SongPracticePreset>> {
        return database.songPracticePresetDao().getPresetsForSong(songId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getAllPresetsList(): List<SongPracticePreset> = withContext(Dispatchers.IO) {
        database.songPracticePresetDao().getAllPresets().map { it.toDomain() }
    }

    override suspend fun savePracticePreset(preset: SongPracticePreset) = withContext(Dispatchers.IO) {
        database.songPracticePresetDao().insertOrUpdate(preset.toEntity())
    }

    override suspend fun deletePracticePreset(id: String) = withContext(Dispatchers.IO) {
        database.songPracticePresetDao().deletePresetById(id)
    }

    override suspend fun seedCurriculumRepertoire(): Int = withContext(Dispatchers.IO) {
        val assetDataSource = com.ian.pianotrainer.data.assets.AssetCurriculumDataSource(context)
        val courses = assetDataSource.getCurriculum().getOrNull() ?: return@withContext 0
        var insertedCount = 0

        database.withTransaction {
            for (course in courses) {
                for (lesson in course.lessons) {
                    val exercise = lesson.exercise ?: continue
                    if (exercise.notes.isEmpty()) continue

                    val songId = "curriculum_${lesson.id}"
                    val existing = importedSongDao.getSongById(songId)
                    if (existing != null) continue

                    val defaultBpm = exercise.defaultBpm.coerceIn(30, 240)
                    val msPerBeat = 60000.0 / defaultBpm
                    var currentStartMs = 0L

                    val noteEntities = mutableListOf<SongNoteEntity>()
                    val rightNotes = mutableListOf<SongNoteEntity>()
                    val leftNotes = mutableListOf<SongNoteEntity>()

                    for (note in exercise.notes) {
                        val durationMs = (note.durationBeats * msPerBeat).toLong().coerceAtLeast(100L)
                        val handStr = note.hand.name
                        val trackIndex = if (note.hand == HandMode.LEFT) 1 else 0

                        val sn = SongNoteEntity(
                            songId = songId,
                            trackIndex = trackIndex,
                            channel = 0,
                            midiNote = note.midiNote,
                            velocity = 80,
                            startTick = (currentStartMs * 480L / msPerBeat).toLong(),
                            endTick = ((currentStartMs + durationMs) * 480L / msPerBeat).toLong(),
                            startMs = currentStartMs,
                            durationMs = durationMs,
                            assignedHand = handStr,
                            chordId = null
                        )
                        noteEntities.add(sn)
                        if (note.hand == HandMode.LEFT) leftNotes.add(sn) else rightNotes.add(sn)
                        currentStartMs += durationMs
                    }

                    val totalDurationMs = currentStartMs
                    val tracks = mutableListOf<SongTrackEntity>()

                    if (rightNotes.isNotEmpty()) {
                        tracks.add(
                            SongTrackEntity(
                                songId = songId,
                                trackIndex = 0,
                                trackName = "Tay phải (Giai điệu)",
                                channelSummary = "Ch 1 (Piano)",
                                instrumentNumber = 0,
                                noteCount = rightNotes.size,
                                minMidiNote = rightNotes.minOf { it.midiNote },
                                maxMidiNote = rightNotes.maxOf { it.midiNote },
                                isSelectedForPractice = true,
                                assignedHand = "RIGHT"
                            )
                        )
                    }

                    if (leftNotes.isNotEmpty()) {
                        tracks.add(
                            SongTrackEntity(
                                songId = songId,
                                trackIndex = 1,
                                trackName = "Tay trái (Hòa âm / Bass)",
                                channelSummary = "Ch 1 (Piano)",
                                instrumentNumber = 0,
                                noteCount = leftNotes.size,
                                minMidiNote = leftNotes.minOf { it.midiNote },
                                maxMidiNote = leftNotes.maxOf { it.midiNote },
                                isSelectedForPractice = true,
                                assignedHand = "LEFT"
                            )
                        )
                    }

                    val songEntity = ImportedSongEntity(
                        id = songId,
                        displayName = lesson.title.replace(Regex("""^\d+\.\s*"""), ""),
                        originalFileName = "${lesson.id}.mid",
                        localFilePath = null,
                        fileHashSha256 = null,
                        fileSizeBytes = null,
                        midiFormatType = 1,
                        ticksPerQuarterNote = 480,
                        trackCount = tracks.size.coerceAtLeast(1),
                        noteCount = noteEntities.size,
                        durationMs = totalDurationMs,
                        defaultBpm = defaultBpm,
                        difficulty = course.difficulty,
                        importedAt = System.currentTimeMillis() - (courses.indexOf(course) * 1000L),
                        lastPracticedAt = null,
                        isFavorite = false,
                        parseStatus = "READY",
                        parseErrorMessage = null
                    )

                    importedSongDao.insertSong(songEntity)
                    songTrackDao.insertTracks(tracks)
                    songNoteDao.insertNotes(noteEntities)
                    songTempoDao.insertTempos(
                        listOf(
                            SongTempoEntity(
                                songId = songId,
                                startTick = 0L,
                                startMs = 0L,
                                microsecondsPerQuarterNote = (60000000L / defaultBpm),
                                bpm = defaultBpm
                            )
                        )
                    )
                    insertedCount++
                }
            }
        }

        // Also seed any bundled MIDI files in assets/starter_songs (e.g. Flower Dance, Amazing Grace...)
        try {
            val starterFiles = context.assets.list("starter_songs") ?: emptyArray()
            for (fileName in starterFiles) {
                if (fileName.endsWith(".mid", ignoreCase = true) || fileName.endsWith(".midi", ignoreCase = true)) {
                    val cleanTitle = fileName.substringBeforeLast(".").replace("_", " ").trim()
                    val existingSongs = importedSongDao.getAllSongsList()
                    if (existingSongs.none { it.displayName.equals(cleanTitle, ignoreCase = true) }) {
                        context.assets.open("starter_songs/$fileName").use { stream ->
                            val result = importMidiFile(
                                inputStream = stream,
                                originalFileName = fileName,
                                fileSize = 1024L,
                                customTitle = cleanTitle
                            )
                            if (result.isSuccess) {
                                insertedCount++
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) { }

        insertedCount
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

class MidiFileTooLargeException(message: String) : Exception(message)

class InvalidMidiFileException(message: String) : Exception(message)

class MidiImportPersistenceException(message: String, cause: Throwable? = null) : Exception(message, cause)
