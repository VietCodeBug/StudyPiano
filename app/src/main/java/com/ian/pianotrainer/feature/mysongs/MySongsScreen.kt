package com.ian.pianotrainer.feature.mysongs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.sp
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
import com.ian.pianotrainer.core.music.NoteHelper
import com.ian.pianotrainer.core.ui.AppTopBar
import com.ian.pianotrainer.core.ui.ConfirmationDialog
import com.ian.pianotrainer.core.ui.EmptyState
import com.ian.pianotrainer.core.ui.LoadingState
import com.ian.pianotrainer.core.ui.PrimaryButton
import com.ian.pianotrainer.core.ui.SectionHeader
import com.ian.pianotrainer.data.local.database.entity.SongTrackEntity
import com.ian.pianotrainer.domain.model.ImportedSong
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.domain.model.PracticeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySongsScreen(
    viewModel: MySongsViewModel,
    onStartPractice: (title: String, songId: String, handMode: String, practiceMode: PracticeMode, bpm: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var songToDelete by remember { mutableStateOf<ImportedSong?>(null) }
    var songToRename by remember { mutableStateOf<ImportedSong?>(null) }
    var renameInputText by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadUrlText by remember { mutableStateOf("") }
    var downloadTitleText by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { pickedUri ->
            val scheme = pickedUri.toString().lowercase()
            if (scheme.endsWith(".zip") || scheme.endsWith(".pianopack")) {
                viewModel.importPackFromUri(pickedUri, context)
            } else {
                viewModel.importMidiFromUri(pickedUri, context)
            }
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

    // Delete Confirmation Dialog
    if (songToDelete != null) {
        ConfirmationDialog(
            title = "Xác nhận xóa bài nhạc",
            message = "Bạn có chắc chắn muốn xóa bài \"${songToDelete?.displayName}\" khỏi thư viện và bộ nhớ máy không?",
            confirmText = "Xóa bài",
            dismissText = "Hủy",
            onConfirm = {
                songToDelete?.let { viewModel.deleteSong(it.id) }
                songToDelete = null
            },
            onDismiss = { songToDelete = null }
        )
    }

    // Download Song from OnlineSequencer / Link Dialog
    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, tint = PianoPrimary)
                    Text(
                        text = "Tải bài nhạc Online",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PianoTextPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Nhập mã ID hoặc liên kết OnlineSequencer (hoặc URL tệp .mid/.pianopack trực tiếp):",
                        style = MaterialTheme.typography.bodySmall,
                        color = PianoTextSecondary
                    )

                    OutlinedTextField(
                        value = downloadUrlText,
                        onValueChange = { downloadUrlText = it },
                        label = { Text("Mã ID hoặc URL bài nhạc") },
                        placeholder = { Text("Ví dụ: 3134103 hoặc https://...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = PianoPrimary)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PianoPrimary,
                            unfocusedBorderColor = PianoOutline,
                            focusedTextColor = PianoTextPrimary,
                            unfocusedTextColor = PianoTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = downloadTitleText,
                        onValueChange = { downloadTitleText = it },
                        label = { Text("Tên bài hiển thị (tùy chọn)") },
                        placeholder = { Text("Để trống sẽ tự động lấy tên gốc") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PianoPrimary,
                            unfocusedBorderColor = PianoOutline,
                            focusedTextColor = PianoTextPrimary,
                            unfocusedTextColor = PianoTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Hỗ trợ nhập Sequence ID hoặc link tải tệp trực tiếp (.mid / .zip / .pianopack).",
                        style = MaterialTheme.typography.bodySmall,
                        color = PianoTextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (downloadUrlText.isNotBlank()) {
                            viewModel.downloadSong(
                                urlOrId = downloadUrlText,
                                customTitle = downloadTitleText.takeIf { it.isNotBlank() }
                            )
                            showDownloadDialog = false
                            downloadUrlText = ""
                            downloadTitleText = ""
                        }
                    },
                    modifier = Modifier.testTag("confirm_download_button")
                ) {
                    Text("Tải về & Thêm", color = PianoPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text("Hủy", color = PianoTextSecondary)
                }
            },
            containerColor = PianoSurface,
            shape = PianoShapes.medium
        )
    }

    // Rename Dialog
    if (songToRename != null) {
        AlertDialog(
            onDismissRequest = { songToRename = null },
            title = {
                Text(
                    text = "Đổi tên bài nhạc",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PianoTextPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    label = { Text("Tên hiển thị") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PianoPrimary,
                        unfocusedBorderColor = PianoOutline,
                        focusedTextColor = PianoTextPrimary,
                        unfocusedTextColor = PianoTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_song_text_field")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        songToRename?.let { song ->
                            if (renameInputText.isNotBlank()) {
                                viewModel.renameSong(song.id, renameInputText)
                            }
                        }
                        songToRename = null
                    },
                    modifier = Modifier.testTag("confirm_rename_button")
                ) {
                    Text("Lưu", color = PianoPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { songToRename = null }) {
                    Text("Hủy", color = PianoTextSecondary)
                }
            },
            containerColor = PianoSurface,
            shape = PianoShapes.medium
        )
    }

    // Song Preparation BottomSheet
    uiState.prepState?.let { prepState ->
        SongPreparationBottomSheet(
            prepState = prepState,
            onDismiss = viewModel::closeSongPreparation,
            onTrackHandChanged = viewModel::updateTrackHand,
            onTrackSelectionToggled = viewModel::toggleTrackSelection,
            onModeChanged = viewModel::setPrepPracticeMode,
            onBpmChanged = viewModel::setPrepBpm,
            onRenameClick = {
                songToRename = prepState.song
                renameInputText = prepState.song.displayName
            },
            onStartPractice = {
                viewModel.saveTrackConfigAndStart(onStartPractice)
            }
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
                            onClick = { showDownloadDialog = true },
                            modifier = Modifier.testTag("download_song_action_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Tải bài nhạc Online",
                                tint = PianoPrimary
                            )
                        }

                        IconButton(
                            onClick = {
                                filePickerLauncher.launch(
                                    arrayOf(
                                        "audio/midi",
                                        "audio/mid",
                                        "audio/x-midi",
                                        "application/x-midi",
                                        "application/zip",
                                        "application/octet-stream",
                                        "*/*"
                                    )
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
            // Search, Filter and Sort Bar
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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

                        Box {
                            FilterChip(
                                selected = false,
                                onClick = { showSortMenu = true },
                                label = { Text(uiState.sortOption.displayName) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sắp xếp",
                                        modifier = Modifier.size(16.dp),
                                        tint = PianoPrimary
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = PianoSurface,
                                    labelColor = PianoTextPrimary
                                ),
                                modifier = Modifier.testTag("sort_filter_chip")
                            )

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.background(PianoSurface)
                            ) {
                                SongSortOption.values().forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option.displayName,
                                                color = if (uiState.sortOption == option) PianoPrimary else PianoTextPrimary,
                                                fontWeight = if (uiState.sortOption == option) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            viewModel.setSortOption(option)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "${uiState.songs.size} bài",
                        style = MaterialTheme.typography.bodySmall,
                        color = PianoTextSecondary
                    )
                }
            }

            // Song List or Empty State
            when {
                uiState.isLoading -> {
                    LoadingState(message = "Đang tải thư viện bài nhạc...")
                }

                uiState.songs.isEmpty() -> {
                    EmptyState(
                        title = stringResource(R.string.songs_empty_title),
                        description = if (uiState.searchQuery.isNotBlank() || uiState.showFavoritesOnly) {
                            "Không tìm thấy bài hát nào phù hợp với điều kiện tìm kiếm."
                        } else {
                            "Ứng dụng hoàn toàn offline và không cài sẵn bài hát có bản quyền. Hãy nhấn nút bên dưới để nhập file .mid / .midi từ điện thoại của bạn."
                        },
                        icon = Icons.Default.LibraryMusic,
                        actionButton = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PrimaryButton(
                                    text = "✨ Nạp 16 bài giáo trình có sẵn",
                                    onClick = { viewModel.seedStarterSongs() },
                                    tag = "seed_starter_songs_button"
                                )

                                PrimaryButton(
                                    text = "☁️ Tải từ OnlineSequencer / Link",
                                    onClick = { showDownloadDialog = true },
                                    tag = "download_first_song_button"
                                )

                                TextButton(
                                    onClick = {
                                        filePickerLauncher.launch(
                                            arrayOf(
                                                "audio/midi",
                                                "audio/mid",
                                                "audio/x-midi",
                                                "application/x-midi",
                                                "application/zip",
                                                "application/octet-stream",
                                                "*/*"
                                            )
                                        )
                                    }
                                ) {
                                    Text("Nhập tệp từ máy (.mid / .pianopack)", color = PianoPrimary)
                                }
                            }
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
                                onCardClick = {
                                    viewModel.openSongPreparation(song)
                                },
                                onToggleFavorite = {
                                    viewModel.toggleFavorite(song.id)
                                },
                                onRenameClick = {
                                    songToRename = song
                                    renameInputText = song.displayName
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
    onCardClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateStr = remember(song.importedAt) {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(song.importedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PianoShapes.medium)
            .clickable { onCardClick() }
            .testTag("song_item_${song.id}"),
        shape = PianoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = PianoSurface),
        border = BorderStroke(1.dp, PianoOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                        text = "${song.difficulty} • ${song.defaultBpm} BPM • ${song.formattedDuration()} • ${song.trackCount} track",
                        style = MaterialTheme.typography.bodySmall,
                        color = PianoTextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = PianoShapes.small,
                            color = PianoPrimaryContainer
                        ) {
                            Text(
                                text = "MIDI",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = PianoPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        Surface(
                            shape = PianoShapes.small,
                            color = Color(0x2210B981)
                        ) {
                            Text(
                                text = "Acoustic Synth",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        Surface(
                            shape = PianoShapes.small,
                            color = Color(0x22F97316)
                        ) {
                            Text(
                                text = "2 tay",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = Color(0xFFF97316),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (song.lastPracticedAt != null) {
                        val lastPracticedStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(song.lastPracticedAt))
                        Text(
                            text = "Luyện gần nhất: $lastPracticedStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = PianoPrimary
                        )
                    }
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
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onRenameClick,
                    modifier = Modifier.testTag("rename_button_${song.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Đổi tên",
                        tint = PianoTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.testTag("delete_button_${song.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Xóa bài",
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongPreparationBottomSheet(
    prepState: SongPreparationState,
    onDismiss: () -> Unit,
    onTrackHandChanged: (trackIndex: Int, assignedHand: String) -> Unit,
    onTrackSelectionToggled: (trackIndex: Int) -> Unit,
    onModeChanged: (PracticeMode) -> Unit,
    onBpmChanged: (Int) -> Unit,
    onRenameClick: () -> Unit,
    onStartPractice: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PianoSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Title & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = prepState.song.displayName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = PianoTextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "Thời lượng: ${prepState.song.formattedDuration()} • Độ khó: ${prepState.song.difficulty}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PianoTextSecondary
                    )
                }

                IconButton(onClick = onRenameClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Đổi tên",
                        tint = PianoPrimary
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            // Mode Selection
            SectionHeader(title = "Chế độ luyện tập")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = prepState.selectedPracticeMode == PracticeMode.WAIT_FOR_NOTE,
                    onClick = { onModeChanged(PracticeMode.WAIT_FOR_NOTE) },
                    label = { Text("Chờ đúng nốt (Wait)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PianoPrimaryContainer,
                        selectedLabelColor = PianoPrimary,
                        containerColor = PianoSurfaceVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_wait_chip")
                )

                FilterChip(
                    selected = prepState.selectedPracticeMode == PracticeMode.RHYTHM,
                    onClick = { onModeChanged(PracticeMode.RHYTHM) },
                    label = { Text("Chạy theo nhịp (Rhythm)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PianoPrimaryContainer,
                        selectedLabelColor = PianoPrimary,
                        containerColor = PianoSurfaceVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_rhythm_chip")
                )
            }

            // Tempo / BPM Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tốc độ chơi (BPM)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = PianoTextPrimary
                )
                Text(
                    text = "${prepState.customBpm} BPM",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PianoPrimary
                )
            }

            Slider(
                value = prepState.customBpm.toFloat(),
                onValueChange = { onBpmChanged(it.toInt()) },
                valueRange = 30f..200f,
                steps = 34,
                colors = SliderDefaults.colors(
                    thumbColor = PianoPrimary,
                    activeTrackColor = PianoPrimary,
                    inactiveTrackColor = PianoOutline
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Track & Hand Configurations
            SectionHeader(title = "Cấu hình Track & Phân tay (${prepState.tracks.size} tracks)")

            if (prepState.isLoadingTracks) {
                LoadingState(message = "Đang phân tích track MIDI...")
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    prepState.tracks.forEach { track ->
                        TrackConfigRow(
                            track = track,
                            onHandChanged = { newHand ->
                                onTrackHandChanged(track.trackIndex, newHand)
                            },
                            onSelectionToggled = {
                                onTrackSelectionToggled(track.trackIndex)
                            }
                        )
                    }
                }
            }

            }

            // Sticky action footer
            // Start Practice Button
            PrimaryButton(
                text = "Bắt đầu luyện bài",
                onClick = onStartPractice,
                modifier = Modifier.fillMaxWidth(),
                tag = "start_song_practice_button"
            )
        }
    }
}

@Composable
private fun TrackConfigRow(
    track: SongTrackEntity,
    onHandChanged: (String) -> Unit,
    onSelectionToggled: () -> Unit
) {
    val minNoteName = NoteHelper.formatNoteName(track.minMidiNote, NoteNamingMode.CDE)
    val maxNoteName = NoteHelper.formatNoteName(track.maxMidiNote, NoteNamingMode.CDE)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = PianoShapes.small,
        colors = CardDefaults.cardColors(containerColor = PianoSurfaceVariant),
        border = BorderStroke(1.dp, if (track.isSelectedForPractice) PianoPrimary.copy(alpha = 0.4f) else PianoOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = track.isSelectedForPractice,
                        onCheckedChange = { onSelectionToggled() },
                        colors = CheckboxDefaults.colors(checkedColor = PianoPrimary)
                    )
                    Column {
                        Text(
                            text = track.trackName.ifBlank { "Track ${track.trackIndex + 1}" },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = PianoTextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "${track.noteCount} nốt • $minNoteName - $maxNoteName • ${track.channelSummary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = PianoTextSecondary
                        )
                    }
                }
            }

            if (track.isSelectedForPractice) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "RIGHT" to "Tay phải",
                        "LEFT" to "Tay trái",
                        "BOTH" to "Cả hai",
                        "IGNORE" to "Bỏ qua"
                    ).forEach { (handCode, label) ->
                        val isSelected = track.assignedHand == handCode
                        val activeColor = if (handCode == "LEFT") Color(0xFFF97316) else PianoPrimary
                        Surface(
                            shape = PianoShapes.small,
                            color = if (isSelected) activeColor.copy(alpha = 0.2f) else PianoSurface,
                            border = BorderStroke(1.dp, if (isSelected) activeColor else PianoOutline),
                            modifier = Modifier
                                .weight(1f)
                                .clip(PianoShapes.small)
                                .clickable { onHandChanged(handCode) }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    ),
                                    color = if (isSelected) activeColor else PianoTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
