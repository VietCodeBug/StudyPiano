package com.ian.pianotrainer.core.contentpack

import android.content.Context
import android.util.Log
import android.util.Xml
import com.ian.pianotrainer.domain.model.PendingSongAsset
import com.ian.pianotrainer.domain.model.SongAssetType
import com.ian.pianotrainer.domain.repository.SongRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.FilterInputStream
import java.util.UUID
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

class ContentPackImporter(
    private val context: Context,
    private val songRepository: SongRepository
) {
    companion object {
        private const val TAG = "ContentPackImporter"
        const val MAX_PACK_BYTES = 25L * 1024L * 1024L
        const val MAX_EXTRACTED_BYTES = 60L * 1024L * 1024L
        const val MAX_ENTRY_BYTES = 25L * 1024L * 1024L
        const val MAX_ENTRIES = 128
        private const val STALE_STAGE_MS = 24L * 60L * 60L * 1000L
    }

    private val manifestAdapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter(ContentPackManifest::class.java)

    suspend fun importPack(
        inputStream: InputStream,
        defaultTitle: String = "Bài nhạc mới"
    ): ContentPackImportResult = withContext(Dispatchers.IO) {
        val stagingBase = File(context.cacheDir, "song_import_staging").apply { mkdirs() }
        cleanupStaleStaging(stagingBase)
        val staging = File(stagingBase, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val entries = extractZip(inputStream, staging)
            if (entries.isEmpty()) return@withContext failure("Gói ZIP/PianoPack không chứa tệp nào.")

            val manifestFile = entries["manifest.json"]
            val manifest = manifestFile?.let { file ->
                try {
                    manifestAdapter.fromJson(file.readText(Charsets.UTF_8))
                        ?: throw PackValidationException("manifest.json không có dữ liệu hợp lệ.")
                } catch (error: PackValidationException) {
                    throw error
                } catch (error: Exception) {
                    throw PackValidationException("manifest.json không hợp lệ: ${error.message ?: "sai cấu trúc JSON"}")
                }
            }
            if (manifest != null && manifest.schemaVersion != 1) {
                throw PackValidationException("Không hỗ trợ schemaVersion ${manifest.schemaVersion} trong manifest.")
            }

            val midi = if (manifest != null) {
                requireEntry(entries, manifest.midiFileName, "MIDI")
            } else {
                entries.entries.firstOrNull { it.key.hasExtension("mid", "midi") }?.toPair()
                    ?: throw PackValidationException("Không tìm thấy tệp MIDI (.mid/.midi); đợt này gói nhập bắt buộc phải có MIDI.")
            }
            if (!midi.second.hasMidiHeader()) {
                throw PackValidationException("Tệp '${midi.first}' không có header MIDI MThd hợp lệ.")
            }

            val pendingAssets = mutableListOf<PendingSongAsset>()
            resolveMusicXml(entries, manifest)?.let { (name, file) ->
                pendingAssets += PendingSongAsset(
                    type = SongAssetType.MUSICXML,
                    originalFileName = name,
                    stagedFilePath = file.absolutePath,
                    fileSizeBytes = file.length(),
                    mimeType = "application/vnd.recordare.musicxml+xml"
                )
            }
            resolveAudio(entries, manifest)?.let { (name, file) ->
                pendingAssets += PendingSongAsset(
                    type = SongAssetType.REFERENCE_AUDIO,
                    originalFileName = name,
                    stagedFilePath = file.absolutePath,
                    fileSizeBytes = file.length(),
                    mimeType = audioMime(name)
                )
            }
            val extractedTotal = staging.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            if (extractedTotal > MAX_EXTRACTED_BYTES) {
                throw PackValidationException("Tổng dữ liệu sau giải nén (kể cả MXL) vượt giới hạn 60 MB.")
            }

            val title = manifest?.title?.trim()?.takeIf { it.isNotEmpty() } ?: defaultTitle
            val importResult = FileInputStream(midi.second).use { stream ->
                songRepository.importSongPackage(
                    inputStream = stream,
                    originalFileName = midi.first.substringAfterLast('/'),
                    fileSize = midi.second.length(),
                    customTitle = title,
                    additionalAssets = pendingAssets
                )
            }
            val song = importResult.getOrElse { throw it }
            ContentPackImportResult(
                isSuccess = true,
                songId = song.id,
                title = song.displayName,
                noteCount = song.noteCount,
                hasSheetMusic = song.assets.any { it.type == SongAssetType.MUSICXML },
                hasAudioTrack = song.assets.any { it.type == SongAssetType.REFERENCE_AUDIO }
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: PackValidationException) {
            failure(error.message ?: "Gói bài hát không hợp lệ.")
        } catch (error: ZipException) {
            failure("Tệp không phải ZIP/PianoPack hợp lệ: ${error.message ?: "dữ liệu nén bị hỏng"}")
        } catch (error: Exception) {
            Log.e(TAG, "Content pack import failed", error)
            failure(error.localizedMessage ?: "Không thể nhập gói bài hát.")
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun extractZip(input: InputStream, root: File): Map<String, File> {
        val result = linkedMapOf<String, File>()
        val seen = mutableSetOf<String>()
        var total = 0L
        var count = 0
        ZipInputStream(LimitedInputStream(input.buffered(), MAX_PACK_BYTES)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val normalized = normalizeEntry(entry.name)
                if (!seen.add(normalized.lowercase())) throw PackValidationException("Gói có đường dẫn trùng: '$normalized'.")
                if (++count > MAX_ENTRIES) throw PackValidationException("Gói có quá $MAX_ENTRIES mục.")
                val target = safeTarget(root, normalized)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    var entryBytes = 0L
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            entryBytes += read
                            total += read
                            if (entryBytes > MAX_ENTRY_BYTES) throw PackValidationException("Tệp '$normalized' vượt giới hạn 25 MB.")
                            if (total > MAX_EXTRACTED_BYTES) throw PackValidationException("Tổng dữ liệu giải nén vượt giới hạn 60 MB.")
                            output.write(buffer, 0, read)
                        }
                    }
                    result[normalized] = target
                }
                zip.closeEntry()
            }
        }
        return result
    }

    private fun resolveMusicXml(
        entries: Map<String, File>,
        manifest: ContentPackManifest?
    ): Pair<String, File>? {
        val declared = manifest?.musicXmlFileName
        if (declared != null) {
            val entry = requireEntry(entries, declared, "MusicXML")
            return validateOrExpandMusicXml(entry, required = true)
        }
        entries.entries.firstOrNull { it.key.hasExtension("musicxml", "xml") && it.value.isMusicXml() }
            ?.let { return it.toPair() }
        entries.entries.firstOrNull { it.key.hasExtension("mxl") }?.let {
            return validateOrExpandMusicXml(it.toPair(), required = false)
        }
        return null
    }

    private fun validateOrExpandMusicXml(entry: Pair<String, File>, required: Boolean): Pair<String, File>? {
        if (entry.first.hasExtension("mxl")) {
            return try { extractMxl(entry.second) } catch (error: Exception) {
                if (required) throw PackValidationException("MXL '${entry.first}' không hợp lệ: ${error.message}") else null
            }
        }
        val validation = MusicXmlValidator.validate(entry.second)
        if (validation.isFailure) {
            if (required) throw PackValidationException("Tệp '${entry.first}' không phải MusicXML hoàn chỉnh, hợp lệ: ${validation.exceptionOrNull()?.message}")
            return null
        }
        return entry
    }

    private fun extractMxl(mxl: File): Pair<String, File> {
        val root = File(mxl.parentFile, "mxl_${UUID.randomUUID()}").apply { mkdirs() }
        val nested = FileInputStream(mxl).use { extractZip(it, root) }
        val rootPath = nested["META-INF/container.xml"]?.let { parseMxlRootPath(it) }
            ?: throw PackValidationException("MXL thiếu META-INF/container.xml hoặc rootfile.")
        val score = nested[normalizeEntry(rootPath)]
            ?: throw PackValidationException("MXL tham chiếu tệp không tồn tại: '$rootPath'.")
        MusicXmlValidator.validate(score).getOrElse { throw PackValidationException("Rootfile trong MXL không phải MusicXML hoàn chỉnh, hợp lệ: ${it.message}") }
        return rootPath.substringAfterLast('/') to score
    }

    private fun parseMxlRootPath(container: File): String? {
        FileInputStream(container).use { input ->
            val parser = Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true); setInput(input, "UTF-8") }
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                    return parser.getAttributeValue(null, "full-path")
                }
                parser.next()
            }
        }
        return null
    }

    private fun File.isMusicXml(): Boolean = MusicXmlValidator.validate(this).isSuccess

    private fun resolveAudio(entries: Map<String, File>, manifest: ContentPackManifest?): Pair<String, File>? {
        val declared = manifest?.audioFileName
        if (declared != null) {
            val entry = requireEntry(entries, declared, "audio tham chiếu")
            if (entry.second.detectAudioFormat() == null) throw PackValidationException("Không nhận diện được header audio '${entry.first}' (WAV/MP3/OGG/M4A), hoặc tệp quá ngắn/bị hỏng. Kiểm tra header không xác minh tệp chắc chắn phát được.")
            return entry
        }
        return entries.entries.firstOrNull { it.key.hasExtension("mp3", "m4a", "ogg", "wav") && it.value.detectAudioFormat() != null }?.toPair()
    }

    private fun File.detectAudioFormat(): DetectedAudioFormat? = inputStream().use { input ->
        val header = ByteArray(16)
        val count = input.read(header)
        AudioHeaderSniffer.detect(if (count > 0) header.copyOf(count) else ByteArray(0))
    }

    private fun requireEntry(entries: Map<String, File>, rawPath: String, label: String): Pair<String, File> {
        val path = normalizeEntry(rawPath)
        val file = entries[path] ?: throw PackValidationException("Manifest tham chiếu $label không tồn tại: '$rawPath'.")
        return path to file
    }

    private fun normalizeEntry(raw: String): String {
        val value = raw.replace('\\', '/').trim('/')
        if (value.isBlank() || raw.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(raw) ||
            value.split('/').any { it == ".." || it.isBlank() }) {
            throw PackValidationException("Đường dẫn không an toàn trong gói: '$raw'.")
        }
        return value
    }

    private fun safeTarget(root: File, relative: String): File {
        val target = File(root, relative).canonicalFile
        val prefix = root.canonicalPath + File.separator
        if (!target.path.startsWith(prefix)) throw PackValidationException("Đường dẫn ZIP vượt ra ngoài vùng tạm.")
        return target
    }

    private fun File.hasMidiHeader(): Boolean = length() >= 14 && inputStream().use {
        val header = ByteArray(4); it.read(header) == 4 && header.contentEquals(byteArrayOf(0x4D, 0x54, 0x68, 0x64))
    }

    private fun String.hasExtension(vararg extensions: String): Boolean =
        extensions.any { endsWith(".$it", ignoreCase = true) }

    private fun audioMime(name: String): String = when {
        name.endsWith(".mp3", true) -> "audio/mpeg"
        name.endsWith(".ogg", true) -> "audio/ogg"
        name.endsWith(".wav", true) -> "audio/wav"
        else -> "audio/mp4"
    }

    private fun cleanupStaleStaging(base: File) {
        val cutoff = System.currentTimeMillis() - STALE_STAGE_MS
        base.listFiles()?.filter { it.isDirectory && it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    }

    private fun failure(message: String) = ContentPackImportResult(isSuccess = false, errorMessage = message)
    private class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) add(1) }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) add(it.toLong()) }
        private fun add(read: Long) {
            count += read
            if (count > limit) throw PackValidationException("Gói nén vượt giới hạn 25 MB.")
        }
    }
    private class PackValidationException(message: String) : Exception(message)
}
