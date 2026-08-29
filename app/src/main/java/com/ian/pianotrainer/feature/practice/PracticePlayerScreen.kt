package com.ian.pianotrainer.feature.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ian.pianotrainer.R
import com.ian.pianotrainer.core.designsystem.PianoBackground
import com.ian.pianotrainer.core.designsystem.PianoOutline
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoPrimaryContainer
import com.ian.pianotrainer.core.designsystem.PianoShapes
import com.ian.pianotrainer.core.designsystem.PianoSuccess
import com.ian.pianotrainer.core.designsystem.PianoSurface
import com.ian.pianotrainer.core.designsystem.PianoSurfaceVariant
import com.ian.pianotrainer.core.designsystem.PianoTextPrimary
import com.ian.pianotrainer.core.designsystem.PianoTextSecondary
import com.ian.pianotrainer.core.ui.ConfirmationDialog
import com.ian.pianotrainer.core.ui.ForceLandscapeWhileVisible
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.KeyboardRangeMode
import com.ian.pianotrainer.domain.model.LearningSection
import com.ian.pianotrainer.domain.model.NoteDisplaySize
import com.ian.pianotrainer.domain.model.PlayerTransportMode
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
    var showDemoIndicator by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current

    ForceLandscapeWhileVisible()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
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

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished && uiState.exerciseNotes.isNotEmpty() && !uiState.isLooping && uiState.transportMode != PlayerTransportMode.DEMO) {
            viewModel.finishAndSaveSession()
        }
    }


    LaunchedEffect(uiState.transportMode) {
        if (uiState.transportMode == PlayerTransportMode.DEMO) {
            showDemoIndicator = true
            kotlinx.coroutines.delay(1200L)
            showDemoIndicator = false
        } else {
            showDemoIndicator = false
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
            onSectionSelect = viewModel::selectSection,
            onHandModeChange = viewModel::setHandMode,
            onPracticeModeChange = viewModel::setPracticeMode,
            onSpeedChange = viewModel::setPlaybackSpeed,
            onBpmChange = viewModel::setBpm,
            onSeekChange = viewModel::seekTo,
            onLoopPointA = viewModel::setLoopPointA,
            onLoopPointB = viewModel::setLoopPointB,
            onClearLoop = viewModel::clearLoop,
            onLoopToggle = viewModel::toggleLooping,
            onMetronomeToggle = viewModel::toggleMetronome,
            onToggleAppSound = viewModel::toggleAppSound,
            onShowNoteNamesChange = viewModel::setShowNoteNames,
            onEnableInteractionChange = viewModel::setEnableVirtualKeyInteraction,
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
            // 1. Compact Sleek Top Bar (<= 44dp, responsive for 640x360)
            AnimatedVisibility(
                visible = uiState.isToolbarVisible || uiState.engineState.isPaused || showSettingsSheet,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                PracticeCompactTopBar(
                    title = uiState.title,
                    sectionLabel = uiState.selectedSection?.label,
                    sectionStartMs = uiState.selectedSection?.startMs ?: 0L,
                    transportMode = uiState.transportMode,
                    currentPositionMs = if (uiState.transportMode == PlayerTransportMode.DEMO) uiState.demoPositionMs else uiState.engineState.currentPositionMs,
                    totalDurationMs = uiState.selectedSection?.endMs ?: uiState.engineState.songDurationMs,
                    isPaused = uiState.engineState.isPaused,
                    isAppSoundEnabled = uiState.isAppSoundEnabled,
                    onToggleDemoMode = viewModel::toggleDemoMode,
                    onPauseToggle = viewModel::togglePause,
                    onRestart = viewModel::restart,
                    onOpenSettings = { showSettingsSheet = true },
                    onClose = { showQuitDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                )
            }

            // 2. Main Piano Roll Stage
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        viewModel.showToolbarTemporarily()
                    }
            ) {
                val currentPosMs = if (uiState.transportMode == PlayerTransportMode.DEMO) {
                    uiState.demoPositionMs
                } else {
                    uiState.engineState.currentPositionMs
                }

                FallingNotesCanvas(
                    notes = uiState.exerciseNotes,
                    currentPositionMs = currentPosMs,
                    currentNoteIndex = uiState.engineState.currentNoteIndex,
                    namingMode = uiState.userSettings.noteNamingMode,
                    startOctave = uiState.startOctave,
                    rangeMode = uiState.rangeMode,
                    noteDisplaySize = uiState.noteDisplaySize,
                    lookAhead = uiState.visualLookAhead,
                    activeFeedback = uiState.engineState.activeFeedback,
                    expectedNotes = uiState.engineState.currentExpectedNotes,
                    showNoteNames = uiState.showNoteNames,
                    tempos = uiState.songPlaybackData?.tempos ?: emptyList(),
                    timeSignatures = uiState.songPlaybackData?.timeSignatures ?: emptyList(),
                    modifier = Modifier.fillMaxSize()
                )

                // Demo Banner indicator
                if (uiState.transportMode == PlayerTransportMode.DEMO && showDemoIndicator) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xDD0284C7),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Headphones, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("ĐANG NGHE MẪU (DEMO)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        }
                    }
                }

                // Pause HUD Indicator
                if (uiState.engineState.isPaused && !uiState.isCountInActive && uiState.transportMode != PlayerTransportMode.DEMO) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xCC0F172A),
                        border = BorderStroke(1.dp, PianoOutline),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clickable { viewModel.togglePause() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Tiếp tục", tint = PianoPrimary, modifier = Modifier.size(24.dp))
                            Text("Đang tạm dừng — Chạm để tiếp tục", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = PianoTextPrimary)
                        }
                    }
                }

                // Count-In Overlay
                if (uiState.isCountInActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xBB050B14)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "CHUẨN BỊ",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                                color = PianoPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${uiState.countInBeatsRemaining}",
                                style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, fontWeight = FontWeight.Black),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 3. Compact Reference Piano Keyboard at bottom edge (56dp <= 20% screen height)
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

            PracticeReferenceKeyboard(
                rangeMode = uiState.rangeMode,
                baseOctave = uiState.startOctave,
                activePressedNotes = uiState.activePressedNotes,
                targetNotes = targetHighlight,
                namingMode = uiState.userSettings.noteNamingMode,
                activeFeedback = uiState.engineState.activeFeedback,
                keyHeight = 56.dp,
                enableInteraction = uiState.enableVirtualKeyInteraction,
                onKeyPressed = viewModel::onVirtualKeyPressed,
                onKeyReleased = viewModel::onVirtualKeyReleased,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("practice_keyboard")
            )
        }

        // Floating restore button if toolbar hidden
        if (!uiState.isToolbarVisible && !uiState.engineState.isPaused && !showSettingsSheet) {
            Surface(
                shape = CircleShape,
                color = PianoSurface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, PianoOutline.copy(alpha = 0.5f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { viewModel.showToolbarTemporarily() }
                    .testTag("restore_toolbar_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Hiện thanh công cụ",
                        tint = PianoTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PracticeCompactTopBar(
    title: String,
    sectionLabel: String?,
    sectionStartMs: Long,
    transportMode: PlayerTransportMode,
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPaused: Boolean,
    isAppSoundEnabled: Boolean,
    onToggleDemoMode: () -> Unit,
    onPauseToggle: () -> Unit,
    onRestart: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xEE0B1120),
        modifier = modifier.testTag("practice_compact_top_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Group: Close Button & Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp).testTag("practice_close_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Thoát", tint = PianoTextSecondary, modifier = Modifier.size(20.dp))
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = PianoTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (sectionLabel != null) {
                        Text(
                            text = sectionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = PianoPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Center Group: Controls & Time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onRestart,
                    modifier = Modifier.size(32.dp).testTag("practice_restart_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Chơi lại từ đầu", tint = PianoTextSecondary, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = onPauseToggle,
                    modifier = Modifier.size(34.dp).testTag("practice_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Phát" else "Tạm dừng",
                        tint = PianoPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Demo toggle chip
                Surface(
                    shape = PianoShapes.small,
                    color = if (transportMode == PlayerTransportMode.DEMO) Color(0xFF0284C7) else PianoSurfaceVariant,
                    modifier = Modifier
                        .clip(PianoShapes.small)
                        .clickable { onToggleDemoMode() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Headphones,
                            contentDescription = "Nghe mẫu",
                            tint = if (transportMode == PlayerTransportMode.DEMO) Color.White else PianoTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (transportMode == PlayerTransportMode.DEMO) "Dừng mẫu" else "Nghe mẫu",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (transportMode == PlayerTransportMode.DEMO) Color.White else PianoTextSecondary
                        )
                    }
                }

                // Time Indicator
                val relativePositionMs = (currentPositionMs - sectionStartMs).coerceIn(0L, (totalDurationMs - sectionStartMs).coerceAtLeast(0L))
                val relativeDurationMs = (totalDurationMs - sectionStartMs).coerceAtLeast(0L)
                val curSec = relativePositionMs / 1000
                val totSec = relativeDurationMs / 1000
                Text(
                    text = "%02d:%02d / %02d:%02d".format(curSec / 60, curSec % 60, totSec / 60, totSec % 60),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = PianoTextSecondary
                )
            }

            // Right Group: Sound Icon & Settings Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isAppSoundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = if (isAppSoundEnabled) "Âm thanh app bật" else "Âm thanh app tắt (đàn ngoài)",
                    tint = if (isAppSoundEnabled) PianoPrimary else PianoTextSecondary,
                    modifier = Modifier.size(18.dp)
                )

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(32.dp).testTag("practice_settings_button")
                ) {
                    Icon(Icons.Default.Tune, contentDescription = "Cài đặt luyện tập", tint = PianoPrimary, modifier = Modifier.size(18.dp))
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
    onSectionSelect: (LearningSection?) -> Unit,
    onHandModeChange: (HandMode) -> Unit,
    onPracticeModeChange: (PracticeMode) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onBpmChange: (Int) -> Unit,
    onSeekChange: (Long) -> Unit,
    onLoopPointA: () -> Unit,
    onLoopPointB: () -> Unit,
    onClearLoop: () -> Unit,
    onLoopToggle: () -> Unit,
    onMetronomeToggle: () -> Unit,
    onToggleAppSound: () -> Unit,
    onShowNoteNamesChange: (Boolean) -> Unit,
    onEnableInteractionChange: (Boolean) -> Unit,
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
                Text("Tùy chỉnh phòng luyện", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PianoTextPrimary)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng", tint = PianoTextSecondary)
                }
            }

            // 1. Learning Section Selector
            if (uiState.sections.isNotEmpty()) {
                Column {
                    Text("Chọn đoạn luyện (Section)", style = MaterialTheme.typography.bodySmall, color = PianoTextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = uiState.selectedSection == null,
                            onClick = { onSectionSelect(null) },
                            label = { Text("Cả bài") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PianoPrimaryContainer,
                                selectedLabelColor = PianoPrimary,
                                containerColor = PianoSurfaceVariant
                            )
                        )
                        uiState.sections.take(4).forEach { sec ->
                            FilterChip(
                                selected = uiState.selectedSection?.id == sec.id,
                                onClick = { onSectionSelect(sec) },
                                label = { Text(sec.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PianoPrimaryContainer,
                                    selectedLabelColor = PianoPrimary,
                                    containerColor = PianoSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            // 2. Timeline Seek Slider
            val maxDur = (uiState.selectedSection?.endMs ?: uiState.engineState.songDurationMs).coerceAtLeast(1000L).toFloat()
            val minDur = (uiState.selectedSection?.startMs ?: 0L).toFloat()
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tua bài nhạc", style = MaterialTheme.typography.bodySmall, color = PianoTextSecondary)
                    val curSec = uiState.engineState.currentPositionMs / 1000
                    val totSec = maxDur.toLong() / 1000
                    Text("%02d:%02d / %02d:%02d".format(curSec / 60, curSec % 60, totSec / 60, totSec % 60), style = MaterialTheme.typography.labelSmall, color = PianoPrimary)
                }
                Slider(
                    value = uiState.engineState.currentPositionMs.toFloat().coerceIn(minDur, maxDur),
                    onValueChange = { onSeekChange(it.toLong()) },
                    valueRange = minDur..maxDur,
                    colors = SliderDefaults.colors(thumbColor = PianoPrimary, activeTrackColor = PianoPrimary, inactiveTrackColor = PianoOutline),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3. Playback Speed
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tốc độ phát", style = MaterialTheme.typography.bodySmall, color = PianoTextSecondary)
                    Text("${uiState.speedMultiplier}x", style = MaterialTheme.typography.labelSmall, color = PianoPrimary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

            // 4. Hand Selection
            Column {
                Text("Luyện tay", style = MaterialTheme.typography.bodySmall, color = PianoTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            // 5. Practice Mode
            Column {
                Text("Chế độ luyện", style = MaterialTheme.typography.bodySmall, color = PianoTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.practiceMode == PracticeMode.WAIT_FOR_NOTE,
                        onClick = { onPracticeModeChange(PracticeMode.WAIT_FOR_NOTE) },
                        label = { Text("Chờ đúng nốt (Wait)") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PianoPrimaryContainer, selectedLabelColor = PianoPrimary, containerColor = PianoSurfaceVariant),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = uiState.practiceMode == PracticeMode.RHYTHM,
                        onClick = { onPracticeModeChange(PracticeMode.RHYTHM) },
                        label = { Text("Theo nhịp (Rhythm)") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PianoPrimaryContainer, selectedLabelColor = PianoPrimary, containerColor = PianoSurfaceVariant),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 6. Loop Controls
            Column {
                Text("Lặp đoạn (Loop)", style = MaterialTheme.typography.bodySmall, color = PianoTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = PianoShapes.small,
                        color = if (uiState.loopPointA != null) PianoPrimaryContainer else PianoSurfaceVariant,
                        modifier = Modifier.weight(1f).clip(PianoShapes.small).clickable { onLoopPointA() }
                    ) {
                        Text(
                            text = if (uiState.loopPointA != null) "Điểm A: ${(uiState.loopPointA!! / 1000)}s" else "Đặt điểm A",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.loopPointA != null) PianoPrimary else PianoTextPrimary,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)
                        )
                    }
                    Surface(
                        shape = PianoShapes.small,
                        color = if (uiState.loopPointB != null) PianoPrimaryContainer else PianoSurfaceVariant,
                        modifier = Modifier.weight(1f).clip(PianoShapes.small).clickable { onLoopPointB() }
                    ) {
                        Text(
                            text = if (uiState.loopPointB != null) "Điểm B: ${(uiState.loopPointB!! / 1000)}s" else "Đặt điểm B",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.loopPointB != null) PianoPrimary else PianoTextPrimary,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)
                        )
                    }
                    Surface(
                        shape = PianoShapes.small,
                        color = if (uiState.isLooping) PianoSuccess.copy(alpha = 0.2f) else PianoSurfaceVariant,
                        modifier = Modifier.clip(PianoShapes.small).clickable { onLoopToggle() }
                    ) {
                        Icon(
                            Icons.Default.Loop,
                            contentDescription = "Bật/tắt lặp",
                            tint = if (uiState.isLooping) PianoSuccess else PianoTextSecondary,
                            modifier = Modifier.padding(8.dp).size(18.dp)
                        )
                    }
                }
            }

            // 7. Audio & Metronome Toggles
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Máy đập nhịp (Metronome)", style = MaterialTheme.typography.bodyMedium, color = PianoTextPrimary)
                    Switch(
                        checked = uiState.isMetronomeSoundEnabled,
                        onCheckedChange = { onMetronomeToggle() },
                        colors = SwitchDefaults.colors(checkedThumbColor = PianoPrimary, checkedTrackColor = PianoPrimaryContainer)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Âm thanh Piano trong App", style = MaterialTheme.typography.bodyMedium, color = PianoTextPrimary)
                    Switch(
                        checked = uiState.isAppSoundEnabled,
                        onCheckedChange = { onToggleAppSound() },
                        colors = SwitchDefaults.colors(checkedThumbColor = PianoPrimary, checkedTrackColor = PianoPrimaryContainer)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Gợi ý tên nốt trên thanh", style = MaterialTheme.typography.bodyMedium, color = PianoTextPrimary)
                    Switch(
                        checked = uiState.showNoteNames,
                        onCheckedChange = { onShowNoteNamesChange(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = PianoPrimary, checkedTrackColor = PianoPrimaryContainer)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Bật cảm ứng chạm phím ảo", style = MaterialTheme.typography.bodyMedium, color = PianoTextPrimary)
                    Switch(
                        checked = uiState.enableVirtualKeyInteraction,
                        onCheckedChange = { onEnableInteractionChange(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = PianoPrimary, checkedTrackColor = PianoPrimaryContainer)
                    )
                }
            }

            // 8. Exit & Save Session
            Surface(
                shape = PianoShapes.medium,
                color = Color(0xFFEF4444).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().clip(PianoShapes.medium).clickable { onFinishSession() }
            ) {
                Text(
                    text = "Kết thúc bài tập & Xem kết quả",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFEF4444),
                    modifier = Modifier.padding(14.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
