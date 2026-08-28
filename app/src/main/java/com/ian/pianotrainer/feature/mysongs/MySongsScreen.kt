package com.ian.pianotrainer.feature.mysongs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var songToDelete by remember { mutableStateOf<ImportedSong?>(null) }

    val midiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importMidiFromUri(it, context)
        }
    }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearFeedback()
        }
    }

    if (songToDelete != null) {
        ConfirmationDialog(
            title = "Xác nhận xóa bài nhạc",
            message = "Bạn có chắc chắn muốn xóa bài \"${songToDelete?.displayName}\" khỏi thư viện không?",
            confirmText = "Xóa",
            dismissText = "Hủy",
            onConfirm = {
                songToDelete?.let { viewModel.deleteSong(it.id) }
                songToDelete = null
            },
            onDismiss = { songToDelete = null }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_my_songs),
                actions = {
                    if (uiState.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            color = PianoPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = {
                                midiPickerLauncher.launch(
                                    arrayOf("audio/midi", "audio/mid", "audio/x-midi", "application/x-midi", "*/*")
                                )
                            },
                            modifier = Modifier.testTag("import_song_action_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = stringResource(R.string.songs_import_button),
                                tint = PianoPrimary
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = PianoTextSecondary
                        )
                    },
                    singleLine = true,
                    shape = PianoShapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = PianoSurface,
                        unfocusedContainerColor = PianoSurface,
                        focusedBorderColor = PianoPrimary,
                        unfocusedBorderColor = PianoOutline,
                        focusedTextColor = PianoTextPrimary,
                        unfocusedTextColor = PianoTextPrimary,
                        unfocusedPlaceholderColor = PianoTextSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_song_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = uiState.showFavoritesOnly,
                        onClick = viewModel::toggleFavoritesFilter,
                        label = { Text(stringResource(R.string.songs_filter_favorites)) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (uiState.showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (uiState.showFavoritesOnly) PianoError else PianoTextSecondary
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PianoPrimaryContainer,
                            selectedLabelColor = PianoPrimary,
                            containerColor = PianoSurface
                        ),
                        modifier = Modifier.testTag("favorite_filter_chip")
                    )

                    Text(
                        text = "${uiState.songs.size} bài nhạc",
                        style = MaterialTheme.typography.bodySmall,
                        color = PianoTextSecondary
                    )
                }
            }

            // Song List or Empty State
            when {
                uiState.isLoading -> {
                    LoadingState(message = "Đang tải thư viện...")
                }

                uiState.songs.isEmpty() -> {
                    EmptyState(
                        title = stringResource(R.string.songs_empty_title),
                        description = if (uiState.searchQuery.isNotBlank() || uiState.showFavoritesOnly) {
                            "Không tìm thấy bài hát nào phù hợp với bộ lọc hiện tại."
                        } else {
                            stringResource(R.string.songs_empty_desc)
                        },
                        icon = Icons.Default.LibraryMusic,
                        actionButton = {
                            PrimaryButton(
                                text = "Chọn file MIDI (.mid) từ máy",
                                onClick = {
                                    midiPickerLauncher.launch(
                                        arrayOf("audio/midi", "audio/mid", "audio/x-midi", "application/x-midi", "*/*")
                                    )
                                },
                                tag = "import_first_song_button"
                            )
                        }
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.songs, key = { it.id }) { song ->
                            SongItemCard(
                                song = song,
                                onPlayClick = {
                                    onPracticeSong(song.displayName, song.id, song.defaultBpm)
                                },
                                onToggleFavorite = {
                                    viewModel.toggleFavorite(song.id)
                                },
                                onDeleteClick = {
                                    songToDelete = song
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongItemCard(
    song: ImportedSong,
    onPlayClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PianoShapes.medium)
            .clickable { onPlayClick() }
            .testTag("song_item_${song.id}"),
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
                    color = PianoPrimaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Luyện bài",
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
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${song.difficulty} • ${song.defaultBpm} BPM • ${song.formattedDuration()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PianoTextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.testTag("fav_button_${song.id}")
                ) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Yêu thích",
                        tint = if (song.isFavorite) PianoError else PianoTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.testTag("delete_button_${song.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Xóa",
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
