package com.ian.pianotrainer.data.repository

import com.ian.pianotrainer.data.local.database.dao.FreePlayRecordingDao
import com.ian.pianotrainer.data.local.database.entity.FreePlayRecordedEventEntity
import com.ian.pianotrainer.data.local.database.entity.FreePlayRecordingEntity
import com.ian.pianotrainer.domain.model.FreePlayRecording
import com.ian.pianotrainer.domain.model.RecordedMidiEvent
import com.ian.pianotrainer.domain.repository.FreePlayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RealFreePlayRepository(
    private val dao: FreePlayRecordingDao
) : FreePlayRepository {

    override fun getAllRecordings(): Flow<List<FreePlayRecording>> {
        return dao.getAllRecordings().map { list ->
            list.map { entity ->
                FreePlayRecording(
                    id = entity.id,
                    title = entity.title,
                    createdAt = entity.createdAt,
                    durationMs = entity.durationMs,
                    noteCount = entity.noteCount,
                    hasAudio = entity.hasAudio,
                    audioFilePath = entity.audioFilePath,
                    midiFilePath = entity.midiFilePath
                )
            }
        }
    }

    override suspend fun getRecordingById(id: String): FreePlayRecording? {
        val entity = dao.getRecordingById(id) ?: return null
        val events = dao.getEventsForRecording(id).map { eventEntity ->
            RecordedMidiEvent(
                timestampMs = eventEntity.timestampMs,
                isNoteOn = eventEntity.eventType == "NOTE_ON",
                note = eventEntity.midiNote,
                velocity = eventEntity.velocity,
                channel = eventEntity.channel
            )
        }
        return FreePlayRecording(
            id = entity.id,
            title = entity.title,
            createdAt = entity.createdAt,
            durationMs = entity.durationMs,
            noteCount = entity.noteCount,
            hasAudio = entity.hasAudio,
            audioFilePath = entity.audioFilePath,
            midiFilePath = entity.midiFilePath,
            events = events
        )
    }

    override suspend fun saveRecording(
        recording: FreePlayRecording,
        events: List<RecordedMidiEvent>
    ) {
        dao.insertRecording(
            FreePlayRecordingEntity(
                id = recording.id,
                title = recording.title,
                createdAt = recording.createdAt,
                durationMs = recording.durationMs,
                noteCount = recording.noteCount,
                hasAudio = recording.hasAudio,
                audioFilePath = recording.audioFilePath,
                midiFilePath = recording.midiFilePath
            )
        )
        val eventEntities = events.map { ev ->
            FreePlayRecordedEventEntity(
                recordingId = recording.id,
                timestampMs = ev.timestampMs,
                eventType = if (ev.isNoteOn) "NOTE_ON" else "NOTE_OFF",
                midiNote = ev.note,
                velocity = ev.velocity,
                channel = ev.channel
            )
        }
        dao.insertEvents(eventEntities)
    }

    override suspend fun deleteRecording(id: String) {
        dao.deleteRecording(id)
    }

    override suspend fun renameRecording(id: String, newTitle: String) {
        dao.updateTitle(id, newTitle)
    }
}
