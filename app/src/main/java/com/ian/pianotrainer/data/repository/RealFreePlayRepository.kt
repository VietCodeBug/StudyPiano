package com.ian.pianotrainer.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.ian.pianotrainer.core.music.midi.MidiRecordingWriter
import com.ian.pianotrainer.data.local.database.PianoTrainerDatabase
import com.ian.pianotrainer.data.local.database.dao.FreePlayRecordingDao
import com.ian.pianotrainer.data.local.database.entity.toDomain
import com.ian.pianotrainer.data.local.database.entity.toEntity
import com.ian.pianotrainer.domain.model.FreePlayRecording
import com.ian.pianotrainer.domain.model.RecordedMidiEvent
import com.ian.pianotrainer.domain.repository.FreePlayRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream

class RealFreePlayRepository(
    private val context: Context,
    private val database: PianoTrainerDatabase,
    private val dao: FreePlayRecordingDao = database.freePlayRecordingDao(),
    private val midiWriter: MidiRecordingWriter = MidiRecordingWriter()
) : FreePlayRepository {

    override fun getAllRecordings(): Flow<List<FreePlayRecording>> {
        return dao.getAllRecordings().map { list ->
            list.map { entity ->
                // Check if physical files exist to determine fileStatus
                val recDir = getRecordingDirectory(entity.id)
                val midiExists = entity.midiFilePath != null && File(entity.midiFilePath).exists()
                val audioExists = entity.audioFilePath != null && File(entity.audioFilePath).exists()
                val status = if (entity.midiFilePath != null && !midiExists) "FILE_MISSING" else entity.fileStatus

                entity.toDomain().copy(
                    fileStatus = status,
                    hasAudio = entity.hasAudio && audioExists
                )
            }
        }
    }

    override suspend fun getAllRecordingsList(): List<FreePlayRecording> = withContext(Dispatchers.IO) {
        dao.getAllRecordingsList().map { entity ->
            val events = dao.getEventsForRecording(entity.id).map { it.toDomain() }
            entity.toDomain(events)
        }
    }

    override suspend fun getRecordingById(id: String): FreePlayRecording? = withContext(Dispatchers.IO) {
        val entity = dao.getRecordingById(id) ?: return@withContext null
        val events = dao.getEventsForRecording(id).map { it.toDomain() }
        entity.toDomain(events)
    }

    override suspend fun saveRecording(
        recording: FreePlayRecording,
        events: List<RecordedMidiEvent>
    ): String = withContext(Dispatchers.IO) {
        val recDir = getRecordingDirectory(recording.id)
        recDir.mkdirs()

        // 1. Write MIDI file Format 0
        val midiFile = File(recDir, "performance.mid")
        midiWriter.write(
            events = events,
            bpm = recording.bpm,
            trackName = recording.title,
            targetFile = midiFile
        )

        // 2. Promote audio file from pending if present
        var finalAudioPath: String? = null
        var hasValidAudio = false
        if (recording.audioFilePath != null) {
            val pendingAudioFile = File(recording.audioFilePath)
            if (pendingAudioFile.exists() && pendingAudioFile.length() > 1024) {
                val targetAudioFile = File(recDir, "audio.m4a")
                if (pendingAudioFile.canonicalPath != targetAudioFile.canonicalPath) {
                    pendingAudioFile.copyTo(targetAudioFile, overwrite = true)
                    pendingAudioFile.delete()
                }
                finalAudioPath = targetAudioFile.absolutePath
                hasValidAudio = true
            }
        }

        val updatedRecording = recording.copy(
            midiFilePath = midiFile.absolutePath,
            audioFilePath = finalAudioPath,
            hasAudio = hasValidAudio,
            fileStatus = "READY"
        )

        // 3. Persist atomically in Room
        try {
            database.withTransaction {
                dao.insertRecording(updatedRecording.toEntity())
                val eventEntities = events.map { it.toEntity(recording.id) }
                dao.insertEvents(eventEntities)
            }
        } catch (e: Exception) {
            // Clean up files on DB transaction error
            recDir.deleteRecursively()
            throw e
        }

        updatedRecording.id
    }

    override suspend fun deleteRecording(id: String): Unit = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.deleteRecording(id)
        }
        val recDir = getRecordingDirectory(id)
        if (recDir.exists()) {
            recDir.deleteRecursively()
        }
    }

    override suspend fun renameRecording(id: String, newTitle: String): Unit = withContext(Dispatchers.IO) {
        val sanitized = newTitle.trim().take(100)
        if (sanitized.isNotBlank()) {
            dao.updateTitle(id, sanitized)
        }
    }

    override suspend fun exportMidi(recordingId: String, outputStream: OutputStream): Unit = withContext(Dispatchers.IO) {
        val recording = getRecordingById(recordingId)
            ?: throw IOException("Recording not found: $recordingId")

        val midiPath = recording.midiFilePath
        if (midiPath != null && File(midiPath).exists()) {
            FileInputStream(File(midiPath)).use { input ->
                input.copyTo(outputStream)
            }
        } else if (recording.events.isNotEmpty()) {
            midiWriter.write(
                events = recording.events,
                bpm = recording.bpm,
                trackName = recording.title,
                outputStream = outputStream
            )
        } else {
            throw IOException("No MIDI data available to export for recording: $recordingId")
        }
    }

    override suspend fun exportAudio(recordingId: String, outputStream: OutputStream): Unit = withContext(Dispatchers.IO) {
        val recording = dao.getRecordingById(recordingId)
            ?: throw IOException("Recording not found: $recordingId")

        val audioPath = recording.audioFilePath
        if (audioPath != null && File(audioPath).exists()) {
            FileInputStream(File(audioPath)).use { input ->
                input.copyTo(outputStream)
            }
        } else {
            throw IOException("No audio recording found for: $recordingId")
        }
    }

    override fun getRecordingDirectory(recordingId: String): File {
        return File(File(context.filesDir, "recordings"), recordingId)
    }
}
