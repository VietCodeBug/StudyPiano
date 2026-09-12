package com.ian.pianotrainer.feature.mysongs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.core.contentpack.CatalogSongItem
import com.ian.pianotrainer.core.contentpack.OnlineSongCatalog
import com.ian.pianotrainer.core.contentpack.ImportFileClassifier
import com.ian.pianotrainer.core.contentpack.ImportFileKind
import com.ian.pianotrainer.data.local.database.entity.SongTrackEntity
import com.ian.pianotrainer.domain.model.ImportedSong
import com.ian.pianotrainer.domain.model.PracticeMode
import com.ian.pianotrainer.domain.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Collator
import java.util.Locale

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
    val isLoadingTracks: Boolean = true,
    val resources: List<SongResourceState> = emptyList()
)

data class SongResourceState(
    val type: com.ian.pianotrainer.domain.model.SongAssetType,
    val fileName: String,
    val isAvailable: Boolean
)

private sealed interface LibraryLoadState {
    data object Loading : LibraryLoadState
    data class Ready(val songs: List<ImportedSong>) : LibraryLoadState
    data class Failed(val message: String) : LibraryLoadState
}

data class MySongsUiState(
    val songs: List<ImportedSong> = emptyList(),
    val searchQuery: String = "",
    val showFavoritesOnly: Boolean = false,
    val sortOption: SongSortOption = SongSortOption.RECENT_IMPORTED,
    val prepState: SongPreparationState? = null,
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null,
    val curatedCatalog: List<CatalogSongItem> = OnlineSongCatalog.curatedSongs
)

