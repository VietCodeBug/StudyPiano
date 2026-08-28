package com.ian.pianotrainer.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "song_notes",
    foreignKeys = [
        ForeignKey(
            entity = ImportedSongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("songId"),
        Index(value = ["songId", "startMs"]),
        Index(value = ["songId", "trackIndex"])
    ]
)
data class SongNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val trackIndex: Int,
    val channel: Int,
    val midiNote: Int,
    val velocity: Int,
    val startTick: Long,
    val endTick: Long,
    val startMs: Long,
    val durationMs: Long,
    val assignedHand: String, // LEFT, RIGHT, BOTH, IGNORE
    val chordId: String? = null
)
