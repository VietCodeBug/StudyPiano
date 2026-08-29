package com.ian.pianotrainer.feature.freeplay

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ian.pianotrainer.core.designsystem.PianoAccent
import com.ian.pianotrainer.core.designsystem.PianoBackground
import com.ian.pianotrainer.core.designsystem.PianoError
import com.ian.pianotrainer.core.designsystem.PianoOutline
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoPrimaryDark
import com.ian.pianotrainer.core.designsystem.PianoSuccess
import com.ian.pianotrainer.core.designsystem.PianoSurface
import com.ian.pianotrainer.core.designsystem.PianoSurfaceVariant
import com.ian.pianotrainer.core.designsystem.PianoTextPrimary
import com.ian.pianotrainer.core.designsystem.PianoTextSecondary
import com.ian.pianotrainer.core.music.NoteHelper
import com.ian.pianotrainer.core.ui.ForceLandscapeWhileVisible
import com.ian.pianotrainer.domain.model.FreePlayRecording
import com.ian.pianotrainer.domain.model.KeyboardRangeMode
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.feature.practice.PianoKeyboardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreePlayScreen(
    viewModel: FreePlayViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showRecordingsSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var recordingTitleInput by remember { mutableStateOf("") }

    // Enforce landscape layout
    ForceLandscapeWhileVisible()

    // BackHandler when recording
    BackHandler(enabled = true) {
        if (uiState.isRecording) {
            viewModel.stopRecording(showDialog = true)
        } else {
            onBackClick()
        }
    }

    // Save recording dialog
    if (uiState.showSaveDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.discardRecording() },
            title = {
                Text(
                    text = "Lưu bản thu",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PianoTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Số nốt: ${uiState.recordedEventCount / 2} • Thời lượng: %02d:%02d".format(
                            uiState.recordingDurationMs / 60000,
                            (uiState.recordingDurationMs % 60000) / 1000
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = PianoTextSecondary
                    )
                    OutlinedTextField(
                        value = recordingTitleInput,
                        onValueChange = { recordingTitleInput = it },
                        label = { Text("Tên bản thu") },
                        placeholder = { Text("Ví dụ: Khúc ngẫu hứng 1") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PianoPrimary,
                            unfocusedBorderColor = PianoOutline
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("recording_title_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveRecording(recordingTitleInput)
                        recordingTitleInput = ""
                    },
                    modifier = Modifier.testTag("confirm_save_recording_button")
                ) {
                    Text("Lưu", fontWeight = FontWeight.Bold, color = PianoPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.discardRecording()
                        recordingTitleInput = ""
                    },
                    modifier = Modifier.testTag("discard_recording_button")
                ) {
                    Text("Hủy bỏ", color = PianoError)
                }
            },
            containerColor = PianoSurface
        )
    }

    // Saved recordings bottom sheet
    if (showRecordingsSheet) {
        SavedRecordingsBottomSheet(
            recordings = uiState.savedRecordings,
            playingId = uiState.playingRecordingId,
            isPlaying = uiState.isPlayingRecording,
            onPlay = viewModel::playRecording,
            onStop = viewModel::stopPlayback,
            onDelete = viewModel::deleteRecording,
            onDismiss = { showRecordingsSheet = false }
        )
    }

    // Free Play settings sheet
    if (showSettingsSheet) {
        FreePlaySettingsBottomSheet(
            uiState = uiState,
            onDismiss = { showSettingsSheet = false },
            onRangeModeChange = viewModel::setRangeMode,
            onOctaveChange = viewModel::setOctave,
            onBpmChange = viewModel::setBpm,
            onMetronomeToggle = viewModel::toggleMetronome,
            onAudioRecordingToggle = viewModel::setAudioRecordingEnabled
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PianoBackground)
            .testTag("free_play_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Control Bar (Compact 44dp)
            FreePlayTopBar(
                isRecording = uiState.isRecording,
                recordingDurationMs = uiState.recordingDurationMs,
                isAudioRecordingEnabled = uiState.isAudioRecordingEnabled,
                savedCount = uiState.savedRecordings.size,
                onToggleRecording = viewModel::toggleRecording,
                onOpenRecordings = { showRecordingsSheet = true },
                onOpenSettings = { showSettingsSheet = true },
                onBack = onBackClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            )

            // 2. Main Rising Notes Stage (~72% height)
            Box(
                modifier = Modifier
                    .weight(0.72f)
                    .fillMaxWidth()
            ) {
                RisingNotesCanvas(
                    trails = uiState.trails,
                    currentClockMs = uiState.currentClockMs,
                    activeNotes = uiState.activePressedNotes,
                    startOctave = uiState.startOctave,
                    rangeMode = uiState.rangeMode,
                    modifier = Modifier.fillMaxSize()
                )

                // Floating Active Note Banner
                if (uiState.lastPressedNote != null) {
                    val cde = NoteHelper.formatNoteName(uiState.lastPressedNote!!, NoteNamingMode.CDE)
                    val doremi = NoteHelper.formatNoteName(uiState.lastPressedNote!!, NoteNamingMode.DOREMI)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x990F172A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PianoOutline.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = "$cde • $doremi",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PianoAccent,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // 3. Piano Keyboard at bottom edge (~28% height)
            PianoKeyboardView(
                onKeyPressed = viewModel::onVirtualKeyPressed,
                onKeyReleased = viewModel::onVirtualKeyReleased,
                activeNotes = uiState.activePressedNotes,
                noteNamingMode = uiState.userSettings.noteNamingMode,
                initialOctaveOffset = uiState.startOctave,
                rangeMode = uiState.rangeMode,
                onOctaveChange = viewModel::setOctave,
                onRangeModeChange = viewModel::setRangeMode,
                showRangeBar = false,
                keyHeight = 115.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("free_play_keyboard")
            )
        }
    }
}

@Composable
fun FreePlayTopBar(
    isRecording: Boolean,
    recordingDurationMs: Long,
    isAudioRecordingEnabled: Boolean,
    savedCount: Int,
    onToggleRecording: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val recordBlinkAlpha by infiniteTransition.animateColor(
        initialValue = Color(0xFFEF4444),
        targetValue = Color(0xFF991B1B),
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        )
    )

    Surface(
        color = PianoSurface,
        tonalElevation = 3.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Back & Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp).testTag("freeplay_back_button")) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = PianoTextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Chơi tự do",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                    color = PianoTextPrimary
                )
            }

            // Center: Record Action Button with Timer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isRecording) Color(0x33EF4444) else PianoSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isRecording) Color(0xFFEF4444) else Color.Transparent
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onToggleRecording() }
                        .testTag("freeplay_record_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = if (isRecording) "Dừng ghi" else "Ghi âm/MIDI",
                            tint = if (isRecording) recordBlinkAlpha else Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        if (isRecording) {
                            val mins = recordingDurationMs / 60000
                            val secs = (recordingDurationMs % 60000) / 1000
                            Text(
                                text = "REC %02d:%02d".format(mins, secs),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFEF4444)
                            )
                        } else {
                            Text(
                                text = "Ghi âm",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PianoTextPrimary
                            )
                        }
                    }
                }
            }

            // Right: Saved Recordings & Settings
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Saved Library Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PianoSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenRecordings() }
                        .testTag("freeplay_library_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = "Bản thu đã lưu",
                            tint = PianoPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        if (savedCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$savedCount",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PianoPrimaryDark
                            )
                        }
                    }
                }

                // Settings Button
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp).testTag("freeplay_settings_button")) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Cài đặt phím",
                        tint = PianoTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedRecordingsBottomSheet(
    recordings: List<FreePlayRecording>,
    playingId: String?,
    isPlaying: Boolean,
    onPlay: (String) -> Unit,
    onStop: () -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
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
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bản thu đã lưu (${recordings.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PianoTextPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Đóng", tint = PianoTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (recordings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Chưa có bản thu nào. Bấm nút 'Ghi âm' để tạo bản nhạc đầu tiên!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PianoTextSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recordings, key = { it.id }) { rec ->
                        val isCurrentPlaying = isPlaying && playingId == rec.id

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurrentPlaying) Color(0x2238BDF8) else PianoSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isCurrentPlaying) PianoPrimary else Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (isCurrentPlaying) onStop() else onPlay(rec.id)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCurrentPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isCurrentPlaying) "Dừng" else "Phát",
                                            tint = PianoPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = rec.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = PianoTextPrimary
                                        )
                                        val mins = rec.durationMs / 60000
                                        val secs = (rec.durationMs % 60000) / 1000
                                        Text(
                                            text = "%02d:%02d • ${rec.noteCount} nốt ${if (rec.hasAudio) "• 🎙️ Audio" else ""}".format(mins, secs),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PianoTextSecondary
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onDelete(rec.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Xóa",
                                        tint = PianoError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreePlaySettingsBottomSheet(
    uiState: FreePlayUiState,
    onDismiss: () -> Unit,
    onRangeModeChange: (KeyboardRangeMode) -> Unit,
    onOctaveChange: (Int) -> Unit,
    onBpmChange: (Int) -> Unit,
    onMetronomeToggle: () -> Unit,
    onAudioRecordingToggle: (Boolean) -> Unit
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cài đặt phím & Âm thanh",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PianoTextPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Đóng", tint = PianoTextSecondary)
                }
            }

            // Keyboard Range
            Text(
                text = "Phạm vi bàn phím",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = PianoPrimaryDark
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KeyboardRangeMode.values().forEach { range ->
                    val isSelected = (uiState.rangeMode == range)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) PianoPrimary else PianoSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onRangeModeChange(range) }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (range) {
                                    KeyboardRangeMode.AUTO -> "Tự động"
                                    KeyboardRangeMode.TWO_OCTAVES -> "2 Quãng"
                                    KeyboardRangeMode.FOUR_OCTAVES -> "4 Quãng"
                                    KeyboardRangeMode.SIX_OCTAVES -> "6 Quãng"
                                    KeyboardRangeMode.FULL_88_KEYS -> "88 Phím"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) Color.White else PianoTextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Octave Position
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quãng 8 cơ sở: C${uiState.startOctave}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = PianoPrimaryDark
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (uiState.startOctave > 1) onOctaveChange(uiState.startOctave - 1) },
                        enabled = uiState.startOctave > 1,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Quãng thấp", tint = PianoPrimaryDark)
                    }
                    Text(
                        text = "C${uiState.startOctave}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = PianoTextPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(
                        onClick = { if (uiState.startOctave < 6) onOctaveChange(uiState.startOctave + 1) },
                        enabled = uiState.startOctave < 6,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Quãng cao", tint = PianoPrimaryDark)
                    }
                }
            }

            // Metronome & Audio Mic Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Metronome toggle
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (uiState.isMetronomeRunning) PianoPrimary else PianoSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onMetronomeToggle() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (uiState.isMetronomeRunning) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = null,
                            tint = if (uiState.isMetronomeRunning) Color.White else PianoTextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Metronome (${uiState.bpm})",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (uiState.isMetronomeRunning) Color.White else PianoTextPrimary
                        )
                    }
                }

                // Audio mic recording toggle
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (uiState.isAudioRecordingEnabled) PianoPrimary else PianoSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAudioRecordingToggle(!uiState.isAudioRecordingEnabled) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (uiState.isAudioRecordingEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = null,
                            tint = if (uiState.isAudioRecordingEnabled) Color.White else PianoTextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Thu Micro: ${if (uiState.isAudioRecordingEnabled) "Bật" else "Tắt"}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (uiState.isAudioRecordingEnabled) Color.White else PianoTextPrimary
                        )
                    }
                }
            }
        }
    }
}
