package com.ian.pianotrainer.feature.mysongs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.ImportedSong
import com.ian.pianotrainer.domain.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MySongsUiState(
    val songs: List<ImportedSong> = emptyList(),
    val searchQuery: String = "",
    val showFavoritesOnly: Boolean = false,
    val isLoading: Boolean = false
)

class MySongsViewModel(
    private val songRepository: SongRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _showFavoritesOnly = MutableStateFlow(false)

    val uiState: StateFlow<MySongsUiState> = combine(
        songRepository.getAllSongs(),
        _searchQuery,
        _showFavoritesOnly
    ) { allSongs, query, favOnly ->
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
            isLoading = false
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
        }
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
