package com.ian.pianotrainer.core.contentpack

import android.content.Context
import android.util.Log
import com.ian.pianotrainer.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class OnlineSongDownloader(
    private val context: Context,
    private val songRepository: SongRepository,
    private val contentPackImporter: ContentPackImporter
) {
    companion object {
        private const val TAG = "OnlineSongDownloader"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    /**
     * Extracts Sequence ID from URL or numeric ID string.
     * Examples:
     * - "https://onlinesequencer.net/3134103" -> "3134103"
     * - "https://onlinesequencer.net/3813152#t=0" -> "3813152"
     * - "3134103" -> "3134103"
     */
    fun extractSequenceId(input: String): String? {
        val trimmed = input.trim()
        val regex = Regex("""(?:onlinesequencer\.net/)?(?:app/midi/)?(\d+)""")
        val match = regex.find(trimmed)
        return match?.groupValues?.get(1)
    }

    /**
     * Downloads and imports a song from OnlineSequencer ID or standard direct URL.
     */
    suspend fun downloadAndImport(urlOrId: String, customTitle: String? = null): ContentPackImportResult =
        withContext(Dispatchers.IO) {
            try {
                val seqId = extractSequenceId(urlOrId)
                val targetUrl = if (seqId != null) {
                    "https://onlinesequencer.net/app/midi/$seqId"
                } else {
                    urlOrId.trim()
                }

                Log.i(TAG, "Attempting to download song from: $targetUrl")
                val url = URL(targetUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "*/*")
                    instanceFollowRedirects = true
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    return@withContext ContentPackImportResult(
                        isSuccess = false,
                        errorMessage = "Máy chủ phản hồi mã lỗi HTTP $responseCode khi tải $targetUrl"
                    )
                }

                val baos = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    input.copyTo(baos)
                }
                val downloadedBytes = baos.toByteArray()

                if (downloadedBytes.isEmpty()) {
                    return@withContext ContentPackImportResult(
                        isSuccess = false,
                        errorMessage = "Dữ liệu tải về từ liên kết bị rỗng"
                    )
                }

                // Check if file is ZIP / .pianopack (magic bytes 0x50 0x4B)
                val isZip = downloadedBytes.size >= 4 &&
                        downloadedBytes[0] == 0x50.toByte() &&
                        downloadedBytes[1] == 0x4B.toByte()

                if (isZip) {
                    return@withContext contentPackImporter.importPack(
                        ByteArrayInputStream(downloadedBytes),
                        defaultTitle = customTitle ?: "Bài nhạc Online"
                    )
                }

                // Otherwise treat as MIDI file
                val effectiveTitle = customTitle
                    ?: if (seqId != null) "Online Sequencer #$seqId" else "Bài nhạc tải về"

                val importResult = songRepository.importMidiFile(
                    inputStream = ByteArrayInputStream(downloadedBytes),
                    originalFileName = if (seqId != null) "onlinesequencer_$seqId.mid" else "downloaded.mid",
                    fileSize = downloadedBytes.size.toLong(),
                    customTitle = effectiveTitle
                )

                val imported = importResult.getOrNull()
                if (importResult.isSuccess && imported != null) {
                    ContentPackImportResult(
                        isSuccess = true,
                        songId = imported.id,
                        title = imported.displayName,
                        noteCount = imported.noteCount,
                        hasSheetMusic = false,
                        hasAudioTrack = false
                    )
                } else {
                    ContentPackImportResult(
                        isSuccess = false,
                        errorMessage = importResult.exceptionOrNull()?.localizedMessage
                            ?: "Lỗi khi lưu tệp MIDI tải về vào cơ sở dữ liệu"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                ContentPackImportResult(
                    isSuccess = false,
                    errorMessage = "Lỗi kết nối mạng khi tải bài: ${e.localizedMessage}"
                )
            }
        }
}
