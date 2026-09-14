package com.ian.pianotrainer.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.ian.pianotrainer.data.local.database.PianoTrainerDatabase
import com.ian.pianotrainer.data.local.database.entity.FreePlayRecordedEventEntity
import com.ian.pianotrainer.data.local.database.entity.FreePlayRecordingEntity
import com.ian.pianotrainer.data.local.database.entity.ImportedSongEntity
import com.ian.pianotrainer.data.local.database.entity.LessonProgressEntity
import com.ian.pianotrainer.data.local.database.entity.PracticeNoteResultEntity
import com.ian.pianotrainer.data.local.database.entity.PracticeSessionEntity
import com.ian.pianotrainer.data.local.database.entity.SongNoteEntity
import com.ian.pianotrainer.data.local.database.entity.SongPracticePresetEntity
import com.ian.pianotrainer.data.local.database.entity.SongTempoEntity
import com.ian.pianotrainer.data.local.database.entity.SongTimeSignatureEntity
import com.ian.pianotrainer.data.local.database.entity.SongTrackEntity
import com.ian.pianotrainer.data.local.database.entity.SongAssetEntity
import com.ian.pianotrainer.data.local.database.entity.RestoreCommitEntity
import com.ian.pianotrainer.domain.repository.BackupManifest
import com.ian.pianotrainer.domain.repository.BackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class RestoreCheckpoint {
    FILES_PREPARED,
    AFTER_PRESERVE_SONGS_RENAME, AFTER_PRESERVE_RECORDINGS_RENAME,
    AFTER_PROMOTE_SONGS_RENAME, AFTER_PROMOTE_RECORDINGS_RENAME,
    FILES_SWAPPED, BEFORE_DB_TRANSACTION, AFTER_DB_COMMIT
}
fun interface RestoreFaultInjector {
    fun onCheckpoint(checkpoint: RestoreCheckpoint)
    companion object { val NONE = RestoreFaultInjector { } }
}

