package com.ian.pianotrainer.feature.mysongs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ian.pianotrainer.R
import com.ian.pianotrainer.core.designsystem.PianoBackground
import com.ian.pianotrainer.core.designsystem.PianoError
import com.ian.pianotrainer.core.designsystem.PianoOutline
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoPrimaryContainer
import com.ian.pianotrainer.core.designsystem.PianoShapes
import com.ian.pianotrainer.core.designsystem.PianoSurface
import com.ian.pianotrainer.core.designsystem.PianoSurfaceVariant
import com.ian.pianotrainer.core.designsystem.PianoTextPrimary
import com.ian.pianotrainer.core.designsystem.PianoTextSecondary
import com.ian.pianotrainer.core.ui.AppTopBar
import com.ian.pianotrainer.core.ui.ConfirmationDialog
import com.ian.pianotrainer.core.ui.EmptyState
import com.ian.pianotrainer.core.ui.LoadingState
import com.ian.pianotrainer.core.ui.PrimaryButton
import com.ian.pianotrainer.domain.model.ImportedSong

@Composable
fun MySongsScreen(
    viewModel: MySongsViewModel,
    onPracticeSong: (title: String, songId: String, bpm: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showImportInfoDialog by remember { mutableStateOf(false) }

    if (showImportInfoDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.songs_import_title),
            message = stringResource(R.string.songs_import_phase2_note),
            confirmText = "Đã hiểu",
            dismissText = "Đóng",
            onConfirm = { showImportInfoDialog = false },
            onDismiss = { showImportInfoDialog = false }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_my_songs),
                actions = {
                    IconButton(
                        onClick = { showImportInfoDialog = true },
                        modifier = Modifier.testTag("import_song_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = stringResource(R.string.songs_import_button),
                            tint = PianoPrimary
                        )
                    }
                }
            )
        },
        containerColor = PianoBackground,
        modifier = modifier.testTag("my_songs_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search and Filter Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    placeholder = { Text(stringResource(R.string.songs_search_placeholder)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = PianoTextSecondary)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("song_search_input"),
                    shape = PianoShapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = PianoSurface,
                        unfocusedContainerColor = PianoSurface,
                        focusedBorderColor = PianoPrimary,
                        unfocusedBorderColor = PianoOutline
                    ),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = uiState.showFavoritesOnly,
                        onClick = viewModel::toggleFavoritesFilter,
                        label = { Text("Chỉ bài yêu thích") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (uiState.showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (uiState.showFavoritesOnly) PianoError else PianoTextSecondary
                            )
                        },
                        modifier = Modifier.testTag("filter_favorites_chip"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PianoPrimaryContainer,
                            selectedLabelColor = PianoTextPrimary
                        )
                    )

                    Text(
                        text = "${uiState.songs.size} bài nhạc",
                        style = MaterialTheme.typography.bodySmall,
                        color = PianoTextSecondary
                    )
                }
            }

            // Song List or Empty State
            if (uiState.isLoading) {
                LoadingState()
            } else if (uiState.songs.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.songs_empty_title),
                    description = stringResource(R.string.songs_empty_description),
                    icon = Icons.Default.LibraryMusic,
                    actionButton = {
                        PrimaryButton(
                            text = stringResource(R.string.songs_import_button),
                            onClick = { showImportInfoDialog = true },
                            modifier = Modifier.fillMaxWidth(0.7f),
                            tag = "empty_import_button"
                        )
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.songs, key = { it.id }) { song ->
                        SongItemCard(
                            song = song,
                            onPlay = { onPracticeSong(song.displayName, song.id, song.defaultBpm) },
                            onToggleFavorite = { viewModel.toggleFavorite(song.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SongItemCard(
    song: ImportedSong,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PianoShapes.medium)
            .clickable { onPlay() }
            .testTag("song_card_${song.id}"),
        shape = PianoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = PianoSurface),
        border = BorderStroke(1.dp, PianoOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = PianoPrimaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = PianoPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = song.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PianoTextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "${song.difficulty} • ${song.defaultBpm} BPM • ${song.originalFileName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PianoTextSecondary,
                        maxLines = 1
                    )
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.testTag("favorite_button_${song.id}")
            ) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Yêu thích",
                    tint = if (song.isFavorite) PianoError else PianoTextSecondary
                )
            }
        }
    }
}
