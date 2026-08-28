package com.ian.pianotrainer.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "song_tempos",
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
data class SongTempoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val startTick: Long,
    val startMs: Long,
    val microsecondsPerQuarterNote: Long,
    val bpm: Int
)
