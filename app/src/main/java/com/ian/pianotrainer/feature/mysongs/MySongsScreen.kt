package com.ian.pianotrainer.feature.mysongs

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ian.pianotrainer.core.designsystem.*
import com.ian.pianotrainer.domain.model.ImportedSong
import com.ian.pianotrainer.domain.model.PracticeMode
import com.ian.pianotrainer.domain.model.SongAssetType
import com.ian.pianotrainer.data.local.database.entity.SongTrackEntity

private val importTypes = arrayOf("audio/midi", "audio/x-midi", "application/x-midi", "application/zip", "application/octet-stream", "*/*")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MySongsScreen(
    viewModel: MySongsViewModel,
    onStartPractice: (String, String, String, PracticeMode, Int) -> Unit,
    initialOpenDownload: Boolean = false,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var addOpen by rememberSaveable { mutableStateOf(initialOpenDownload) }
    var linkOpen by rememberSaveable { mutableStateOf(false) }
    var sequencerOpen by rememberSaveable { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var link by rememberSaveable { mutableStateOf("") }
    var rename by remember { mutableStateOf<ImportedSong?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<ImportedSong?>(null) }
    val isRenaming = state.operation.pendingAction == SongPendingAction.RENAME
    val isDeleting = state.operation.pendingAction == SongPendingAction.DELETE

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.importFromUri(it, context) }
    }
    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let { snackbar.showSnackbar(it); viewModel.clearFeedback() }
    }
    LaunchedEffect(state.prepState?.song?.id) {
        if (linkOpen && state.prepState != null && state.operation.urlError == null) linkOpen = false
    }
    deleting?.let { song ->
        AlertDialog(
            onDismissRequest = { if (!isDeleting) { deleting = null; viewModel.dismissDeleteError() } },
            title = { Text("Xóa bài khỏi thư viện?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bài “" + song.displayName + "” cùng MIDI, bản nhạc và audio đính kèm sẽ bị xóa khỏi thư viện trên thiết bị.")
                state.operation.deleteError?.let { DialogError(it) }
            } },
            confirmButton = { TextButton(enabled = !isDeleting, onClick = { viewModel.deleteSong(song.id) { deleting = null } }) { Text(if (isDeleting) "Đang xóa…" else "Xóa bài", color = PianoError) } },
            dismissButton = { TextButton(enabled = !isDeleting, onClick = { deleting = null; viewModel.dismissDeleteError() }) { Text("Hủy") } }
        )
    }
    rename?.let { song ->
        AlertDialog(
            onDismissRequest = { if (!isRenaming) { rename = null; viewModel.dismissRenameError() } },
            title = { Text("Đổi tên bài") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(renameText, { renameText = it.take(100) }, enabled = !isRenaming, label = { Text("Tên bài") }, modifier = Modifier.fillMaxWidth().testTag("rename_song_input"))
                state.operation.renameError?.let { DialogError(it) }
            } },
            confirmButton = { TextButton(enabled = renameText.isNotBlank() && !isRenaming, onClick = { viewModel.renameSong(song.id, renameText) { rename = null } }) { Text(if (isRenaming) "Đang lưu…" else "Lưu") } },
            dismissButton = { TextButton(enabled = !isRenaming, onClick = { rename = null; viewModel.dismissRenameError() }) { Text("Hủy") } }
        )
    }
    if (linkOpen) {
        AlertDialog(
            onDismissRequest = { if (!state.isImporting) { linkOpen = false; viewModel.dismissUrlError() } },
            title = { Text("Nhập từ liên kết") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Dán URL tải trực tiếp MIDI hoặc PianoPack. Ứng dụng không tự dò file từ trang web.")
                OutlinedTextField(link, { link = it }, enabled = !state.isImporting, label = { Text("URL file") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("import_url_input"))
                state.operation.urlError?.let { ErrorActions(it, viewModel::retryUrlImport, viewModel::dismissUrlError) }
            } },
            confirmButton = { Button(enabled = link.isNotBlank() && !state.isImporting, onClick = { viewModel.downloadSong(link) }, modifier = Modifier.testTag("import_url_confirm")) { Text(if (state.isImporting) "Đang tải…" else "Tải và nhập") } },
            dismissButton = { TextButton(enabled = !state.isImporting, onClick = { linkOpen = false; viewModel.dismissUrlError() }) { Text("Hủy") } }
        )
    }
    if (sequencerOpen) {
        AlertDialog(
            onDismissRequest = { sequencerOpen = false },
            title = { Text("Online Sequencer") },
            text = { Text("1. Mở bài trên Online Sequencer.\n2. Chọn Export MIDI.\n3. Quay lại và chọn file MIDI vừa tải.") },
            confirmButton = { TextButton(onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://onlinesequencer.net/")))
                } catch (error: Exception) {
                    viewModel.reportError("Không tìm thấy trình duyệt để mở Online Sequencer.")
                }
            }) { Text("Mở trang") } },
            dismissButton = { TextButton(onClick = { sequencerOpen = false; picker.launch(importTypes) }) { Text("Chọn MIDI đã tải") } }
        )
    }
    if (addOpen) {
        ModalBottomSheet(onDismissRequest = { addOpen = false }, containerColor = PianoSurface) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                Text("Thêm bài vào thư viện", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
                AddChoice(Icons.Default.UploadFile, "Nhập từ thiết bị", "MIDI hoặc PianoPack đã lưu trên máy") { addOpen = false; picker.launch(importTypes) }
                AddChoice(Icons.Default.Link, "Nhập từ liên kết", "URL tải trực tiếp MIDI hoặc PianoPack") { addOpen = false; linkOpen = true }
                AddChoice(Icons.Default.Language, "Online Sequencer", "Mở trang, Export MIDI rồi nhập file") { addOpen = false; sequencerOpen = true }
            }
        }
    }
    state.prepState?.let { detail ->
        SongDetail(
            detail, viewModel::closeSongPreparation,
            { viewModel.toggleFavorite(detail.song.id) },
            { rename = detail.song; renameText = detail.song.displayName },
            { deleting = detail.song },
            viewModel::toggleTrackSelection,
            viewModel::updateTrackHand,
            viewModel::setPrepPracticeMode, viewModel::setPrepBpm,
            { viewModel.saveTrackConfigAndStart(onStartPractice) },
            viewModel::retrySongPreparation
        )
    }

    Scaffold(
        modifier = modifier.testTag("my_songs_screen"),
        containerColor = PianoBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { Surface(color = PianoSurface, shadowElevation = 1.dp) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Bài của tôi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Thư viện offline", style = MaterialTheme.typography.bodySmall, color = PianoTextSecondary) }
                Button({ addOpen = true }, enabled = !state.isImporting, modifier = Modifier.testTag("add_song_button")) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Thêm bài") }
            }
        } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    state.searchQuery, viewModel::onSearchQueryChanged,
                    placeholder = { Text("Tìm theo tên bài hoặc tên file") },
                    leadingIcon = { Icon(Icons.Default.Search, "Tìm bài") },
                    trailingIcon = { if (state.searchQuery.isNotEmpty()) IconButton({ viewModel.onSearchQueryChanged("") }) { Icon(Icons.Default.Close, "Xóa từ khóa") } },
                    singleLine = true, shape = PianoShapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("search_song_input")
                )
                FlowRow(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.showFavoritesOnly, viewModel::toggleFavoritesFilter, { Text("Yêu thích") }, leadingIcon = { Icon(if (state.showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, Modifier.size(18.dp)) })
                    Box {
                        FilterChip(false, { sortOpen = true }, { Text(state.sortOption.displayName) }, leadingIcon = { Icon(Icons.Default.Sort, "Sắp xếp", Modifier.size(18.dp)) })
                        DropdownMenu(sortOpen, { sortOpen = false }) { SongSortOption.values().forEach { option ->
                            DropdownMenuItem({ Text(option.displayName) }, { viewModel.setSortOption(option); sortOpen = false })
                        } }
                    }
                    Text(state.songs.size.toString() + " bài", style = MaterialTheme.typography.bodySmall, color = PianoTextSecondary, modifier = Modifier.align(Alignment.CenterVertically))
                }
                state.operationErrorMessage?.let { ErrorLine(it, null, viewModel::dismissError) }
                when {
                    state.isLoading -> EmptyLibrary("Đang tải thư viện…", null)
                    state.libraryErrorMessage != null -> ErrorLine(state.libraryErrorMessage.orEmpty(), viewModel::retryLibrary, null)
                    state.songs.isEmpty() -> {
                        val filtered = state.searchQuery.isNotBlank() || state.showFavoritesOnly
                        EmptyLibrary(if (filtered) "Không tìm thấy bài phù hợp" else "Thư viện chưa có bài", if (filtered) "Thử đổi từ khóa hoặc bỏ lọc yêu thích." else "Thêm MIDI hoặc PianoPack để bắt đầu luyện.")
                    }
                    else -> LazyColumn(state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        items(state.songs, key = { it.id }) { song ->
                            LibrarySong(song, { viewModel.openSongPreparation(song) }, { viewModel.toggleFavorite(song.id) }, { rename = song; renameText = song.displayName }, { deleting = song })
                        }
                    }
                }
            }
            if (state.isImporting) Surface(color = PianoSurface, shape = PianoShapes.medium, shadowElevation = 6.dp, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).testTag("import_loading")) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Text("Đang xử lý bài nhạc…") }
            }
        }
    }
}

