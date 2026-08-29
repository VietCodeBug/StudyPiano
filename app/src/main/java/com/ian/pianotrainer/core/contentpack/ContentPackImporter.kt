package com.ian.pianotrainer.core.contentpack

import android.content.Context
import android.util.Log
import com.ian.pianotrainer.core.music.midi.MidiFileParser
import com.ian.pianotrainer.domain.repository.SongRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class ContentPackImporter(
    private val context: Context,
    private val songRepository: SongRepository
) {
    companion object {
        private const val TAG = "ContentPackImporter"
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val manifestAdapter = moshi.adapter(ContentPackManifest::class.java)

    /**
     * Imports a .pianopack ZIP bundle or multi-file stream into local storage and database.
     */
    suspend fun importPack(inputStream: InputStream, defaultTitle: String = "Bài nhạc mới"): ContentPackImportResult =
        withContext(Dispatchers.IO) {
            try {
                val filesMap = mutableMapOf<String, ByteArray>()
                val zip = ZipInputStream(inputStream)
                var entry = zip.nextEntry

                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.substringAfterLast("/")
                        val baos = ByteArrayOutputStream()
                        zip.copyTo(baos)
                        filesMap[name] = baos.toByteArray()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }

                if (filesMap.isEmpty()) {
                    return@withContext ContentPackImportResult(
                        isSuccess = false,
                        errorMessage = "Gói tệp rỗng hoặc không đúng định dạng ZIP / .pianopack"
                    )
                }

                // 1. Find manifest if present
                val manifestBytes = filesMap["manifest.json"]
                val manifest: ContentPackManifest? = if (manifestBytes != null) {
                    runCatching { manifestAdapter.fromJson(manifestBytes.decodeToString()) }.getOrNull()
                } else null

                // 2. Find MIDI file
                val midiEntryName = manifest?.midiFileName
                    ?: filesMap.keys.firstOrNull { it.endsWith(".mid", ignoreCase = true) || it.endsWith(".midi", ignoreCase = true) }

                val midiBytes = midiEntryName?.let { filesMap[it] }
                if (midiBytes == null) {
                    return@withContext ContentPackImportResult(
                        isSuccess = false,
                        errorMessage = "Không tìm thấy tệp MIDI (.mid) trong gói"
                    )
                }

                // 3. Find MusicXML and Audio files
                val xmlEntryName = manifest?.musicXmlFileName
                    ?: filesMap.keys.firstOrNull { it.endsWith(".musicxml", ignoreCase = true) || it.endsWith(".xml", ignoreCase = true) }
                val xmlBytes = xmlEntryName?.let { filesMap[it] }

                val audioEntryName = manifest?.audioFileName
                    ?: filesMap.keys.firstOrNull {
                        it.endsWith(".mp3", ignoreCase = true) ||
                        it.endsWith(".m4a", ignoreCase = true) ||
                        it.endsWith(".ogg", ignoreCase = true)
                    }
                val audioBytes = audioEntryName?.let { filesMap[it] }

                // 4. Parse MIDI
                val songId = manifest?.id?.ifBlank { UUID.randomUUID().toString() } ?: UUID.randomUUID().toString()
                val parsedMidi = MidiFileParser.parse(ByteArrayInputStream(midiBytes))

                // 5. Store files in app internal storage
                val songDir = File(context.filesDir, "songs/$songId").apply { mkdirs() }
                val localMidiFile = File(songDir, "song.mid").apply {
                    FileOutputStream(this).use { it.write(midiBytes) }
                }

                val localXmlFile = xmlBytes?.let {
                    File(songDir, "sheet.musicxml").apply {
                        FileOutputStream(this).use { fos -> fos.write(it) }
                    }
                }

                val localAudioFile = audioBytes?.let {
                    val ext = audioEntryName?.substringAfterLast(".", "mp3") ?: "mp3"
                    File(songDir, "audio.$ext").apply {
                        FileOutputStream(this).use { fos -> fos.write(it) }
                    }
                }

                val title = manifest?.title?.ifBlank { defaultTitle } ?: defaultTitle

                // 6. Save into SongRepository
                val importResult = songRepository.importMidiFile(
                    inputStream = ByteArrayInputStream(midiBytes),
                    originalFileName = midiEntryName ?: "song.mid",
                    fileSize = midiBytes.size.toLong(),
                    customTitle = title
                )

                val importedSong = importResult.getOrNull()
                if (importResult.isSuccess && importedSong != null) {
                    val totalNotes = importedSong.noteCount
                    Log.i(TAG, "Successfully imported content pack: $title ($totalNotes notes)")
                    ContentPackImportResult(
                        isSuccess = true,
                        songId = importedSong.id,
                        title = title,
                        noteCount = totalNotes,
                        hasSheetMusic = localXmlFile != null,
                        hasAudioTrack = localAudioFile != null
                    )
                } else {
                    ContentPackImportResult(
                        isSuccess = false,
                        errorMessage = importResult.exceptionOrNull()?.localizedMessage ?: "Lỗi khi lưu vào cơ sở dữ liệu"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import content pack", e)
                ContentPackImportResult(
                    isSuccess = false,
                    errorMessage = "Lỗi giải nén: ${e.localizedMessage}"
                )
            }
        }
}