internal fun filterAndSortSongs(
    songs: List<ImportedSong>,
    query: String,
    favoritesOnly: Boolean,
    sort: SongSortOption
): List<ImportedSong> = songs.filter { song ->
    val matchesQuery = song.displayName.contains(query.trim(), ignoreCase = true) ||
        song.originalFileName.contains(query.trim(), ignoreCase = true)
    matchesQuery && (!favoritesOnly || song.isFavorite)
}.let { filtered ->
    when (sort) {
        SongSortOption.RECENT_IMPORTED -> filtered.sortedByDescending { it.importedAt }
        SongSortOption.RECENT_PRACTICED -> filtered.sortedWith(
            compareByDescending<ImportedSong> { it.lastPracticedAt ?: Long.MIN_VALUE }
                .thenByDescending { it.importedAt }
        )
        SongSortOption.TITLE_AZ -> {
            val vietnamese = Collator.getInstance(Locale("vi", "VN")).apply {
                strength = Collator.PRIMARY
            }
            filtered.sortedWith { left, right -> vietnamese.compare(left.displayName, right.displayName) }
        }
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
    private val _libraryRetry = MutableStateFlow(0)
    private var preparationJob: Job? = null

    private val libraryState = _libraryRetry.flatMapLatest {
        songRepository.getAllSongs()
            .map<List<ImportedSong>, LibraryLoadState> { LibraryLoadState.Ready(it) }
            .onStart { emit(LibraryLoadState.Loading) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(LibraryLoadState.Failed(error.localizedMessage ?: "Không thể đọc thư viện"))
            }
    }

    private val _filterState = combine(_searchQuery, _showFavoritesOnly, _sortOption) { query, favOnly, sort ->
        Triple(query, favOnly, sort)
    }

    private val _statusState = combine(_isImporting, _feedbackMessage, _errorMessage, _prepState) { importing, feedback, error, prep ->
        listOf(importing as Any?, feedback, error, prep)
    }

    val uiState: StateFlow<MySongsUiState> = combine(
        libraryState,
        _filterState,
        _statusState
    ) { library, filter, status ->
        val query = filter.first
        val favOnly = filter.second
        val sort = filter.third

        val importing = status[0] as Boolean
        val feedback = status[1] as String?
        val error = status[2] as String?
        @Suppress("UNCHECKED_CAST")
        val prep = status[3] as SongPreparationState?

        val allSongs = (library as? LibraryLoadState.Ready)?.songs.orEmpty()
        val filtered = filterAndSortSongs(allSongs, query, favOnly, sort)
        val libraryError = (library as? LibraryLoadState.Failed)?.message

        MySongsUiState(
            songs = filtered,
            searchQuery = query,
            showFavoritesOnly = favOnly,
            sortOption = sort,
            prepState = prep,
            isLoading = library is LibraryLoadState.Loading,
            isImporting = importing,
            feedbackMessage = feedback,
            errorMessage = error ?: libraryError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MySongsUiState(isLoading = true)
    )

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

    fun retryLibrary() {
        _errorMessage.value = null
        _libraryRetry.value += 1
    }

    fun toggleFavorite(songId: String) {
        viewModelScope.launch {
            try {
                songRepository.toggleFavorite(songId)
                _prepState.value?.takeIf { it.song.id == songId }?.let {
                    _prepState.value = it.copy(song = it.song.copy(isFavorite = !it.song.isFavorite))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showError("Không thể cập nhật yêu thích", error)
            }
        }
    }

    fun renameSong(songId: String, newName: String, onSuccess: () -> Unit = {}) {
        val cleanName = newName.trim().take(100)
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            try {
                songRepository.renameSong(songId, cleanName)
                _prepState.value?.let { current ->
                    if (current.song.id == songId) {
                        _prepState.value = current.copy(song = current.song.copy(displayName = cleanName))
                    }
                }
                _feedbackMessage.value = "Đã cập nhật tên bài nhạc"
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showError("Không thể đổi tên bài", error)
            }
        }
    }

    fun deleteSong(songId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                songRepository.deleteSong(songId)
                _feedbackMessage.value = "Đã xóa bài nhạc khỏi thư viện"
                if (_prepState.value?.song?.id == songId) _prepState.value = null
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showError("Không thể xóa bài", error)
            }
        }
    }

    fun openSongPreparation(song: ImportedSong) {
        preparationJob?.cancel()
        val requestedId = song.id
        _prepState.value = SongPreparationState(
            song = song,
            tracks = emptyList(),
            selectedPracticeMode = PracticeMode.WAIT_FOR_NOTE,
            customBpm = song.defaultBpm,
            isLoadingTracks = true
        )
        preparationJob = viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val currentSong = songRepository.getSongById(requestedId) ?: song
                    val tracks = songRepository.getSongTracks(requestedId)
                    val resources = currentSong.assets.map {
                        SongResourceState(it.type, it.originalFileName, File(it.localFilePath).isFile)
                    }.ifEmpty {
                        currentSong.localFilePath?.let {
                            listOf(SongResourceState(com.ian.pianotrainer.domain.model.SongAssetType.MIDI, currentSong.originalFileName, File(it).isFile))
                        }.orEmpty()
                    }
                    Triple(currentSong, tracks, resources)
                }
                val current = _prepState.value
                if (current?.song?.id == requestedId) {
                    _prepState.value = current.copy(song = loaded.first, tracks = loaded.second, resources = loaded.third, isLoadingTracks = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_prepState.value?.song?.id == requestedId) {
                    _prepState.value = _prepState.value?.copy(isLoadingTracks = false)
                    showError("Không thể tải chi tiết bài", error)
                }
            }
        }
    }

    fun closeSongPreparation() {
        preparationJob?.cancel()
        preparationJob = null
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
            try {
                songRepository.updateTrackConfigurations(current.song.id, current.tracks)
                val hand = when {
                    activeTracks.all { it.assignedHand == "RIGHT" } -> "RIGHT"
                    activeTracks.all { it.assignedHand == "LEFT" } -> "LEFT"
                    else -> "BOTH"
                }
                if (_prepState.value?.song?.id == current.song.id) _prepState.value = null
                onStart(current.song.displayName, current.song.id, hand, current.selectedPracticeMode, current.customBpm)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showError("Không thể lưu cấu hình luyện", error)
            }
        }
    }

    fun importMidiFromUri(uri: Uri, context: Context, customTitle: String? = null) {
        viewModelScope.launch {
            if (_isImporting.value) return@launch
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
            } catch (e: CancellationException) {
                throw e
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
            if (_isImporting.value) return@launch
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = "Lỗi khi tải bài: ${e.localizedMessage}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun downloadCuratedSong(item: CatalogSongItem, context: Context) {
        viewModelScope.launch {
            if (_isImporting.value) return@launch
            _isImporting.value = true
            _errorMessage.value = null
            _feedbackMessage.value = null
            try {
                val result = OnlineSongCatalog.downloadCurated(
                    context = context,
                    item = item,
                    downloader = onlineSongDownloader,
                    songRepository = songRepository
                )
                if (result.isSuccess && result.songId != null) {
                    _feedbackMessage.value = "Tải thành công: ${result.title} (${result.noteCount} nốt)"
                    val song = songRepository.getSongById(result.songId)
                    if (song != null) {
                        openSongPreparation(song)
                    }
                } else {
                    _errorMessage.value = result.errorMessage ?: "Không thể tải bài nhạc '${item.title}'"
                }
            } catch (e: CancellationException) {
                throw e
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
            if (_isImporting.value) return@launch
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = "Lỗi khi xử lý gói: ${e.localizedMessage}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun importFromUri(uri: Uri, context: Context) {
        viewModelScope.launch {
            if (_isImporting.value) return@launch
            _isImporting.value = true
            _errorMessage.value = null
            _feedbackMessage.value = null
            try {
                val imported = withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    var displayName = "imported.mid"
                    var fileSize = 0L
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let {
                                displayName = cursor.getString(it) ?: displayName
                            }
                            cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let {
                                if (!cursor.isNull(it)) fileSize = cursor.getLong(it)
                            }
                        }
                    }
                    val header = resolver.openInputStream(uri)?.use { input ->
                        val bytes = ByteArray(8)
                        val count = input.read(bytes)
                        if (count > 0) bytes.copyOf(count) else ByteArray(0)
                    } ?: throw IllegalArgumentException("Không thể mở tệp được chọn.")

                    when (ImportFileClassifier.classify(displayName, resolver.getType(uri), header)) {
                        ImportFileKind.MIDI -> resolver.openInputStream(uri)?.use { stream ->
                            songRepository.importMidiFile(stream, displayName, fileSize)
                        } ?: Result.failure(IllegalArgumentException("Không thể mở tệp MIDI được chọn."))
                        ImportFileKind.PIANO_PACK -> {
                            val importer = contentPackImporter
                                ?: return@withContext Result.failure(IllegalStateException("Chức năng nhập PianoPack chưa khả dụng."))
                            val pack = resolver.openInputStream(uri)?.use { importer.importPack(it) }
                                ?: return@withContext Result.failure(IllegalArgumentException("Không thể mở PianoPack được chọn."))
                            if (!pack.isSuccess || pack.songId == null) {
                                Result.failure(IllegalArgumentException(pack.errorMessage ?: "PianoPack không hợp lệ."))
                            } else {
                                songRepository.getSongById(pack.songId)?.let { Result.success(it) }
                                    ?: Result.failure(IllegalStateException("Đã nhập file nhưng không đọc được bài vừa lưu."))
                            }
                        }
                        ImportFileKind.UNSUPPORTED -> Result.failure(
                            IllegalArgumentException("Nội dung tệp không phải MIDI hoặc ZIP/PianoPack hợp lệ. Gói MXL chỉ được nhận khi đi kèm MIDI trong PianoPack.")
                        )
                    }
                }
                imported.fold(
                    onSuccess = { song ->
                        _feedbackMessage.value = "Nhập thành công: " + song.displayName + " (" + song.noteCount + " nốt)"
                        openSongPreparation(song)
                    },
                    onFailure = { throw it }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _errorMessage.value = error.localizedMessage ?: "Không thể nhập tệp."
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
        _errorMessage.value = null
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun reportError(message: String) {
        _errorMessage.value = message
    }

    private fun showError(prefix: String, error: Throwable) {
        _errorMessage.value = prefix + ": " + (error.localizedMessage ?: "lỗi không xác định")
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
