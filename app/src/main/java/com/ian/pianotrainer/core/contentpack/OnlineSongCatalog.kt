package com.ian.pianotrainer.core.contentpack

import android.content.Context
import com.ian.pianotrainer.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CatalogSongItem(
    val id: String,
    val title: String,
    val composer: String,
    val genre: String,
    val difficulty: String, // "Cơ bản", "Trung bình", "Nâng cao"
    val durationText: String,
    val noteCountText: String,
    val downloadUrl: String,
    val assetFallback: String? = null,
    val description: String
)

object OnlineSongCatalog {
    val curatedSongs = listOf(
        CatalogSongItem(
            id = "curated_flower_dance",
            title = "Flower Dance",
            composer = "DJ Okawari",
            genre = "Hiện đại / Anime",
            difficulty = "Nâng cao",
            durationText = "3:45",
            noteCountText = "~1,400 nốt",
            downloadUrl = "https://raw.githubusercontent.com/VietCodeBug/StudyPiano/main/MID/%ED%94%8C%EB%9D%BC%EC%9B%8C%20%EB%8C%84%EC%8A%A4Flower%20Dance%20-%20DJ%20Okawari%20%ED%94%BC%EC%95%84%EB%85%B8.mid.mid",
            assetFallback = "starter_songs/flower_dance.mid",
            description = "Tác phẩm piano hiện đại thịnh hành bậc nhất với nhịp điệu bay bổng và hợp âm cuốn hút."
        ),
        CatalogSongItem(
            id = "curated_ode_to_joy",
            title = "Ode to Joy (Khải hoàn ca)",
            composer = "Ludwig van Beethoven",
            genre = "Cổ điển",
            difficulty = "Cơ bản",
            durationText = "1:30",
            noteCountText = "480 nốt",
            downloadUrl = "https://raw.githubusercontent.com/VietCodeBug/StudyPiano/main/MID/Ode%20to%20Joy%20(Piano).mid",
            assetFallback = "starter_songs/ode_to_joy.mid",
            description = "Giai điệu bất hủ trong Giao hưởng số 9, dễ học với phím tay đôi cân bằng cho người mới."
        ),
        CatalogSongItem(
            id = "curated_amazing_grace",
            title = "Amazing Grace (Ân điển diệu kỳ)",
            composer = "John Newton",
            genre = "Thánh ca / Ballad",
            difficulty = "Cơ bản",
            durationText = "2:10",
            noteCountText = "320 nốt",
            downloadUrl = "https://raw.githubusercontent.com/VietCodeBug/StudyPiano/main/MID/Amazing%20Grace.mid",
            assetFallback = "starter_songs/amazing_grace.mid",
            description = "Bản thánh ca êm dịu, ấm áp với giai điệu mượt mà và kỹ thuật ngón tay nhẹ nhàng."
        ),
        CatalogSongItem(
            id = "curated_jingle_bells",
            title = "Jingle Bells (Tiếng chuông ngân)",
            composer = "James Lord Pierpont",
            genre = "Lễ hội / Pop",
            difficulty = "Cơ bản",
            durationText = "1:45",
            noteCountText = "290 nốt",
            downloadUrl = "https://raw.githubusercontent.com/VietCodeBug/StudyPiano/main/MID/Jingle%20Bells%20(MIDI%20RECORDED).mid",
            assetFallback = "starter_songs/jingle_bells.mid",
            description = "Giai điệu vui tươi, rộn rã rất thích hợp để luyện nhịp staccato và cảm thụ tiết tấu."
        ),
        CatalogSongItem(
            id = "curated_twinkle_star",
            title = "Twinkle Twinkle Little Star",
            composer = "W. A. Mozart (Biến tấu)",
            genre = "Thiếu nhi / Cổ điển",
            difficulty = "Cơ bản",
            durationText = "1:15",
            noteCountText = "180 nốt",
            downloadUrl = "https://raw.githubusercontent.com/VietCodeBug/StudyPiano/main/MID/Twinkle%20Twinkle%20Little%20Star%20(MIDI%20Version).mid",
            assetFallback = "starter_songs/twinkle_twinkle_little_star.mid",
            description = "Bản nhạc mở đầu hoàn hảo cho việc định vị bàn phím và phản xạ đọc nốt."
        ),
        CatalogSongItem(
            id = "curated_fur_elise",
            title = "Für Elise (Thư gửi Elise)",
            composer = "Ludwig van Beethoven",
            genre = "Cổ điển",
            difficulty = "Trung bình",
            durationText = "2:50",
            noteCountText = "~620 nốt",
            downloadUrl = "https://raw.githubusercontent.com/VietCodeBug/StudyPiano/main/MID/fur_elise.mid",
            assetFallback = "starter_songs/ode_to_joy.mid", // Fallback if offline
            description = "Kiệt tác lãng mạn kinh điển của Beethoven với chủ đề nửa cung E-D# vang danh toàn cầu."
        ),
        CatalogSongItem(
            id = "curated_canon_in_d",
            title = "Canon in D (Khúc luân vũ Re trưởng)",
            composer = "Johann Pachelbel",
            genre = "Cổ điển / Baroque",
            difficulty = "Trung bình",
            durationText = "3:20",
            noteCountText = "~780 nốt",
            downloadUrl = "https://raw.githubusercontent.com/VietCodeBug/StudyPiano/main/MID/canon_in_d.mid",
            assetFallback = "starter_songs/amazing_grace.mid",
            description = "Chuỗi hợp âm Canon huyền thoại, mang lại cảm giác bình yên và thăng hoa sâu sắc."
        )
    )

    /**
     * Download or import a curated song with safe fallback.
     */
    suspend fun downloadCurated(
        context: Context,
        item: CatalogSongItem,
        downloader: OnlineSongDownloader?,
        songRepository: SongRepository
    ): ContentPackImportResult = withContext(Dispatchers.IO) {
        // 1. First try downloading via OnlineSongDownloader if available
        if (downloader != null) {
            val result = downloader.downloadAndImport(item.downloadUrl, item.title)
            if (result.isSuccess) return@withContext result
        }

        // 2. If download fails or downloader is null, fallback to asset file if exists
        if (!item.assetFallback.isNullOrBlank()) {
            val assetResult = runCatching {
                context.assets.open(item.assetFallback).use { stream ->
                    val bytes = stream.readBytes()
                    songRepository.importMidiFile(
                        inputStream = bytes.inputStream(),
                        originalFileName = "${item.id}.mid",
                        fileSize = bytes.size.toLong(),
                        customTitle = item.title
                    )
                }
            }
            val imported = assetResult.getOrNull()?.getOrNull()
            if (imported != null) {
                return@withContext ContentPackImportResult(
                    isSuccess = true,
                    songId = imported.id,
                    title = imported.displayName,
                    noteCount = imported.noteCount,
                    hasSheetMusic = false,
                    hasAudioTrack = false
                )
            }
        }

        ContentPackImportResult(
            isSuccess = false,
            errorMessage = "Không thể tải hoặc cài đặt bài nhạc '${item.title}'."
        )
    }
}