@Composable private fun AddChoice(icon: ImageVector, title: String, subtitle: String, action: () -> Unit) {
    ListItem({ Text(title, fontWeight = FontWeight.SemiBold) }, Modifier.fillMaxWidth().clickable(onClick = action).padding(horizontal = 8.dp), supportingContent = { Text(subtitle) }, leadingContent = { Icon(icon, null, tint = PianoPrimary) }, colors = ListItemDefaults.colors(containerColor = PianoSurface))
}

@Composable private fun LibrarySong(song: ImportedSong, open: () -> Unit, favorite: () -> Unit, rename: () -> Unit, delete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = open).testTag("song_item_" + song.id), shape = PianoShapes.medium, colors = CardDefaults.cardColors(PianoSurface), border = BorderStroke(1.dp, PianoOutline)) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = PianoPrimaryContainer, modifier = Modifier.size(44.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = PianoPrimary) } }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(song.noteCount.toString() + " nốt • " + song.trackCount + " track", style = MaterialTheme.typography.bodySmall, color = PianoTextSecondary)
                Spacer(Modifier.height(6.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (song.localFilePath != null || song.assets.any { it.type == SongAssetType.MIDI }) Pill("MIDI")
                    if (song.assets.any { it.type == SongAssetType.MUSICXML }) Pill("Bản nhạc")
                    if (song.assets.any { it.type == SongAssetType.REFERENCE_AUDIO }) Pill("Audio")
                }
            }
            IconButton(favorite, Modifier.testTag("fav_button_" + song.id)) { Icon(if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Yêu thích", tint = if (song.isFavorite) PianoError else PianoTextSecondary) }
            Box {
                IconButton({ menu = true }, Modifier.testTag("song_menu_" + song.id)) { Icon(Icons.Default.MoreVert, "Thao tác bài") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Đổi tên") }, { menu = false; rename() })
                    DropdownMenuItem({ Text("Xóa", color = PianoError) }, { menu = false; delete() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = PianoError) })
                }
            }
        }
    }
}
@Composable private fun Pill(text: String) { Surface(shape = PianoShapes.small, color = PianoPrimaryContainer) { Text(text, style = MaterialTheme.typography.labelSmall, color = PianoPrimary, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)) } }
@Composable private fun EmptyLibrary(title: String, text: String?) { Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.LibraryMusic, null, Modifier.size(44.dp), tint = PianoTextSecondary); Text(title, fontWeight = FontWeight.Bold); text?.let { Text(it, color = PianoTextSecondary) } } } }
@Composable private fun DialogError(message: String) { Text(message, color = PianoError, style = MaterialTheme.typography.bodySmall) }
@Composable private fun ErrorActions(message: String, retry: (() -> Unit)?, close: (() -> Unit)?) { Column { DialogError(message); Row { retry?.let { TextButton(it) { Text("Thử lại") } }; close?.let { TextButton(it) { Text("Đóng") } } } } }
@Composable private fun ErrorLine(message: String, retry: (() -> Unit)?, close: (() -> Unit)?) { Surface(color = PianoError.copy(alpha = .12f), modifier = Modifier.fillMaxWidth().padding(16.dp)) { Column(Modifier.padding(12.dp)) { ErrorActions(message, retry, close) } } }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun SongDetail(state: SongPreparationState, dismiss: () -> Unit, favorite: () -> Unit, rename: () -> Unit, delete: () -> Unit, toggleTrack: (Int) -> Unit, setHand: (Int, String) -> Unit, mode: (PracticeMode) -> Unit, bpm: (Int) -> Unit, start: () -> Unit, retry: () -> Unit) {
    val song = state.song
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = PianoSurface) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.navigationBarsPadding().testTag("song_detail_sheet")) {
            state.errorMessage?.let { message -> item { ErrorActions(message, if (state.isLoadingTracks) null else retry, null) } }
            item { Row(verticalAlignment = Alignment.Top) { Column(Modifier.weight(1f)) { Text(song.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(song.originalFileName, style = MaterialTheme.typography.bodySmall, color = PianoTextSecondary) }; IconButton(favorite) { Icon(if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Yêu thích") } } }
            item { Text(song.noteCount.toString() + " nốt • " + song.trackCount + " track • " + song.defaultBpm + " BPM", color = PianoTextSecondary) }
            item {
                Text("Tài nguyên", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    state.resources.forEach { resource ->
                        Resource(when (resource.type) { SongAssetType.MIDI -> "MIDI"; SongAssetType.MUSICXML -> "Bản nhạc (file MusicXML)"; SongAssetType.REFERENCE_AUDIO -> "Audio" }, resource.fileName, resource.isAvailable)
                    }
                }
            }
            item {
                Text("Track và tay", fontWeight = FontWeight.Bold)
                if (state.isLoadingTracks) CircularProgressIndicator(Modifier.size(24.dp))
                else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.tracks.forEach { track -> TrackRow(track, { toggleTrack(track.trackIndex) }, { hand -> setHand(track.trackIndex, hand) }) }
                }
            }
            item { Text("Chế độ luyện", fontWeight = FontWeight.Bold); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { FilterChip(state.selectedPracticeMode == PracticeMode.WAIT_FOR_NOTE, { mode(PracticeMode.WAIT_FOR_NOTE) }, { Text("Chờ đúng nốt") }); FilterChip(state.selectedPracticeMode == PracticeMode.RHYTHM, { mode(PracticeMode.RHYTHM) }, { Text("Theo nhịp") }) }; Text("Tốc độ: " + state.customBpm + " BPM"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ bpm(state.customBpm - 5) }) { Text("−5") }; OutlinedButton({ bpm(state.customBpm + 5) }) { Text("+5") } } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(rename, Modifier.weight(1f)) { Text("Đổi tên") }; OutlinedButton(delete, Modifier.weight(1f)) { Text("Xóa", color = PianoError) } } }
            item { Button(start, enabled = !state.isLoadingTracks && !state.isSaving && state.tracks.any { it.isSelectedForPractice }, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("start_song_practice_button")) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (state.isSaving) "Đang lưu…" else "Bắt đầu luyện") } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun TrackRow(track: SongTrackEntity, toggle: () -> Unit, setHand: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(PianoSurfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = track.isSelectedForPractice, onCheckedChange = { toggle() }, modifier = Modifier.testTag("track_select_" + track.trackIndex))
                Column(Modifier.weight(1f)) {
                    Text(track.trackName.ifBlank { "Track " + (track.trackIndex + 1) }, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.noteCount.toString() + " nốt • " + track.channelSummary, style = MaterialTheme.typography.bodySmall, color = PianoTextSecondary)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("RIGHT" to "Tay phải", "LEFT" to "Tay trái", "BOTH" to "Cả hai", "IGNORE" to "Bỏ qua").forEach { option ->
                    FilterChip(
                        selected = track.assignedHand == option.first,
                        onClick = { setHand(option.first) },
                        enabled = track.isSelectedForPractice,
                        label = { Text(option.second) },
                        modifier = Modifier.testTag("track_hand_" + track.trackIndex + "_" + option.first)
                    )
                }
            }
        }
    }
}
@Composable private fun Resource(label: String, name: String, exists: Boolean) { Card(colors = CardDefaults.cardColors(PianoSurfaceVariant), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Description, null, tint = PianoPrimary); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(label, fontWeight = FontWeight.SemiBold); Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Text(if (exists) "Sẵn sàng" else "Thiếu file", color = if (exists) PianoPrimary else PianoError, style = MaterialTheme.typography.labelSmall) } } }
