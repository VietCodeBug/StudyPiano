package com.ian.pianotrainer.feature.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ian.pianotrainer.R
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
import com.ian.pianotrainer.core.ui.NoteBadge
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.HandMode

@Composable
fun PracticePlayerScreen(
    viewModel: PracticePlayerViewModel,
    onBackClick: () -> Unit,
    onNavigateToResult: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showQuitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.navigateToResult.collect { sessionId ->
            onNavigateToResult(sessionId)
        }
    }

    // When exercise notes finished, trigger auto finish
    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished && uiState.exerciseNotes.isNotEmpty()) {
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

    Scaffold(
        topBar = {
            PracticePlayerTopBar(
                title = uiState.title,
                bpm = uiState.bpm,
                currentBeat = uiState.currentBeat,
                isPaused = uiState.engineState.isPaused,
                onPauseToggle = viewModel::togglePause,
                onRestart = viewModel::restart,
                onClose = { showQuitDialog = true }
            )
        },
        containerColor = PianoBackground,
        modifier = modifier.testTag("practice_player_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Live Stats & Progress
            StatsHeader(
                correct = uiState.engineState.correctNotesCount,
                wrong = uiState.engineState.wrongNotesCount,
                streak = uiState.engineState.currentStreak,
                progress = if (uiState.exerciseNotes.isNotEmpty()) {
                    uiState.engineState.currentNoteIndex.toFloat() / uiState.exerciseNotes.size
                } else 0f
            )

            // 2. Note Stream / Visualizer Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (uiState.displayMode == DisplayMode.FALLING_NOTES) {
                    FallingNotesStream(
                        notes = uiState.exerciseNotes,
                        currentIndex = uiState.engineState.currentNoteIndex,
                        namingMode = uiState.userSettings.noteNamingMode
                    )
                } else {
                    SheetMusicDisplay(
                        currentNote = uiState.engineState.currentExpectedNote,
                        namingMode = uiState.userSettings.noteNamingMode
                    )
                }
            }

            // 3. Virtual Piano Keyboard
            val targetHighlight = uiState.engineState.currentExpectedNote?.let { note ->
                listOf(
                    KeyHighlight(
                        midiNote = note.midiNote,
                        color = PianoPrimary,
                        label = note.noteName,
                        fingerNumber = note.fingerNumber,
                        hand = note.hand
                    )
                )
            } ?: emptyList()

            PianoKeyboardView(
                onKeyPressed = viewModel::onVirtualKeyPressed,
                onKeyReleased = viewModel::onVirtualKeyReleased,
                activeNotes = uiState.activePressedNotes,
                targetNotes = targetHighlight,
                noteNamingMode = uiState.userSettings.noteNamingMode,
                initialOctaveOffset = if (uiState.handMode == HandMode.LEFT) 2 else 3,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun PracticePlayerTopBar(
    title: String,
    bpm: Int,
    currentBeat: Int,
    isPaused: Boolean,
    onPauseToggle: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = PianoSurface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("practice_close_button")
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Dừng", tint = PianoTextPrimary)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PianoTextPrimary,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$bpm BPM",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = PianoPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Metronome 4-beat dots
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..4).forEach { beat ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = if (currentBeat == beat) PianoPrimary else PianoOutline,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
            }

            Row {
                IconButton(
                    onClick = onRestart,
                    modifier = Modifier.testTag("practice_restart_button")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Luyện lại", tint = PianoTextPrimary)
                }
                IconButton(
                    onClick = onPauseToggle,
                    modifier = Modifier.testTag("practice_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Tạm dừng",
                        tint = PianoPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsHeader(
    correct: Int,
    wrong: Int,
    streak: Int,
    progress: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = PianoSuccess, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "$correct", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = PianoSuccess)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = PianoError, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "$wrong", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = PianoError)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = PianoGold, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Streak: $streak", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = PianoGold)
            }
        }

        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = PianoPrimary,
            trackColor = PianoSurfaceVariant
        )
    }
}

@Composable
private fun FallingNotesStream(
    notes: List<ExerciseNote>,
    currentIndex: Int,
    namingMode: com.ian.pianotrainer.domain.model.NoteNamingMode
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .testTag("falling_notes_stream"),
        shape = PianoShapes.large,
        colors = CardDefaults.cardColors(containerColor = PianoSurface),
        border = BorderStroke(1.dp, PianoOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Hàng nốt cần đánh tiếp theo:",
                style = MaterialTheme.typography.bodySmall,
                color = PianoTextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(notes) { index, note ->
                    val isTarget = (index == currentIndex)
                    val isPast = (index < currentIndex)

                    Card(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(if (isTarget) 72.dp else 56.dp),
                        shape = PianoShapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isTarget -> PianoPrimary
                                isPast -> PianoSuccess.copy(alpha = 0.2f)
                                else -> PianoSurfaceVariant
                            }
                        ),
                        border = if (isTarget) BorderStroke(2.dp, PianoPrimaryDark) else null
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = NoteHelper.formatNoteName(note.midiNote, namingMode),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (isTarget) 18.sp else 14.sp
                                ),
                                color = when {
                                    isTarget -> Color.White
                                    isPast -> PianoSuccess
                                    else -> PianoTextPrimary
                                }
                            )
                            if (note.fingerNumber in 1..5) {
                                Text(
                                    text = "Ngón ${note.fingerNumber}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = if (isTarget) 11.sp else 9.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = if (isTarget) Color.White.copy(alpha = 0.9f) else PianoTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetMusicDisplay(
    currentNote: ExerciseNote?,
    namingMode: com.ian.pianotrainer.domain.model.NoteNamingMode
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .testTag("sheet_music_display"),
        shape = PianoShapes.large,
        colors = CardDefaults.cardColors(containerColor = PianoSurface),
        border = BorderStroke(1.dp, PianoOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Bản nhạc (Khuông nhạc mẫu)",
                style = MaterialTheme.typography.bodySmall,
                color = PianoTextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Sheet lines visual simulation
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(PianoSurfaceVariant, PianoShapes.medium)
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(5) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(PianoTextPrimary.copy(alpha = 0.4f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (currentNote != null) {
                NoteBadge(
                    midiNote = currentNote.midiNote,
                    fingerNumber = currentNote.fingerNumber,
                    handMode = currentNote.hand,
                    noteNamingMode = namingMode,
                    isHighlighted = true
                )
            }
        }
    }
}
