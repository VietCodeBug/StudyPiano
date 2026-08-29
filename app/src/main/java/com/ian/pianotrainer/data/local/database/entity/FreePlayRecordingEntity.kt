package com.ian.pianotrainer.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ian.pianotrainer.domain.model.FreePlayRecording
import com.ian.pianotrainer.domain.model.RecordedMidiEvent

@Entity(tableName = "freeplay_recordings")
data class FreePlayRecordingEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val createdAt: Long,
    val durationMs: Long,
    val noteCount: Int,
    val hasAudio: Boolean = false,
    val audioFilePath: String? = null,
    val midiFilePath: String? = null,
    val inputSource: String = "VIRTUAL_KEYBOARD",
    val bpm: Int = 80,
    val fileStatus: String = "READY" // READY, FILE_MISSING, CORRUPTED
)

@Entity(
    tableName = "freeplay_recorded_events",
    foreignKeys = [
        ForeignKey(
            entity = FreePlayRecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["recordingId"])
    ]
)
data class FreePlayRecordedEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordingId: String,
    val timestampMs: Long,
    val eventType: String, // "NOTE_ON", "NOTE_OFF", "CONTROL_CHANGE"
    val midiNote: Int,
    val velocity: Int,
    val channel: Int = 0,
    val controlNumber: Int = 0,
    val controlValue: Int = 0
)

fun FreePlayRecordingEntity.toDomain(events: List<RecordedMidiEvent> = emptyList()): FreePlayRecording {
    return FreePlayRecording(
        id = id,
        title = title,
        createdAt = createdAt,
        durationMs = durationMs,
        noteCount = noteCount,
        hasAudio = hasAudio,
        audioFilePath = audioFilePath,
        midiFilePath = midiFilePath,
        inputSource = inputSource,
        bpm = bpm,
        fileStatus = fileStatus,
        events = events
    )
}

fun FreePlayRecording.toEntity(): FreePlayRecordingEntity {
    return FreePlayRecordingEntity(
        id = id,
        title = title,
        createdAt = createdAt,
        durationMs = durationMs,
        noteCount = noteCount,
        hasAudio = hasAudio,
        audioFilePath = audioFilePath,
        midiFilePath = midiFilePath,
        inputSource = inputSource,
        bpm = bpm,
        fileStatus = fileStatus
    )
}

fun FreePlayRecordedEventEntity.toDomain(): RecordedMidiEvent {
    return RecordedMidiEvent(
        timestampMs = timestampMs,
        isNoteOn = eventType == "NOTE_ON",
        note = midiNote,
        velocity = velocity,
        channel = channel,
        isControlChange = eventType == "CONTROL_CHANGE",
        controlNumber = controlNumber,
        controlValue = controlValue
    )
}

fun RecordedMidiEvent.toEntity(recordingId: String): FreePlayRecordedEventEntity {
    val type = when {
        isControlChange -> "CONTROL_CHANGE"
        isNoteOn -> "NOTE_ON"
        else -> "NOTE_OFF"
    }
    return FreePlayRecordedEventEntity(
        recordingId = recordingId,
        timestampMs = timestampMs,
        eventType = type,
        midiNote = note,
        velocity = velocity,
        channel = channel,
        controlNumber = controlNumber,
        controlValue = controlValue
    )
}
