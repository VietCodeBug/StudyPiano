package com.ian.pianotrainer.domain.repository

import java.io.InputStream
import java.io.OutputStream

data class BackupManifest(
    val version: Int = 1,
    val appVersion: String = "2.1.0",
    val createdAt: Long = System.currentTimeMillis(),
    val songCount: Int = 0,
    val sessionCount: Int = 0,
    val recordingCount: Int = 0,
    val includeAudio: Boolean = true
)

interface BackupRepository {
    suspend fun createBackupZip(outputStream: OutputStream, includeAudio: Boolean = true): Result<BackupManifest>
    suspend fun restoreBackupZip(inputStream: InputStream): Result<BackupManifest>
}
