package com.ian.pianotrainer.domain.repository

import com.ian.pianotrainer.domain.model.FreePlayRecording
import com.ian.pianotrainer.domain.model.RecordedMidiEvent
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.OutputStream

interface FreePlayRepository {
    fun getAllRecordings(): Flow<List<FreePlayRecording>>
    suspend fun getAllRecordingsList(): List<FreePlayRecording>
    suspend fun getRecordingById(id: String): FreePlayRecording?
    suspend fun saveRecording(recording: FreePlayRecording, events: List<RecordedMidiEvent>): String
    suspend fun deleteRecording(id: String)
    suspend fun renameRecording(id: String, newTitle: String)
    suspend fun exportMidi(recordingId: String, outputStream: OutputStream)
    suspend fun exportAudio(recordingId: String, outputStream: OutputStream)
    fun getRecordingDirectory(recordingId: String): File
}