class BackupRepositoryImpl(
    private val context: Context,
    private val database: PianoTrainerDatabase,
    private val faultInjector: RestoreFaultInjector = RestoreFaultInjector.NONE
) : BackupRepository {

    companion object {
        const val MAX_ZIP_ENTRIES = 5000
        const val MAX_DECOMPRESSED_BYTES = 500L * 1024L * 1024L // 500 MB
    }

    override suspend fun createBackupZip(
        outputStream: OutputStream,
        includeAudio: Boolean
    ): Result<BackupManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val songs = database.importedSongDao().getAllSongsList()
            val songAssets = songs.flatMap { database.songAssetDao().getAssetsForSong(it.id) }
            val sessions = database.practiceSessionDao().getAllSessionsList()
            val recordings = database.freePlayRecordingDao().getAllRecordingsList()

            val manifest = BackupManifest(
                version = 2,
                appVersion = "2.1.0",
                createdAt = System.currentTimeMillis(),
                songCount = songs.size,
                sessionCount = sessions.size,
                recordingCount = recordings.size,
                includeAudio = includeAudio
            )

            ZipOutputStream(outputStream).use { zos ->
                // 1. Write manifest.json
                val manifestJson = JSONObject().apply {
                    put("version", manifest.version)
                    put("appVersion", manifest.appVersion)
                    put("createdAt", manifest.createdAt)
                    put("songCount", manifest.songCount)
                    put("sessionCount", manifest.sessionCount)
                    put("recordingCount", manifest.recordingCount)
                    put("includeAudio", manifest.includeAudio)
                }
                writeZipTextEntry(zos, "manifest.json", manifestJson.toString(2))

                // 2. Export Database Tables as JSON
                // Songs
                val songsArr = JSONArray()
                for (s in songs) {
                    val obj = JSONObject().apply {
                        put("id", s.id)
                        put("displayName", s.displayName)
                        put("originalFileName", s.originalFileName)
                        put("localFilePath", s.localFilePath ?: "")
                        put("localRelativePath", s.localFilePath?.let { relativeSongPath(File(it)) } ?: "")
                        put("fileSizeBytes", s.fileSizeBytes ?: 0L)
                        put("fileHashSha256", s.fileHashSha256 ?: "")
                        put("durationMs", s.durationMs ?: 0L)
                        put("noteCount", s.noteCount)
                        put("defaultBpm", s.defaultBpm)
                        put("difficulty", s.difficulty)
                        put("isFavorite", s.isFavorite)
                        put("importedAt", s.importedAt)
                        put("lastPracticedAt", s.lastPracticedAt ?: 0L)
                        put("midiFormatType", s.midiFormatType)
                        put("ticksPerQuarterNote", s.ticksPerQuarterNote)
                        put("trackCount", s.trackCount)
                        put("parseStatus", s.parseStatus)
                    }
                    songsArr.put(obj)
                }
                writeZipTextEntry(zos, "data/imported_songs.json", songsArr.toString())

                val assetsArr = JSONArray()
                songAssets.forEach { asset ->
                    assetsArr.put(JSONObject().apply {
                        put("id", asset.id); put("songId", asset.songId); put("type", asset.type)
                        put("originalFileName", asset.originalFileName)
                        put("localRelativePath", relativeSongPath(File(asset.localFilePath)) ?: "")
                        put("fileSizeBytes", asset.fileSizeBytes); put("mimeType", asset.mimeType ?: "")
                    })
                }
                writeZipTextEntry(zos, "data/song_assets.json", assetsArr.toString())

                // Presets
                val presets = database.songPracticePresetDao().getAllPresets()
                val presetsArr = JSONArray()
                for (p in presets) {
                    val obj = JSONObject().apply {
                        put("id", p.id)
                        put("songId", p.songId)
                        put("name", p.name)
                        put("loopStartMs", p.loopStartMs ?: -1L)
                        put("loopEndMs", p.loopEndMs ?: -1L)
                        put("handMode", p.handMode)
                        put("practiceMode", p.practiceMode)
                        put("targetBpm", p.targetBpm)
                        put("speedMultiplier", p.speedMultiplier.toDouble())
                        put("lookAhead", p.lookAhead)
                        put("noteDisplaySize", p.noteDisplaySize)
                        put("createdAt", p.createdAt)
                        put("updatedAt", p.updatedAt)
                    }
                    presetsArr.put(obj)
                }
                writeZipTextEntry(zos, "data/practice_presets.json", presetsArr.toString())

                // Sessions
                val sessionsArr = JSONArray()
                for (s in sessions) {
                    val obj = JSONObject().apply {
                        put("id", s.id)
                        put("sourceType", s.sourceType)
                        put("sourceId", s.sourceId ?: "")
                        put("practiceMode", s.practiceMode)
                        put("handMode", s.handMode)
                        put("displayMode", s.displayMode)
                        put("bpm", s.bpm)
                        put("startedAt", s.startedAt)
                        put("durationMs", s.durationMs)
                        put("totalExpectedNotes", s.totalExpectedNotes)
                        put("correctNotes", s.correctNotes)
                        put("wrongNotes", s.wrongNotes)
                        put("missedNotes", s.missedNotes)
                        put("earlyNotes", s.earlyNotes)
                        put("lateNotes", s.lateNotes)
                        put("accuracy", s.accuracy.toDouble())
                        put("sourceTitleSnapshot", s.sourceTitleSnapshot ?: "")
                        put("score", s.score)
                        put("maxStreak", s.maxStreak)
                        put("inputSource", s.inputSource)
                        put("effectiveSpeed", s.effectiveSpeed.toDouble())
                    }
                    sessionsArr.put(obj)
                }
                writeZipTextEntry(zos, "data/practice_sessions.json", sessionsArr.toString())

                // Free Play Recordings
                val recArr = JSONArray()
                for (r in recordings) {
                    val obj = JSONObject().apply {
                        put("id", r.id)
                        put("title", r.title)
                        put("createdAt", r.createdAt)
                        put("durationMs", r.durationMs)
                        put("noteCount", r.noteCount)
                        put("hasAudio", r.hasAudio)
                        put("inputSource", r.inputSource)
                        put("bpm", r.bpm)
                        put("fileStatus", r.fileStatus)
                    }
                    recArr.put(obj)
                }
                writeZipTextEntry(zos, "data/freeplay_recordings.json", recArr.toString())

                // 3. Write physical song files
                val songsDir = File(context.filesDir, "songs")
                if (songsDir.exists()) {
                    songsDir.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relPath = "files/songs/" + file.relativeTo(songsDir).path.replace('\\', '/')
                        writeZipFileEntry(zos, relPath, file)
                    }
                }

                // 4. Write physical recordings files
                val recDir = File(context.filesDir, "recordings")
                if (recDir.exists()) {
                    recDir.walkTopDown().filter { it.isFile }.forEach { file ->
                        if (file.name.endsWith(".m4a") && !includeAudio) {
                            // Skip audio if not requested
                        } else {
                            val relPath = "files/recordings/" + file.relativeTo(recDir).path.replace('\\', '/')
                            writeZipFileEntry(zos, relPath, file)
                        }
                    }
                }
            }

            manifest
        }
    }

    override suspend fun restoreBackupZip(inputStream: InputStream): Result<BackupManifest> = withContext(Dispatchers.IO) {
        val tempRestoreDir = File(context.cacheDir, "temp_restore_${UUID.randomUUID()}")
        tempRestoreDir.mkdirs()

        try {
            var entryCount = 0
            var totalBytes = 0L

            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    entryCount++
                    if (entryCount > MAX_ZIP_ENTRIES) {
                        throw IOException("Backup zip contains too many entries (exceeds $MAX_ZIP_ENTRIES limit)")
                    }

                    val name = entry.name
                    // ZipSlip security check
                    if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                        throw SecurityException("Malicious zip entry path rejected: $name")
                    }

                    val targetFile = File(tempRestoreDir, name)
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).use { fos ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (zis.read(buffer).also { read = it } != -1) {
                                totalBytes += read
                                if (totalBytes > MAX_DECOMPRESSED_BYTES) {
                                    throw IOException("Backup zip decompressed size exceeds maximum safety limit (500MB)")
                                }
                                fos.write(buffer, 0, read)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Read and validate manifest
            val manifestFile = File(tempRestoreDir, "manifest.json")
            if (!manifestFile.exists()) {
                throw IOException("Invalid backup zip: manifest.json not found")
            }

            val manifestJson = JSONObject(manifestFile.readText(Charsets.UTF_8))
            val manifest = BackupManifest(
                version = manifestJson.optInt("version", 1),
                appVersion = manifestJson.optString("appVersion", "2.1.0"),
                createdAt = manifestJson.optLong("createdAt", System.currentTimeMillis()),
                songCount = manifestJson.optInt("songCount", 0),
                sessionCount = manifestJson.optInt("sessionCount", 0),
                recordingCount = manifestJson.optInt("recordingCount", 0),
                includeAudio = manifestJson.optBoolean("includeAudio", true)
            )
            validateRestorePackage(tempRestoreDir, manifest)

            SONG_FILE_OPERATION_MUTEX.withLock {
                recoverPendingRestores()
                val operationId = UUID.randomUUID().toString()
                val operationDir = prepareRestoreOperation(tempRestoreDir, operationId)
                val recovery = RestoreOperationRecovery(context, database)
                try {
                    swapRestoreFiles(operationDir, recovery)
                    faultInjector.onCheckpoint(RestoreCheckpoint.FILES_SWAPPED)
                    faultInjector.onCheckpoint(RestoreCheckpoint.BEFORE_DB_TRANSACTION)

                    // Atomic Room database restoration
                    database.withTransaction {
                // Clear old user data first
                database.practiceNoteResultDao().clearAll()
                database.practiceSessionDao().clearAll()
                database.songPracticePresetDao().clearAll()
                database.freePlayRecordingDao().clearAll()
                database.importedSongDao().clearAll()

                // Restore Songs
                val songsFile = File(tempRestoreDir, "data/imported_songs.json")
                if (songsFile.exists()) {
                    val arr = JSONArray(songsFile.readText(Charsets.UTF_8))
                    val songEntities = mutableListOf<ImportedSongEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        songEntities.add(
                            ImportedSongEntity(
                                id = obj.getString("id"),
                                displayName = obj.getString("displayName"),
                                originalFileName = obj.optString("originalFileName", obj.optString("fileName", "song.mid")),
                                localFilePath = restoredSongPath(obj, obj.getString("id")),
                                fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                                fileHashSha256 = obj.optString("fileHashSha256").takeIf { it.isNotBlank() },
                                durationMs = if (obj.has("durationMs")) obj.getLong("durationMs") else null,
                                noteCount = obj.optInt("noteCount", 0),
                                defaultBpm = obj.optInt("defaultBpm", obj.optInt("bpm", 120)),
                                difficulty = obj.optString("difficulty", "MEDIUM"),
                                isFavorite = obj.optBoolean("isFavorite", false),
                                importedAt = obj.getLong("importedAt"),
                                lastPracticedAt = if (obj.optLong("lastPracticedAt", 0L) > 0) obj.getLong("lastPracticedAt") else null,
                                midiFormatType = obj.optInt("midiFormatType", 1),
                                ticksPerQuarterNote = obj.optInt("ticksPerQuarterNote", 480),
                                trackCount = obj.optInt("trackCount", 1),
                                parseStatus = obj.optString("parseStatus", "READY")
                            )
                        )
                    }
                    database.importedSongDao().insertSongs(songEntities)

                    val assetsFile = File(tempRestoreDir, "data/song_assets.json")
                    val assetEntities = if (assetsFile.exists()) {
                        val assets = JSONArray(assetsFile.readText(Charsets.UTF_8))
                        buildList {
                            for (j in 0 until assets.length()) {
                                val a = assets.getJSONObject(j)
                                val relative = safeRelativeSongPath(a.optString("localRelativePath"))
                                    ?: throw IOException("Invalid SongAsset relative path in backup")
                                add(SongAssetEntity(a.getString("id"), a.getString("songId"), a.getString("type"),
                                    a.getString("originalFileName"), File(context.filesDir, "songs/$relative").absolutePath,
                                    a.optLong("fileSizeBytes"), a.optString("mimeType").takeIf { it.isNotBlank() }))
                            }
                        }
                    } else {
                        // Version-1 compatibility: only the standardized source.mid link is unambiguous.
                        songEntities.mapNotNull { song ->
                            val midi = File(context.filesDir, "songs/${song.id}/source.mid").takeIf { it.isFile } ?: return@mapNotNull null
                            SongAssetEntity("${song.id}_midi", song.id, "MIDI", song.originalFileName,
                                midi.absolutePath, midi.length(), "audio/midi")
                        }
                    }
                    if (assetEntities.isNotEmpty()) database.songAssetDao().insertAssets(assetEntities)
                }

                // Restore Presets
                val presetsFile = File(tempRestoreDir, "data/practice_presets.json")
                if (presetsFile.exists()) {
                    val arr = JSONArray(presetsFile.readText(Charsets.UTF_8))
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val startMs = obj.optLong("loopStartMs", -1L)
                        val endMs = obj.optLong("loopEndMs", -1L)
                        database.songPracticePresetDao().insertOrUpdate(
                            SongPracticePresetEntity(
                                id = obj.getString("id"),
                                songId = obj.getString("songId"),
                                name = obj.getString("name"),
                                loopStartMs = if (startMs >= 0) startMs else null,
                                loopEndMs = if (endMs >= 0) endMs else null,
                                handMode = obj.optString("handMode", "BOTH"),
                                practiceMode = obj.optString("practiceMode", "WAIT_FOR_NOTE"),
                                targetBpm = obj.optInt("targetBpm", 120),
                                speedMultiplier = obj.optDouble("speedMultiplier", 1.0).toFloat(),
                                lookAhead = obj.optInt("lookAhead", 4000),
                                noteDisplaySize = obj.optString("noteDisplaySize", "MEDIUM"),
                                createdAt = obj.getLong("createdAt"),
                                updatedAt = obj.getLong("updatedAt")
                            )
                        )
                    }
                }

                // Restore Sessions
                val sessionsFile = File(tempRestoreDir, "data/practice_sessions.json")
                if (sessionsFile.exists()) {
                    val arr = JSONArray(sessionsFile.readText(Charsets.UTF_8))
                    val sessionEntities = mutableListOf<PracticeSessionEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        sessionEntities.add(
                            PracticeSessionEntity(
                                id = obj.getString("id"),
                                sourceType = obj.getString("sourceType"),
                                sourceId = if (obj.has("sourceId")) obj.getString("sourceId") else null,
                                practiceMode = obj.getString("practiceMode"),
                                handMode = obj.getString("handMode"),
                                displayMode = obj.getString("displayMode"),
                                bpm = obj.getInt("bpm"),
                                startedAt = obj.getLong("startedAt"),
                                durationMs = obj.getLong("durationMs"),
                                totalExpectedNotes = obj.getInt("totalExpectedNotes"),
                                correctNotes = obj.getInt("correctNotes"),
                                wrongNotes = obj.getInt("wrongNotes"),
                                missedNotes = obj.getInt("missedNotes"),
                                earlyNotes = obj.getInt("earlyNotes"),
                                lateNotes = obj.getInt("lateNotes"),
                                accuracy = obj.getDouble("accuracy").toFloat(),
                                sourceTitleSnapshot = if (obj.has("sourceTitleSnapshot")) obj.getString("sourceTitleSnapshot") else null,
                                score = obj.optInt("score", 0),
                                maxStreak = obj.optInt("maxStreak", 0),
                                inputSource = obj.optString("inputSource", "VIRTUAL_KEYBOARD"),
                                effectiveSpeed = obj.optDouble("effectiveSpeed", 1.0).toFloat()
                            )
                        )
                    }
                    database.practiceSessionDao().insertSessions(sessionEntities)
                }

                // Restore Free Play Recordings
                val recFile = File(tempRestoreDir, "data/freeplay_recordings.json")
                if (recFile.exists()) {
                    val arr = JSONArray(recFile.readText(Charsets.UTF_8))
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.getString("id")
                        val midiFile = File(context.filesDir, "recordings/$id/performance.mid")
                        val audioFile = File(context.filesDir, "recordings/$id/audio.m4a")

                        database.freePlayRecordingDao().insertRecording(
                            FreePlayRecordingEntity(
                                id = id,
                                title = obj.getString("title"),
                                createdAt = obj.getLong("createdAt"),
                                durationMs = obj.getLong("durationMs"),
                                noteCount = obj.getInt("noteCount"),
                                hasAudio = obj.optBoolean("hasAudio", false) && audioFile.exists(),
                                audioFilePath = if (audioFile.exists()) audioFile.absolutePath else null,
                                midiFilePath = if (midiFile.exists()) midiFile.absolutePath else null,
                                inputSource = obj.optString("inputSource", "VIRTUAL_KEYBOARD"),
                                bpm = obj.optInt("bpm", 80),
                                fileStatus = "READY"
                            )
                        )
                    }
                }
                        database.restoreCommitDao().insert(RestoreCommitEntity(operationId, System.currentTimeMillis()))
                    }
                    faultInjector.onCheckpoint(RestoreCheckpoint.AFTER_DB_COMMIT)
                    recovery.reconcile(operationDir)
                } catch (error: Throwable) {
                    withContext(NonCancellable) { recovery.reconcile(operationDir) }
                    throw error
                }
            }

            Result.success(manifest)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempRestoreDir.deleteRecursively()
        }
    }

    private fun writeZipTextEntry(zos: ZipOutputStream, entryName: String, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun validateRestorePackage(root: File, manifest: BackupManifest): Map<String, String?> {
        require(manifest.version in 1..2) { "Unsupported backup version ${manifest.version}" }
        val songsFile = File(root, "data/imported_songs.json")
        if (!songsFile.isFile) throw IOException("Backup is missing data/imported_songs.json")
        val songs = JSONArray(songsFile.readText(Charsets.UTF_8))
        if (songs.length() != manifest.songCount) throw IOException("Backup songCount does not match imported_songs metadata")
        val ids = mutableSetOf<String>()
        val expected = linkedMapOf<String, String?>()
        for (i in 0 until songs.length()) {
            val song = songs.getJSONObject(i)
            val id = song.getString("id").takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,80}")) }
                ?: throw IOException("Invalid song id in backup")
            if (!ids.add(id)) throw IOException("Duplicate song id '$id' in backup")
            song.getString("displayName"); song.getLong("importedAt")
            expected[id] = song.optString("fileHashSha256").takeIf { it.isNotBlank() }
        }
        listOf("practice_presets.json", "practice_sessions.json", "freeplay_recordings.json").forEach { name ->
            File(root, "data/$name").takeIf { it.exists() }?.let { JSONArray(it.readText(Charsets.UTF_8)) }
        }

        val stagedSongs = File(root, "files/songs")
        if (manifest.version >= 2) {
            val assetsFile = File(root, "data/song_assets.json")
            if (!assetsFile.isFile) throw IOException("Backup v2 is missing data/song_assets.json")
            val assets = JSONArray(assetsFile.readText(Charsets.UTF_8))
            val typesBySong = mutableMapOf<String, MutableSet<String>>()
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val songId = asset.getString("songId")
                if (songId !in ids) throw IOException("SongAsset references unknown song '$songId'")
                val relative = safeRelativeSongPath(asset.getString("localRelativePath"))
                    ?: throw IOException("Invalid SongAsset relative path")
                if (!relative.startsWith("$songId/")) throw IOException("SongAsset path is outside its song folder: '$relative'")
                val type = asset.getString("type")
                if (!typesBySong.getOrPut(songId) { mutableSetOf() }.add(type)) throw IOException("Duplicate SongAsset type '$type' for '$songId'")
                val staged = File(stagedSongs, relative).canonicalFile
                if (!staged.isFile || !staged.path.startsWith(stagedSongs.canonicalPath + File.separator)) {
                    throw IOException("Backup v2 is missing declared SongAsset file '$relative'")
                }
                if (staged.length() != asset.getLong("fileSizeBytes")) throw IOException("SongAsset size mismatch for '$relative'")
            }
            ids.forEach { id -> if (typesBySong[id]?.contains("MIDI") != true) throw IOException("Backup v2 song '$id' has no MIDI asset metadata") }
        } else {
            for (i in 0 until songs.length()) {
                val song = songs.getJSONObject(i); val id = song.getString("id")
                if (song.optString("localFilePath").isNotBlank() && !File(stagedSongs, "$id/source.mid").isFile) {
                    throw IOException("Backup v1 is missing MIDI file for '$id'")
                }
            }
        }
        expected.forEach { (id, hash) ->
            if (hash != null) {
                val midi = if (manifest.version >= 2) {
                    val assets = JSONArray(File(root, "data/song_assets.json").readText(Charsets.UTF_8))
                    (0 until assets.length()).asSequence().map { assets.getJSONObject(it) }
                        .first { it.getString("songId") == id && it.getString("type") == "MIDI" }
                        .getString("localRelativePath").let { File(stagedSongs, it) }
                } else File(stagedSongs, "$id/source.mid")
                if (sha256(midi) != hash.lowercase()) throw IOException("MIDI hash mismatch for '$id'")
            }
        }
        return expected
    }

    private fun prepareRestoreOperation(staging: File, operationId: String): File {
        val op = File(context.filesDir, "song_operations/restore_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val newSongs = File(op, "newSongs").apply { mkdirs() }
            val newRecordings = File(op, "newRecordings").apply { mkdirs() }
            if (File(staging, "files/songs").exists() && !File(staging, "files/songs").copyRecursively(newSongs, overwrite = false)) {
                throw IOException("Failed to stage restored song files")
            }
            if (File(staging, "files/recordings").exists() && !File(staging, "files/recordings").copyRecursively(newRecordings, overwrite = false)) {
                throw IOException("Failed to stage restored recording files")
            }
            RestoreOperationRecovery(context, database).create(
                op, operationId,
                hadSongs = File(context.filesDir, "songs").exists(),
                oldSongsDigest = RestoreOperationRecovery.treeDigest(File(context.filesDir, "songs")),
                newSongsDigest = RestoreOperationRecovery.treeDigest(newSongs),
                hadRecordings = File(context.filesDir, "recordings").exists(),
                oldRecordingsDigest = RestoreOperationRecovery.treeDigest(File(context.filesDir, "recordings")),
                newRecordingsDigest = RestoreOperationRecovery.treeDigest(newRecordings)
            )
            faultInjector.onCheckpoint(RestoreCheckpoint.FILES_PREPARED)
            return op
        } catch (error: Throwable) {
            op.deleteRecursively()
            throw error
        }
    }

    private fun swapRestoreFiles(op: File, recovery: RestoreOperationRecovery) {
        val songs = File(context.filesDir, "songs")
        val recordings = File(context.filesDir, "recordings")
        recovery.writeState(op, RestoreJournalState.PRESERVE_SONGS_INTENT)
        if (songs.exists() && !songs.renameTo(File(op, "oldSongs"))) throw IOException("Failed to preserve current song files")
        faultInjector.onCheckpoint(RestoreCheckpoint.AFTER_PRESERVE_SONGS_RENAME)
        recovery.writeState(op, RestoreJournalState.PRESERVE_SONGS_DONE)

        recovery.writeState(op, RestoreJournalState.PRESERVE_RECORDINGS_INTENT)
        if (recordings.exists() && !recordings.renameTo(File(op, "oldRecordings"))) throw IOException("Failed to preserve current recording files")
        faultInjector.onCheckpoint(RestoreCheckpoint.AFTER_PRESERVE_RECORDINGS_RENAME)
        recovery.writeState(op, RestoreJournalState.PRESERVE_RECORDINGS_DONE)

        recovery.writeState(op, RestoreJournalState.PROMOTE_SONGS_INTENT)
        if (!File(op, "newSongs").renameTo(songs)) throw IOException("Failed to promote restored song files")
        faultInjector.onCheckpoint(RestoreCheckpoint.AFTER_PROMOTE_SONGS_RENAME)
        recovery.writeState(op, RestoreJournalState.PROMOTE_SONGS_DONE)

        recovery.writeState(op, RestoreJournalState.PROMOTE_RECORDINGS_INTENT)
        if (!File(op, "newRecordings").renameTo(recordings)) throw IOException("Failed to promote restored recording files")
        faultInjector.onCheckpoint(RestoreCheckpoint.AFTER_PROMOTE_RECORDINGS_RENAME)
        recovery.writeState(op, RestoreJournalState.PROMOTE_RECORDINGS_DONE)
        recovery.writeState(op, RestoreJournalState.FILES_SWAPPED)
    }

    private suspend fun recoverPendingRestores() {
        File(context.filesDir, "song_operations").listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("restore_") }
            .forEach { RestoreOperationRecovery(context, database).reconcile(it) }
    }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun relativeSongPath(file: File): String? {
        val root = File(context.filesDir, "songs").canonicalFile
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return null
        return candidate.takeIf { it.path.startsWith(root.path + File.separator) }
            ?.relativeTo(root)?.path?.replace('\\', '/')
    }

    private fun safeRelativeSongPath(raw: String): String? {
        val clean = raw.replace('\\', '/').trim('/')
        return clean.takeIf { it.isNotBlank() && it.split('/').none { part -> part.isBlank() || part == ".." } && !Regex("^[A-Za-z]:").containsMatchIn(raw) }
    }

    private fun restoredSongPath(obj: JSONObject, songId: String): String? {
        val relative = safeRelativeSongPath(obj.optString("localRelativePath"))
            ?: "${songId}/source.mid".takeIf { File(context.filesDir, "songs/$it").isFile }
        return relative?.let { File(context.filesDir, "songs/$it").absolutePath }
    }

    private fun writeZipFileEntry(zos: ZipOutputStream, entryName: String, file: File) {
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { input ->
            input.copyTo(zos)
        }
        zos.closeEntry()
    }
}
