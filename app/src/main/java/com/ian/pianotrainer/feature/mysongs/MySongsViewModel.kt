package com.ian.pianotrainer.feature.mysongs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.ImportedSong
import com.ian.pianotrainer.domain.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MySongsUiState(
    val songs: List<ImportedSong> = emptyList(),
    val searchQuery: String = "",
    val showFavoritesOnly: Boolean = false,
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

class MySongsViewModel(
    private val songRepository: SongRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _showFavoritesOnly = MutableStateFlow(false)
    private val _isImporting = MutableStateFlow(false)
    private val _feedbackMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val _filterState = combine(_searchQuery, _showFavoritesOnly) { query, favOnly ->
        Pair(query, favOnly)
    }

    private val _statusState = combine(_isImporting, _feedbackMessage, _errorMessage) { importing, feedback, error ->
        Triple(importing, feedback, error)
    }

    val uiState: StateFlow<MySongsUiState> = combine(
        songRepository.getAllSongs(),
        _filterState,
        _statusState
    ) { allSongs, filter, status ->
        val query = filter.first
        val favOnly = filter.second
        val importing = status.first
        val feedback = status.second
        val error = status.third

        val filtered = allSongs.filter { song ->
            val matchesQuery = song.displayName.contains(query, ignoreCase = true) ||
                    song.originalFileName.contains(query, ignoreCase = true)
            val matchesFav = !favOnly || song.isFavorite
            matchesQuery && matchesFav
        }

        MySongsUiState(
            songs = filtered,
            searchQuery = query,
            showFavoritesOnly = favOnly,
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

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavoritesFilter() {
        _showFavoritesOnly.value = !_showFavoritesOnly.value
    }

    fun toggleFavorite(songId: String) {
        viewModelScope.launch {
            songRepository.toggleFavorite(songId)
        }
    }

    fun deleteSong(songId: String) {
        viewModelScope.launch {
            songRepository.deleteSong(songId)
            _feedbackMessage.value = "Đã xóa bài nhạc khỏi thư viện"
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
                    _feedbackMessage.value = "Nhập thành công: ${song.displayName} (${song.notes.size} nốt)"
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

    fun clearFeedback() {
        _feedbackMessage.value = null
        _errorMessage.value = null
    }

    class Factory(
        private val songRepository: SongRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MySongsViewModel(songRepository) as T
        }
    }
}
