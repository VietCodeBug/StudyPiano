package com.ian.pianotrainer.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "song_tracks",
    primaryKeys = ["songId", "trackIndex"],
    foreignKeys = [
        ForeignKey(
            entity = ImportedSongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("songId")]
)
data class SongTrackEntity(
    val songId: String,
    val trackIndex: Int,
    val trackName: String,
    val channelSummary: String,
    val instrumentNumber: Int? = null,
    val noteCount: Int,
    val minMidiNote: Int,
    val maxMidiNote: Int,
    val isSelectedForPractice: Boolean = true,
    val assignedHand: String = "BOTH" // LEFT, RIGHT, BOTH, IGNORE
)
