package com.ian.pianotrainer.domain.model

enum class SongAssetType { MIDI, MUSICXML, REFERENCE_AUDIO }

data class SongAsset(
    val id: String,
    val songId: String,
    val type: SongAssetType,
    val originalFileName: String,
    val localFilePath: String,
    val fileSizeBytes: Long,
    val mimeType: String? = null
)

/** A validated file staged by an importer and consumed synchronously by the repository. */
data class PendingSongAsset(
    val type: SongAssetType,
    val originalFileName: String,
    val stagedFilePath: String,
    val fileSizeBytes: Long,
    val mimeType: String? = null
)
