package com.ian.pianotrainer.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val midiFilePath: String? = null
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
    val eventType: String, // "NOTE_ON" or "NOTE_OFF"
    val midiNote: Int,
    val velocity: Int,
    val channel: Int = 0
)
