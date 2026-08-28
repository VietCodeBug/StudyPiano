package com.ian.pianotrainer.domain.repository

import com.ian.pianotrainer.domain.model.FreePlayRecording
import com.ian.pianotrainer.domain.model.RecordedMidiEvent
import kotlinx.coroutines.flow.Flow

interface FreePlayRepository {
    fun getAllRecordings(): Flow<List<FreePlayRecording>>
    suspend fun getRecordingById(id: String): FreePlayRecording?
    suspend fun saveRecording(recording: FreePlayRecording, events: List<RecordedMidiEvent>)
    suspend fun deleteRecording(id: String)
    suspend fun renameRecording(id: String, newTitle: String)
}
