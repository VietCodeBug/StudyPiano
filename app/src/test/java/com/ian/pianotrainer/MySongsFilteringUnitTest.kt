package com.ian.pianotrainer

import com.ian.pianotrainer.domain.model.ImportedSong
import com.ian.pianotrainer.feature.mysongs.SongSortOption
import com.ian.pianotrainer.feature.mysongs.filterAndSortSongs
import org.junit.Assert.assertEquals
import org.junit.Test

class MySongsFilteringUnitTest {
    private val songs = listOf(
        ImportedSong("a", "Bản tình ca", "romance.mid", importedAt = 100, lastPracticedAt = 900),
        ImportedSong("b", "Ánh trăng", "moon.mid", importedAt = 300, isFavorite = true),
        ImportedSong("c", "Canon", "canon-package.mid", importedAt = 200, lastPracticedAt = 500)
    )

    @Test
    fun search_matches_display_name_or_original_file_name_case_insensitively() {
        assertEquals(listOf("a"), filterAndSortSongs(songs, "TÌNH", false, SongSortOption.RECENT_IMPORTED).map { it.id })
        assertEquals(listOf("b"), filterAndSortSongs(songs, "MOON", false, SongSortOption.RECENT_IMPORTED).map { it.id })
    }

    @Test
    fun favorite_filter_and_all_sort_options_are_deterministic() {
        assertEquals(listOf("b"), filterAndSortSongs(songs, "", true, SongSortOption.RECENT_IMPORTED).map { it.id })
        assertEquals(listOf("b", "c", "a"), filterAndSortSongs(songs, "", false, SongSortOption.RECENT_IMPORTED).map { it.id })
        assertEquals(listOf("a", "c", "b"), filterAndSortSongs(songs, "", false, SongSortOption.RECENT_PRACTICED).map { it.id })
        assertEquals(listOf("b", "a", "c"), filterAndSortSongs(songs, "", false, SongSortOption.TITLE_AZ).map { it.id })
    }

    @Test
    fun whitespace_query_behaves_like_empty_search() {
        assertEquals(3, filterAndSortSongs(songs, "   ", false, SongSortOption.RECENT_IMPORTED).size)
    }
}
