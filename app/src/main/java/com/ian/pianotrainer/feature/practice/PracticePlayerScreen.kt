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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ian.pianotrainer.R
import com.ian.pianotrainer.core.designsystem.PianoAccent
import com.ian.pianotrainer.core.designsystem.PianoBackground
import com.ian.pianotrainer.core.designsystem.PianoError
import com.ian.pianotrainer.core.designsystem.PianoGold
import com.ian.pianotrainer.core.designsystem.PianoOutline
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoPrimaryContainer
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

    val lifecycleOwner = LocalLifecycleOwner.current

    // Enforce landscape while in practice player
    ForceLandscapeWhileVisible()

    // Handle background lifecycle events: auto pause
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.onBackgroundPause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
            onSpeedChange = viewModel::setPlaybackSpeed,
            onBpmChange = viewModel::setBpm,
            onSeekChange = viewModel::seekTo,
            onLoopPointA = viewModel::setLoopPointA,
            onLoopPointB = viewModel::setLoopPointB,
            onClearLoop = viewModel::clearLoop,
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
            // 1. Hideable Compact Top Bar (~48dp)
            AnimatedVisibility(
                visible = uiState.isToolbarVisible,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                PracticeCompactTopBar(
                    title = uiState.title,
                    bpm = uiState.bpm,
                    speedMultiplier = uiState.speedMultiplier,
                    practiceMode = uiState.practiceMode,
                    currentPositionMs = uiState.engineState.currentPositionMs,
                    totalDurationMs = uiState.engineState.songDurationMs,
                    isPaused = uiState.engineState.isPaused,
                    isLooping = uiState.isLooping,
                    loopPointA = uiState.loopPointA,
                    loopPointB = uiState.loopPointB,
                    onPauseToggle = viewModel::togglePause,
                    onRestart = viewModel::restart,
                    onSpeedChange = viewModel::setPlaybackSpeed,
                    onSetLoopA = viewModel::setLoopPointA,
                    onSetLoopB = viewModel::setLoopPointB,
                    onClearLoop = viewModel::clearLoop,
                    onOpenSettings = { showSettingsSheet = true },
                    onToggleToolbar = viewModel::toggleToolbar,
                    onClose = { showQuitDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
            }

            // 2. Main Piano Roll / Visualizer Stage (~67% of remaining height)
            Box(
                modifier = Modifier
                    .weight(0.67f)
                    .fillMaxWidth()
            ) {
                FallingNotesCanvas(
                    notes = uiState.exerciseNotes,
                    currentPositionMs = uiState.engineState.currentPositionMs,
                    currentNoteIndex = uiState.engineState.currentNoteIndex,
                    namingMode = uiState.userSettings.noteNamingMode,
                    startOctave = uiState.startOctave,
                    rangeMode = uiState.rangeMode,
                    noteDisplaySize = uiState.noteDisplaySize,
                    lookAhead = uiState.visualLookAhead,
                    lastResult = uiState.engineState.lastEvaluatedResult,
                    lastPlayedMidi = uiState.engineState.lastPlayedNote,
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

            // 3. Piano Keyboard at bottom edge (~33% of height)
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
                keyHeight = 120.dp,
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
    speedMultiplier: Float,
    practiceMode: PracticeMode,
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPaused: Boolean,
    isLooping: Boolean,
    loopPointA: Long?,
    loopPointB: Long?,
    onPauseToggle: () -> Unit,
    onRestart: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSetLoopA: () -> Unit,
    onSetLoopB: () -> Unit,
    onClearLoop: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleToolbar: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSpeedMenu by remember { mutableStateOf(false) }

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
                modifier = Modifier.weight(0.30f, fill = false)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("practice_close_button")
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

            // Center: Timeline & Mode & Loop Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Current / Total Time
                val curSec = currentPositionMs / 1000
                val totSec = totalDurationMs / 1000
                Text(
                    text = "%02d:%02d / %02d:%02d".format(curSec / 60, curSec % 60, totSec / 60, totSec % 60),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = PianoTextPrimary
                )

                // Speed Selector
                Box {
                    Surface(
                        shape = PianoShapes.small,
                        color = PianoSurfaceVariant,
                        modifier = Modifier
                            .clip(PianoShapes.small)
                            .clickable { showSpeedMenu = true }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${speedMultiplier}x",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = PianoPrimary
                        )
                    }

                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false }
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("${speed}x", color = PianoTextPrimary) },
                                onClick = {
                                    onSpeedChange(speed)
                                    showSpeedMenu = false
                                }
                            )
                        }
                    }
                }

                // Mode Badge
                Surface(
                    shape = PianoShapes.small,
                    color = if (practiceMode == PracticeMode.WAIT_FOR_NOTE) PianoPrimaryContainer else PianoSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (practiceMode == PracticeMode.WAIT_FOR_NOTE) "Chờ nốt" else "Theo nhịp",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (practiceMode == PracticeMode.WAIT_FOR_NOTE) PianoPrimary else PianoTextSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Loop A-B quick buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Surface(
                        shape = PianoShapes.small,
                        color = if (loopPointA != null) PianoPrimaryContainer else PianoSurfaceVariant,
                        modifier = Modifier
                            .clip(PianoShapes.small)
                            .clickable { onSetLoopA() }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "A",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (loopPointA != null) PianoPrimary else PianoTextSecondary
                        )
                    }

                    Surface(
                        shape = PianoShapes.small,
                        color = if (loopPointB != null) PianoPrimaryContainer else PianoSurfaceVariant,
                        modifier = Modifier
                            .clip(PianoShapes.small)
                            .clickable { onSetLoopB() }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "B",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (loopPointB != null) PianoPrimary else PianoTextSecondary
                        )
                    }

                    if (isLooping || loopPointA != null || loopPointB != null) {
                        Surface(
                            shape = PianoShapes.small,
                            color = PianoSurfaceVariant,
                            modifier = Modifier
                                .clip(PianoShapes.small)
                                .clickable { onClearLoop() }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "✕",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PianoError
                            )
                        }
                    }
                }
            }

            // Right: Playback & Settings Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onRestart,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("practice_restart_button")
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
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("practice_pause_button")
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
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("practice_settings_button")
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
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("practice_fullscreen_button")
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
    onSpeedChange: (Float) -> Unit,
    onBpmChange: (Int) -> Unit,
    onSeekChange: (Long) -> Unit,
    onLoopPointA: () -> Unit,
    onLoopPointB: () -> Unit,
    onClearLoop: () -> Unit,
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
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng",
                        tint = PianoTextSecondary
                    )
                }
            }

            // Timeline Seek Slider
            val maxDur = uiState.engineState.songDurationMs.coerceAtLeast(1000L).toFloat()
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tua bài nhạc", style = MaterialTheme.typography.bodySmall, color = PianoTextSecondary)
                    val curSec = uiState.engineState.currentPositionMs / 1000
                    val totSec = uiState.engineState.songDurationMs / 1000
                    Text(
                        "%02d:%02d / %02d:%02d".format(curSec / 60, curSec % 60, totSec / 60, totSec % 60),
                        style = MaterialTheme.typography.labelSmall,
                        color = PianoPrimary
                    )
                }
                Slider(
                    value = uiState.engineState.currentPositionMs.toFloat().coerceIn(0f, maxDur),
                    onValueChange = { onSeekChange(it.toLong()) },
                    valueRange = 0f..maxDur,
                    colors = SliderDefaults.colors(
                        thumbColor = PianoPrimary,
                        activeTrackColor = PianoPrimary,
                        inactiveTrackColor = PianoOutline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Playback Speed
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tốc độ phát", style = MaterialTheme.typography.bodySmall, color = PianoTextSecondary)
                    Text("${uiState.speedMultiplier}x", style = MaterialTheme.typography.labelSmall, color = PianoPrimary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                        FilterChip(
                            selected = uiState.speedMultiplier == speed,
                            onClick = { onSpeedChange(speed) },
                            label = { Text("${speed}x") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PianoPrimaryContainer,
                                selectedLabelColor = PianoPrimary,
                                containerColor = PianoSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Hand Selection
            Column {
                Text("Luyện tay", style = MaterialTheme.typography.bodySmall, color = PianoTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        HandMode.RIGHT to "Tay phải",
                        HandMode.LEFT to "Tay trái",
                        HandMode.BOTH to "Cả 2 tay"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = uiState.handMode == mode,
                            onClick = { onHandModeChange(mode) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (mode == HandMode.LEFT) Color(0xFFF97316).copy(alpha = 0.2f) else PianoPrimaryContainer,
                                selectedLabelColor = if (mode == HandMode.LEFT) Color(0xFFF97316) else PianoPrimary,
                                containerColor = PianoSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Practice Mode
            Column {
                Text("Chế độ luyện", style = MaterialTheme.typography.bodySmall, color = PianoTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.practiceMode == PracticeMode.WAIT_FOR_NOTE,
                        onClick = { onPracticeModeChange(PracticeMode.WAIT_FOR_NOTE) },
                        label = { Text("Chờ đúng nốt") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PianoPrimaryContainer,
                            selectedLabelColor = PianoPrimary,
                            containerColor = PianoSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = uiState.practiceMode == PracticeMode.RHYTHM,
                        onClick = { onPracticeModeChange(PracticeMode.RHYTHM) },
                        label = { Text("Chạy theo nhịp") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PianoPrimaryContainer,
                            selectedLabelColor = PianoPrimary,
                            containerColor = PianoSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Visual Look-Ahead Time
            Column {
                Text("Tầm nhìn nốt rơi", style = MaterialTheme.typography.bodySmall, color = PianoTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VisualLookAhead.values().forEach { lookAhead ->
                        FilterChip(
                            selected = uiState.visualLookAhead == lookAhead,
                            onClick = { onLookAheadChange(lookAhead) },
                            label = { Text(lookAhead.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PianoPrimaryContainer,
                                selectedLabelColor = PianoPrimary,
                                containerColor = PianoSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Finish session button
            Surface(
                shape = PianoShapes.medium,
                color = PianoError.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, PianoError.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PianoShapes.medium)
                    .clickable { onFinishSession() }
                    .padding(vertical = 10.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Kết thúc & Lưu phiên tập",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = PianoError
                    )
                }
            }
        }
    }
}
