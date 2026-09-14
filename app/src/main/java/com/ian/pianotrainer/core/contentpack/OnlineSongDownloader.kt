package com.ian.pianotrainer.core.contentpack

import android.content.Context
import android.util.Log
import com.ian.pianotrainer.domain.repository.SongRepository
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class OnlineSongDownloader(
    private val context: Context,
    private val songRepository: SongRepository,
    private val contentPackImporter: ContentPackImporter
) {
    companion object {
        private const val TAG = "OnlineSongDownloader"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
        private const val MAX_DOWNLOAD_BYTES = 25 * 1024 * 1024
        private const val MAX_REDIRECTS = 5
    }

    /** Returns an OnlineSequencer ID only for an ID or a real OnlineSequencer URL. */
    fun extractSequenceId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.matches(Regex("^\\d+$"))) return trimmed

        val uri = runCatching {
            URI(if (trimmed.contains("://")) trimmed else "https://$trimmed")
        }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        if (host != "onlinesequencer.net") return null

        val parts = uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
        return when {
            parts.size == 1 && parts[0].matches(Regex("^\\d+$")) -> parts[0]
            parts.size >= 3 && parts[0] == "app" && parts[1] == "midi" &&
                parts[2].matches(Regex("^\\d+$")) -> parts[2]
            else -> null
        }
    }

    /** Downloads a direct MIDI or PianoPack URL and imports it into the local library. */
    suspend fun downloadAndImport(
        urlOrId: String,
        customTitle: String? = null
    ): ContentPackImportResult = withContext(Dispatchers.IO) {
        val input = urlOrId.trim()
        if (input.isBlank()) return@withContext failure("Hãy nhập một liên kết tải trực tiếp.")

        val sequenceId = extractSequenceId(input)
        if (sequenceId != null) {
            return@withContext failure(
                "OnlineSequencer không còn cung cấp tệp MIDI trực tiếp bằng mã ID. " +
                    "Hãy mở bài trên OnlineSequencer, chọn Export MIDI, rồi nhập tệp đã tải vào ứng dụng."
            )
        }

        val normalizedUrl = normalizeDirectUrl(input)
            ?: return@withContext failure("Liên kết không hợp lệ. Chỉ hỗ trợ URL http/https tới tệp MIDI hoặc PianoPack.")

        val tempFile = File(context.cacheDir, "song_download_${java.util.UUID.randomUUID()}.tmp")
        try {
            val response = download(normalizedUrl, tempFile)
            if (response.file.length() == 0L) return@withContext failure("Tệp tải về không có dữ liệu.")

            val isZip = response.file.hasPrefix(0x50, 0x4B, 0x03, 0x04)
            val isMidi = response.file.hasPrefix(0x4D, 0x54, 0x68, 0x64) // MThd
            if (!isZip && !isMidi) {
                return@withContext failure(
                    "Liên kết không trả về tệp MIDI/PianoPack. Nếu đây là trang web, hãy tìm nút tải MIDI trực tiếp."
                )
            }

            val inferredFileName = response.fileName ?: fileNameFromUrl(response.finalUrl)
            val inferredTitle = customTitle?.trim()?.takeIf { it.isNotBlank() }
                ?: inferredFileName.substringBeforeLast('.').ifBlank { "Bài nhạc online" }

            if (isZip) {
                return@withContext contentPackImporter.importPack(
                    inputStream = FileInputStream(response.file),
                    defaultTitle = inferredTitle
                )
            }

            val importResult = FileInputStream(response.file).use { stream ->
                songRepository.importMidiFile(
                    inputStream = stream,
                    originalFileName = inferredFileName.ensureMidiExtension(),
                    fileSize = response.file.length(),
                    customTitle = inferredTitle
                )
            }
            val imported = importResult.getOrNull()
            if (imported != null) {
                ContentPackImportResult(
                    isSuccess = true,
                    songId = imported.id,
                    title = imported.displayName,
                    noteCount = imported.noteCount,
                    hasSheetMusic = false,
                    hasAudioTrack = false
                )
            } else {
                failure(importResult.exceptionOrNull()?.localizedMessage ?: "Không thể lưu tệp MIDI đã tải.")
            }
        } catch (error: DownloadException) {
            failure(error.message ?: "Không thể tải bài nhạc.")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Download error", error)
            failure("Lỗi kết nối khi tải bài: ${error.localizedMessage ?: "không rõ nguyên nhân"}")
        } finally {
            tempFile.delete()
        }
    }

    internal fun normalizeDirectUrl(input: String): String? {
        val parsed = runCatching { URI(input.trim()) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (parsed.host.isNullOrBlank()) return null

        if (parsed.host.equals("github.com", ignoreCase = true)) {
            val parts = parsed.path.orEmpty().trim('/').split('/')
            if (parts.size >= 5 && parts[2] == "blob") {
                val rawPath = parts.drop(3).joinToString("/")
                return "https://raw.githubusercontent.com/${parts[0]}/${parts[1]}/$rawPath"
            }
        }
        return parsed.toASCIIString()
    }

    private fun download(initialUrl: String, target: File): DownloadResponse {
        var currentUrl = initialUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "audio/midi,audio/x-midi,application/zip,application/octet-stream;q=0.9,*/*;q=0.2")
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw DownloadException("Máy chủ chuyển hướng nhưng không cung cấp địa chỉ mới.")
                    if (redirectCount >= MAX_REDIRECTS) throw DownloadException("Liên kết chuyển hướng quá nhiều lần.")
                    currentUrl = URL(URL(currentUrl), location).toString()
                    return@repeat
                }
                if (code !in 200..299) throw DownloadException("Máy chủ trả về lỗi HTTP $code.")

                val declaredSize = connection.contentLengthLong
                if (declaredSize > MAX_DOWNLOAD_BYTES) {
                    throw DownloadException("Tệp lớn hơn giới hạn 25 MB.")
                }

                FileOutputStream(target).use { output ->
                    connection.inputStream.use { inputStream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = inputStream.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_DOWNLOAD_BYTES) throw DownloadException("Tệp lớn hơn giới hạn 25 MB.")
                            output.write(buffer, 0, read)
                        }
                    }
                }
                return DownloadResponse(
                    file = target,
                    finalUrl = currentUrl,
                    fileName = parseContentDispositionFileName(connection.getHeaderField("Content-Disposition"))
                )
            } finally {
                connection.disconnect()
            }
        }
        throw DownloadException("Không thể theo dõi liên kết tải.")
    }

    private fun parseContentDispositionFileName(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val encoded = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.getOrNull(1)
        if (!encoded.isNullOrBlank()) {
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8.name()).safeFileName()
        }
        return Regex("filename=\\\"?([^\\\";]+)", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.getOrNull(1)?.safeFileName()
    }

    private fun fileNameFromUrl(url: String): String {
        val pathName = runCatching { URI(url).path.substringAfterLast('/') }.getOrDefault("")
        return URLDecoder.decode(pathName, StandardCharsets.UTF_8.name())
            .safeFileName()
            .ifBlank { "downloaded.mid" }
    }

    private fun String.safeFileName(): String = substringAfterLast('/').substringAfterLast('\\').trim()
    private fun String.ensureMidiExtension(): String =
        if (lowercase().endsWith(".mid") || lowercase().endsWith(".midi")) this else "$this.mid"

    private fun File.hasPrefix(vararg expected: Int): Boolean = inputStream().use { input ->
        val bytes = ByteArray(expected.size)
        input.read(bytes) == expected.size && expected.indices.all { bytes[it].toInt() and 0xFF == expected[it] }
    }

    private fun failure(message: String) = ContentPackImportResult(isSuccess = false, errorMessage = message)

    private data class DownloadResponse(
        val file: File,
        val finalUrl: String,
        val fileName: String?
    )

    private class DownloadException(message: String) : Exception(message)
}
