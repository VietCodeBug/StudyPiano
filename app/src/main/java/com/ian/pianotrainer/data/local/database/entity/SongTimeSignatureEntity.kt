package com.ian.pianotrainer.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ian.pianotrainer.domain.model.SongTimeSignature

@Entity(
    tableName = "song_time_signatures",
    foreignKeys = [
        ForeignKey(
            entity = ImportedSongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["songId"])
    ]
)
data class SongTimeSignatureEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: String,
    val startTick: Long,
    val startMs: Long,
    val numerator: Int,
    val denominator: Int
)

fun SongTimeSignatureEntity.toDomainModel() = SongTimeSignature(
    startTick = startTick,
    startMs = startMs,
    numerator = numerator,
    denominator = denominator
)
