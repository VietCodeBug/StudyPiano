package com.ian.pianotrainer.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ian.pianotrainer.domain.model.SongAsset
import com.ian.pianotrainer.domain.model.SongAssetType

@Entity(
    tableName = "song_assets",
    foreignKeys = [ForeignKey(
        entity = ImportedSongEntity::class,
        parentColumns = ["id"],
        childColumns = ["songId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("songId"), Index(value = ["songId", "type"], unique = true)]
)
data class SongAssetEntity(
    @PrimaryKey val id: String,
    val songId: String,
    val type: String,
    val originalFileName: String,
    val localFilePath: String,
    val fileSizeBytes: Long,
    val mimeType: String? = null
)

fun SongAssetEntity.toDomainModel() = SongAsset(
    id = id,
    songId = songId,
    type = SongAssetType.valueOf(type),
    originalFileName = originalFileName,
    localFilePath = localFilePath,
    fileSizeBytes = fileSizeBytes,
    mimeType = mimeType
)
