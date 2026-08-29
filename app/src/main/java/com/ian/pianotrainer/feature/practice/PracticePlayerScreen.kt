package com.ian.pianotrainer.feature.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ian.pianotrainer.R
import com.ian.pianotrainer.core.designsystem.PianoAccent
import com.ian.pianotrainer.core.designsystem.PianoBackground
import com.ian.pianotrainer.core.designsystem.PianoError
import com.ian.pianotrainer.core.designsystem.PianoGold
import com.ian.pianotrainer.core.designsystem.PianoOutline
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoPrimaryDark
import com.ian.pianotrainer.core.designsystem.PianoShapes
import com.ian.pianotrainer.core.designsystem.PianoSuccess
import com.ian.pianotrainer.core.designsystem.PianoSurface
import com.ian.pianotrainer.core.designsystem.PianoSurfaceVariant
import com.ian.pianotrainer.core.designsystem.PianoTextPrimary
import com.ian.pianotrainer.core.designsystem.PianoTextSecondary
import com.ian.pianotrainer.core.music.NoteHelper
import com.ian.pianotrainer.core.ui.ConfirmationDialog
import com.ian.pianotrainer.core.ui.ForceLandscapeWhileVisible
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.KeyboardRangeMode
import com.ian.pianotrainer.domain.model.NoteDisplaySize
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.domain.model.NoteResultType
import com.ian.pianotrainer.domain.model.PracticeMode
import com.ian.pianotrainer.domain.model.VisualLookAhead

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticePlayerScreen(
    viewModel: PracticePlayerViewModel,
    onBackClick: () -> Unit,
    onNavigateToResult: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showQuitDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Enforce landscape while in practice player
    ForceLandscapeWhileVisible()

    LaunchedEffect(Unit) {
        viewModel.navigateToResult.collect { sessionId ->
            if (sessionId.isNotBlank()) {
                onNavigateToResult(sessionId)
            } else {
                onBackClick()
            }
        }
    }

    // Auto finish when notes complete and not looping
    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished && uiState.exerciseNotes.isNotEmpty() && !uiState.isLooping) {
            viewModel.finishAndSaveSession()
        }
    }

    if (showQuitDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.practice_quit_title),
            message = stringResource(R.string.practice_quit_message),
            confirmText = stringResource(R.string.practice_finish_button),
            dismissText = stringResource(R.string.practice_resume_button),
            onConfirm = {
                showQuitDialog = false
                viewModel.finishAndSaveSession()
            },
            onDismiss = { showQuitDialog = false }
        )
    }

    if (showSettingsSheet) {
        PracticeSettingsBottomSheet(
            uiState = uiState,
            onDismiss = { showSettingsSheet = false },
            onHandModeChange = viewModel::setHandMode,
            onPracticeModeChange = viewModel::setPracticeMode,
            onDisplayModeChange = viewModel::setDisplayMode,
            onRangeModeChange = viewModel::setRangeMode,
            onNoteSizeChange = viewModel::setNoteDisplaySize,
            onLookAheadChange = viewModel::setVisualLookAhead,
            onBpmChange = viewModel::setBpm,
            onLoopToggle = viewModel::toggleLooping,
            onMetronomeToggle = viewModel::toggleMetronome,
            onFinishSession = {
                showSettingsSheet = false
                showQuitDialog = true
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PianoBackground)
            .testTag("practice_player_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Hideable Compact Top Bar (~44dp)
            AnimatedVisibility(
                visible = uiState.isToolbarVisible,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                PracticeCompactTopBar(
                    title = uiState.title,
                    bpm = uiState.bpm,
                    currentBeat = uiState.currentBeat,
                    isPaused = uiState.engineState.isPaused,
                    elapsedSeconds = uiState.engineState.elapsedActiveSeconds,
                    correctCount = uiState.engineState.correctNotesCount,
                    wrongCount = uiState.engineState.wrongNotesCount,
                    streak = uiState.engineState.currentStreak,
                    onPauseToggle = viewModel::togglePause,
                    onRestart = viewModel::restart,
                    onOpenSettings = { showSettingsSheet = true },
                    onToggleToolbar = viewModel::toggleToolbar,
                    onClose = { showQuitDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                )
            }

            // 2. Main Piano Roll / Visualizer Stage (~72% of remaining height)
            Box(
                modifier = Modifier
                    .weight(0.72f)
                    .fillMaxWidth()
            ) {
                PracticeVisualizer(
                    exerciseNotes = uiState.exerciseNotes,
                    currentPositionMs = uiState.engineState.currentPositionMs,
                    currentNoteIndex = uiState.engineState.currentNoteIndex,
                    namingMode = uiState.userSettings.noteNamingMode,
                    startOctave = uiState.startOctave,
                    rangeMode = uiState.rangeMode,
                    noteDisplaySize = uiState.noteDisplaySize,
                    lookAhead = uiState.visualLookAhead,
                    lastResult = uiState.engineState.lastEvaluatedResult,
                    lastPlayedMidi = uiState.engineState.lastPlayedNote,
                    displayMode = uiState.displayMode,
                    currentExpectedNote = uiState.engineState.currentExpectedNote,
                    expectedNotes = uiState.engineState.currentExpectedNotes,
                    modifier = Modifier.fillMaxSize()
                )

                // Stats Overlay in Top Corner (Subtle, non-intrusive)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color(0x880F172A), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x33CBD5E1), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✓ ${uiState.engineState.correctNotesCount}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = PianoSuccess
                    )
                    Text(
                        text = "✗ ${uiState.engineState.wrongNotesCount}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = PianoError
                    )
                    if (uiState.engineState.currentStreak > 1) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = PianoGold,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${uiState.engineState.currentStreak}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PianoGold
                            )
                        }
                    }
                }
            }

            // 3. Piano Keyboard at bottom edge (~28% of height)
            val expectedChord = uiState.engineState.currentExpectedNotes.ifEmpty {
                listOfNotNull(uiState.engineState.currentExpectedNote)
            }
            val targetHighlight = expectedChord.map { note ->
                KeyHighlight(
                    midiNote = note.midiNote,
                    color = if (note.hand == HandMode.LEFT) Color(0xFFF97316) else PianoPrimary,
                    label = note.noteName,
                    fingerNumber = note.fingerNumber,
                    hand = note.hand
                )
            }

            PianoKeyboardView(
                onKeyPressed = viewModel::onVirtualKeyPressed,
                onKeyReleased = viewModel::onVirtualKeyReleased,
                activeNotes = uiState.activePressedNotes,
                targetNotes = targetHighlight,
                noteNamingMode = uiState.userSettings.noteNamingMode,
                initialOctaveOffset = uiState.startOctave,
                rangeMode = uiState.rangeMode,
                onOctaveChange = viewModel::setOctave,
                onRangeModeChange = viewModel::setRangeMode,
                upcomingNotes = uiState.exerciseNotes
                    .filter { it.startMs in uiState.engineState.currentPositionMs..(uiState.engineState.currentPositionMs + 5000L) }
                    .map { it.midiNote },
                showRangeBar = false,
                keyHeight = 115.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("practice_keyboard")
            )
        }

        // Floating mini toolbar restore button when toolbar is hidden
        if (!uiState.isToolbarVisible) {
            Surface(
                shape = CircleShape,
                color = PianoSurface.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, PianoOutline.copy(alpha = 0.5f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { viewModel.toggleToolbar() }
                    .testTag("restore_toolbar_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Hiện thanh công cụ",
                        tint = PianoTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PracticeCompactTopBar(
    title: String,
    bpm: Int,
    currentBeat: Int,
    isPaused: Boolean,
    elapsedSeconds: Long,
    correctCount: Int,
    wrongCount: Int,
    streak: Int,
    onPauseToggle: () -> Unit,
    onRestart: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleToolbar: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            // Left: Close & Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(0.35f, fill = false)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp).testTag("practice_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Thoát",
                        tint = PianoTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                    color = PianoTextPrimary,
                    maxLines = 1
                )
            }

            // Center: Time & BPM
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Time
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Thời gian",
                        tint = PianoPrimaryDark,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    val mins = elapsedSeconds / 60
                    val secs = elapsedSeconds % 60
                    Text(
                        text = "%02d:%02d".format(mins, secs),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = PianoTextPrimary
                    )
                }

                // BPM
                Text(
                    text = "$bpm BPM",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = PianoPrimaryDark
                )
            }

            // Right: Playback & Settings Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onRestart,
                    modifier = Modifier.size(32.dp).testTag("practice_restart_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Luyện lại",
                        tint = PianoTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onPauseToggle,
                    modifier = Modifier.size(32.dp).testTag("practice_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Tiếp tục" else "Tạm dừng",
                        tint = PianoPrimaryDark,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(32.dp).testTag("practice_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Tùy chỉnh luyện tập",
                        tint = PianoPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onToggleToolbar,
                    modifier = Modifier.size(32.dp).testTag("practice_fullscreen_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Toàn màn hình",
                        tint = PianoTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeSettingsBottomSheet(
    uiState: PracticePlayerUiState,
    onDismiss: () -> Unit,
    onHandModeChange: (HandMode) -> Unit,
    onPracticeModeChange: (PracticeMode) -> Unit,
    onDisplayModeChange: (DisplayMode) -> Unit,
    onRangeModeChange: (KeyboardRangeMode) -> Unit,
    onNoteSizeChange: (NoteDisplaySize) -> Unit,
    onLookAheadChange: (VisualLookAhead) -> Unit,
    onBpmChange: (Int) -> Unit,
    onLoopToggle: () -> Unit,
    onMetronomeToggle: () -> Unit,
    onFinishSession: () -> Unit
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tùy chỉnh phòng luyện",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PianoTextPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Đóng", tint = PianoTextSecondary)
                }
            }

            // 1. Keyboard Range Mode Selection
            Text(
                text = "Phạm vi hiển thị bàn phím",
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
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
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

            // 2. Hand Mode Selection
            Text(
                text = "Tay luyện tập",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = PianoPrimaryDark
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    HandMode.RIGHT to "Tay phải",
                    HandMode.LEFT to "Tay trái",
                    HandMode.BOTH to "Hai tay"
                ).forEach { (hand, label) ->
                    val isSelected = (uiState.handMode == hand)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) PianoPrimary else PianoSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onHandModeChange(hand) }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) Color.White else PianoTextPrimary
                            )
                        }
                    }
                }
            }

            // 3. Practice Mode Selection
            Text(
                text = "Chế độ luyện",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = PianoPrimaryDark
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    PracticeMode.WAIT_FOR_NOTE to "Chờ đúng nốt",
                    PracticeMode.RHYTHM to "Chạy theo nhịp"
                ).forEach { (mode, label) ->
                    val isSelected = (uiState.practiceMode == mode)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) PianoPrimary else PianoSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPracticeModeChange(mode) }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) Color.White else PianoTextPrimary
                            )
                        }
                    }
                }
            }

            // 4. Note Sizing & Look-ahead speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Note Size
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kích thước nốt",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = PianoPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        NoteDisplaySize.values().forEach { size ->
                            val isSelected = (uiState.noteDisplaySize == size)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) PianoPrimary else PianoSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onNoteSizeChange(size) }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = size.label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                        color = if (isSelected) Color.White else PianoTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Look-Ahead Speed
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tốc độ nhìn trước",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = PianoPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        VisualLookAhead.values().forEach { speed ->
                            val isSelected = (uiState.visualLookAhead == speed)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) PianoPrimary else PianoSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onLookAheadChange(speed) }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (speed) {
                                            VisualLookAhead.SLOW -> "Chậm"
                                            VisualLookAhead.MEDIUM -> "Vừa"
                                            VisualLookAhead.FAST -> "Nhanh"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                        color = if (isSelected) Color.White else PianoTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. BPM Adjustment & Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onBpmChange(uiState.bpm - 5) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Giảm BPM", tint = PianoPrimaryDark)
                    }
                    Text(
                        text = "${uiState.bpm} BPM",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PianoTextPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(
                        onClick = { onBpmChange(uiState.bpm + 5) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Tăng BPM", tint = PianoPrimaryDark)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (uiState.isLooping) PianoPrimary else PianoSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onLoopToggle() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Loop,
                                contentDescription = null,
                                tint = if (uiState.isLooping) Color.White else PianoTextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Lặp bài",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (uiState.isLooping) Color.White else PianoTextPrimary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (uiState.isMetronomeSoundEnabled) PianoPrimary else PianoSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onMetronomeToggle() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (uiState.isMetronomeSoundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = null,
                                tint = if (uiState.isMetronomeSoundEnabled) Color.White else PianoTextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Metronome",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (uiState.isMetronomeSoundEnabled) Color.White else PianoTextPrimary
                            )
                        }
                    }
                }
            }

            // Bottom Finish Action
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = PianoPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onFinishSession() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Kết thúc phiên luyện tập",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun PracticeVisualizer(
    exerciseNotes: List<ExerciseNote>,
    currentPositionMs: Long,
    currentNoteIndex: Int,
    namingMode: NoteNamingMode,
    startOctave: Int,
    rangeMode: KeyboardRangeMode,
    noteDisplaySize: NoteDisplaySize,
    lookAhead: VisualLookAhead,
    lastResult: NoteResultType?,
    lastPlayedMidi: Int?,
    displayMode: DisplayMode,
    currentExpectedNote: ExerciseNote?,
    expectedNotes: List<ExerciseNote> = emptyList(),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .testTag("practice_visualizer")
    ) {
        if (displayMode == DisplayMode.FALLING_NOTES) {
            FallingNotesCanvas(
                notes = exerciseNotes,
                currentPositionMs = currentPositionMs,
                currentNoteIndex = currentNoteIndex,
                namingMode = namingMode,
                startOctave = startOctave,
                rangeMode = rangeMode,
                noteDisplaySize = noteDisplaySize,
                lookAhead = lookAhead,
                lastResult = lastResult,
                lastPlayedMidi = lastPlayedMidi,
                expectedNotes = expectedNotes,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Note focus card (Active Note Mode)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A), PianoShapes.medium)
                    .border(1.dp, PianoOutline.copy(alpha = 0.5f), PianoShapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Nốt hiện tại",
                        style = MaterialTheme.typography.bodySmall,
                        color = PianoTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (currentExpectedNote != null) {
                        Text(
                            text = NoteHelper.formatNoteName(currentExpectedNote.midiNote, namingMode),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 48.sp
                            ),
                            color = if (currentExpectedNote.hand == HandMode.LEFT) Color(0xFFF97316) else PianoPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ngón số ${currentExpectedNote.fingerNumber}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = PianoTextPrimary
                        )
                    } else {
                        Text(
                            text = "Hoàn thành bài tập",
                            style = MaterialTheme.typography.titleMedium,
                            color = PianoSuccess
                        )
                    }
                }
            }
        }
    }
}
