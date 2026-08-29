package com.ian.pianotrainer.feature.mysongs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.data.local.database.entity.SongTrackEntity
import com.ian.pianotrainer.domain.model.ImportedSong
import com.ian.pianotrainer.domain.model.PracticeMode
import com.ian.pianotrainer.domain.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SongSortOption(val displayName: String) {
    RECENT_IMPORTED("Mới nhập nhất"),
    RECENT_PRACTICED("Luyện gần đây"),
    TITLE_AZ("Tên (A-Z)")
}

data class SongPreparationState(
    val song: ImportedSong,
    val tracks: List<SongTrackEntity> = emptyList(),
    val selectedPracticeMode: PracticeMode = PracticeMode.RHYTHM,
    val customBpm: Int = 60,
    val isLoadingTracks: Boolean = true
)

data class MySongsUiState(
    val songs: List<ImportedSong> = emptyList(),
    val searchQuery: String = "",
    val showFavoritesOnly: Boolean = false,
    val sortOption: SongSortOption = SongSortOption.RECENT_IMPORTED,
    val prepState: SongPreparationState? = null,
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

class MySongsViewModel(
    private val songRepository: SongRepository,
    private val contentPackImporter: com.ian.pianotrainer.core.contentpack.ContentPackImporter? = null,
    private val onlineSongDownloader: com.ian.pianotrainer.core.contentpack.OnlineSongDownloader? = null
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _showFavoritesOnly = MutableStateFlow(false)
    private val _sortOption = MutableStateFlow(SongSortOption.RECENT_IMPORTED)
    private val _isImporting = MutableStateFlow(false)
    private val _feedbackMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _prepState = MutableStateFlow<SongPreparationState?>(null)

    private val _filterState = combine(_searchQuery, _showFavoritesOnly, _sortOption) { query, favOnly, sort ->
        Triple(query, favOnly, sort)
    }

    private val _statusState = combine(_isImporting, _feedbackMessage, _errorMessage, _prepState) { importing, feedback, error, prep ->
        listOf(importing as Any?, feedback, error, prep)
    }

    val uiState: StateFlow<MySongsUiState> = combine(
        songRepository.getAllSongs(),
        _filterState,
        _statusState
    ) { allSongs, filter, status ->
        val query = filter.first
        val favOnly = filter.second
        val sort = filter.third

        val importing = status[0] as Boolean
        val feedback = status[1] as String?
        val error = status[2] as String?
        @Suppress("UNCHECKED_CAST")
        val prep = status[3] as SongPreparationState?

        val filtered = allSongs.filter { song ->
            val matchesQuery = song.displayName.contains(query, ignoreCase = true) ||
                    song.originalFileName.contains(query, ignoreCase = true)
            val matchesFav = !favOnly || song.isFavorite
            matchesQuery && matchesFav
        }.let { list ->
            when (sort) {
                SongSortOption.RECENT_IMPORTED -> list.sortedByDescending { it.importedAt }
                SongSortOption.RECENT_PRACTICED -> list.sortedWith(
                    compareByDescending<ImportedSong> { it.lastPracticedAt ?: 0L }
                        .thenByDescending { it.importedAt }
                )
                SongSortOption.TITLE_AZ -> list.sortedBy { it.displayName.lowercase() }
            }
        }

        MySongsUiState(
            songs = filtered,
            searchQuery = query,
            showFavoritesOnly = favOnly,
            sortOption = sort,
            prepState = prep,
            isLoading = false,
            isImporting = importing,
            feedbackMessage = feedback,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MySongsUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            try {
                val currentSongs = songRepository.getAllSongsList()
                if (currentSongs.isEmpty()) {
                    songRepository.seedCurriculumRepertoire()
                }
            } catch (_: Exception) {}
        }
    }

    fun seedStarterSongs() {
        viewModelScope.launch {
            _isImporting.value = true
            _errorMessage.value = null
            _feedbackMessage.value = null
            try {
                val count = songRepository.seedCurriculumRepertoire()
                if (count > 0) {
                    _feedbackMessage.value = "Đã nạp $count bài hát giáo trình mẫu vào thư viện!"
                } else {
                    _feedbackMessage.value = "Toàn bộ bài hát giáo trình đã có sẵn trong thư viện"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Lỗi khi nạp bài mẫu: ${e.localizedMessage}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: SongSortOption) {
        _sortOption.value = option
    }

    fun toggleFavoritesFilter() {
        _showFavoritesOnly.value = !_showFavoritesOnly.value
    }

    fun toggleFavorite(songId: String) {
        viewModelScope.launch {
            songRepository.toggleFavorite(songId)
        }
    }

    fun renameSong(songId: String, newName: String) {
        val cleanName = newName.trim().take(100)
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            songRepository.renameSong(songId, cleanName)
            _feedbackMessage.value = "Đã cập nhật tên bài nhạc"
            _prepState.value?.let { current ->
                if (current.song.id == songId) {
                    _prepState.value = current.copy(song = current.song.copy(displayName = cleanName))
                }
            }
        }
    }

    fun deleteSong(songId: String) {
        viewModelScope.launch {
            songRepository.deleteSong(songId)
            _feedbackMessage.value = "Đã xóa bài nhạc khỏi thư viện"
            if (_prepState.value?.song?.id == songId) {
                _prepState.value = null
            }
        }
    }

    fun openSongPreparation(song: ImportedSong) {
        _prepState.value = SongPreparationState(
            song = song,
            tracks = emptyList(),
            selectedPracticeMode = PracticeMode.WAIT_FOR_NOTE,
            customBpm = song.defaultBpm,
            isLoadingTracks = true
        )
        viewModelScope.launch {
            val tracks = songRepository.getSongTracks(song.id)
            _prepState.value = _prepState.value?.copy(
                tracks = tracks,
                isLoadingTracks = false
            )
        }
    }

    fun closeSongPreparation() {
        _prepState.value = null
    }

    fun updateTrackHand(trackIndex: Int, newHand: String) {
        val current = _prepState.value ?: return
        val updatedTracks = current.tracks.map { track ->
            if (track.trackIndex == trackIndex) {
                track.copy(assignedHand = newHand)
            } else {
                track
            }
        }
        _prepState.value = current.copy(tracks = updatedTracks)
    }

    fun toggleTrackSelection(trackIndex: Int) {
        val current = _prepState.value ?: return
        val updatedTracks = current.tracks.map { track ->
            if (track.trackIndex == trackIndex) {
                track.copy(isSelectedForPractice = !track.isSelectedForPractice)
            } else {
                track
            }
        }
        _prepState.value = current.copy(tracks = updatedTracks)
    }

    fun setPrepPracticeMode(mode: PracticeMode) {
        _prepState.value = _prepState.value?.copy(selectedPracticeMode = mode)
    }

    fun setPrepBpm(bpm: Int) {
        _prepState.value = _prepState.value?.copy(customBpm = bpm.coerceIn(30, 240))
    }

    fun saveTrackConfigAndStart(
        onStart: (title: String, songId: String, handMode: String, practiceMode: PracticeMode, bpm: Int) -> Unit
    ) {
        val current = _prepState.value ?: return
        val activeTracks = current.tracks.filter { it.isSelectedForPractice }
        if (activeTracks.isEmpty()) {
            _errorMessage.value = "Hãy chọn ít nhất một track để luyện tập"
            return
        }

        viewModelScope.launch {
            songRepository.updateTrackConfigurations(current.song.id, current.tracks)
            _prepState.value = null
            // Determine predominant active hand:
            val hand = when {
                activeTracks.all { it.assignedHand == "RIGHT" } -> "RIGHT"
                activeTracks.all { it.assignedHand == "LEFT" } -> "LEFT"
                else -> "BOTH"
            }
            onStart(
                current.song.displayName,
                current.song.id,
                hand,
                current.selectedPracticeMode,
                current.customBpm
            )
        }
    }

    fun importMidiFromUri(uri: Uri, context: Context, customTitle: String? = null) {
        viewModelScope.launch {
            _isImporting.value = true
            _errorMessage.value = null
            _feedbackMessage.value = null

            try {
                var fileName = "imported.mid"
                var fileSize = 0L

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex) ?: "imported.mid"
                        }
                        if (sizeIndex != -1) {
                            fileSize = cursor.getLong(sizeIndex)
                        }
                    }
                }

                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _errorMessage.value = "Không thể mở file được chọn"
                    _isImporting.value = false
                    return@launch
                }

                val result = inputStream.use { stream ->
                    songRepository.importMidiFile(
                        inputStream = stream,
                        originalFileName = fileName,
                        fileSize = fileSize,
                        customTitle = customTitle
                    )
                }

                result.onSuccess { song ->
                    _feedbackMessage.value = "Nhập thành công: ${song.displayName} (${song.noteCount} nốt)"
                    openSongPreparation(song)
                }.onFailure { error ->
                    _errorMessage.value = error.localizedMessage ?: "Lỗi khi nhập file MIDI"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Lỗi khi xử lý file: ${e.localizedMessage}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun downloadSong(urlOrId: String, customTitle: String? = null) {
        if (onlineSongDownloader == null) {
            _errorMessage.value = "Chức năng tải bài trực tuyến chưa khả dụng"
            return
        }
        viewModelScope.launch {
            _isImporting.value = true
            _errorMessage.value = null
            _feedbackMessage.value = null
            try {
                val result = onlineSongDownloader.downloadAndImport(urlOrId, customTitle)
                if (result.isSuccess && result.songId != null) {
                    _feedbackMessage.value = "Tải thành công: ${result.title} (${result.noteCount} nốt)"
                    val song = songRepository.getSongById(result.songId)
                    if (song != null) {
                        openSongPreparation(song)
                    }
                } else {
                    _errorMessage.value = result.errorMessage ?: "Không thể tải bài nhạc từ liên kết"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Lỗi khi tải bài: ${e.localizedMessage}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun importPackFromUri(uri: Uri, context: Context) {
        if (contentPackImporter == null) {
            importMidiFromUri(uri, context)
            return
        }
        viewModelScope.launch {
            _isImporting.value = true
            _errorMessage.value = null
            _feedbackMessage.value = null
            try {
                val stream = context.contentResolver.openInputStream(uri)
                if (stream == null) {
                    _errorMessage.value = "Không thể mở file gói được chọn"
                    _isImporting.value = false
                    return@launch
                }
                val result = stream.use { contentPackImporter.importPack(it) }
                if (result.isSuccess && result.songId != null) {
                    _feedbackMessage.value = "Nhập gói thành công: ${result.title} (${result.noteCount} nốt)"
                    val song = songRepository.getSongById(result.songId)
                    if (song != null) {
                        openSongPreparation(song)
                    }
                } else {
                    _errorMessage.value = result.errorMessage ?: "Lỗi khi nhập gói bài hát"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Lỗi khi xử lý gói: ${e.localizedMessage}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
        _errorMessage.value = null
    }

    class Factory(
        private val songRepository: SongRepository,
        private val contentPackImporter: com.ian.pianotrainer.core.contentpack.ContentPackImporter? = null,
        private val onlineSongDownloader: com.ian.pianotrainer.core.contentpack.OnlineSongDownloader? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MySongsViewModel(songRepository, contentPackImporter, onlineSongDownloader) as T
        }
    }
}
