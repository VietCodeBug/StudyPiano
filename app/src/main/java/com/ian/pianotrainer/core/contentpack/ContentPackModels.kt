package com.ian.pianotrainer.core.contentpack

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ContentPackManifest(
    val schemaVersion: Int = 1,
    val id: String,
    val title: String,
    val composer: String? = null,
    val arranger: String? = null,
    val difficulty: String = "Cơ bản",
    val defaultBpm: Int = 80,
    val midiFileName: String = "song.mid",
    val musicXmlFileName: String? = null,
    val audioFileName: String? = null,
    val tags: List<String> = emptyList()
)

data class ContentPackImportResult(
    val isSuccess: Boolean,
    val songId: String? = null,
    val title: String? = null,
    val noteCount: Int = 0,
    val hasSheetMusic: Boolean = false,
    val hasAudioTrack: Boolean = false,
    val errorMessage: String? = null
)
