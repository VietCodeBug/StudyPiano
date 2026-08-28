package com.ian.pianotrainer.feature.freeplay

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ian.pianotrainer.R
import com.ian.pianotrainer.core.designsystem.PianoBackground
import com.ian.pianotrainer.core.designsystem.PianoOutline
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoPrimaryContainer
import com.ian.pianotrainer.core.designsystem.PianoPrimaryDark
import com.ian.pianotrainer.core.designsystem.PianoShapes
import com.ian.pianotrainer.core.designsystem.PianoSurface
import com.ian.pianotrainer.core.designsystem.PianoSurfaceVariant
import com.ian.pianotrainer.core.designsystem.PianoTextPrimary
import com.ian.pianotrainer.core.designsystem.PianoTextSecondary
import com.ian.pianotrainer.core.music.NoteHelper
import com.ian.pianotrainer.core.ui.AppTopBar
import com.ian.pianotrainer.core.ui.NoteBadge
import com.ian.pianotrainer.core.ui.TempoControl
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.feature.practice.PianoKeyboardView

@Composable
fun FreePlayScreen(
    viewModel: FreePlayViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_free_play),
                onBackClick = onBackClick
            )
        },
        containerColor = PianoBackground,
        modifier = modifier.testTag("free_play_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Upper section: Current Note Display & Metronome
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Note Display
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = PianoShapes.large,
                    colors = CardDefaults.cardColors(containerColor = PianoSurface),
                    border = BorderStroke(1.dp, PianoOutline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Nốt đang bấm:",
                            style = MaterialTheme.typography.bodySmall,
                            color = PianoTextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        if (uiState.lastPressedNote != null) {
                            val cde = NoteHelper.formatNoteName(uiState.lastPressedNote!!, NoteNamingMode.CDE)
                            val doremi = NoteHelper.formatNoteName(uiState.lastPressedNote!!, NoteNamingMode.DOREMI)

                            Text(
                                text = "$cde • $doremi",
                                style = MaterialTheme.typography.headlineLarge,
                                color = PianoPrimaryDark
                            )
                        } else {
                            Text(
                                text = "Bấm một phím bất kỳ",
                                style = MaterialTheme.typography.titleMedium,
                                color = PianoTextSecondary
                            )
                        }
                    }
                }

                // Metronome & Settings Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = PianoShapes.medium,
                    colors = CardDefaults.cardColors(containerColor = PianoSurface),
                    border = BorderStroke(1.dp, PianoOutline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AvTimer,
                                    contentDescription = null,
                                    tint = PianoPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Máy đập nhịp (Metronome)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = PianoTextPrimary
                                    )
                                    if (uiState.isMetronomeRunning) {
                                        Text(
                                            text = "Nhịp ${uiState.currentBeat}/4",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PianoPrimary
                                        )
                                    }
                                }
                            }

                            Switch(
                                checked = uiState.isMetronomeRunning,
                                onCheckedChange = { viewModel.toggleMetronome() },
                                modifier = Modifier.testTag("freeplay_metronome_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PianoPrimary,
                                    checkedTrackColor = PianoPrimaryContainer
                                )
                            )
                        }

                        if (uiState.isMetronomeRunning) {
                            TempoControl(
                                bpm = uiState.bpm,
                                onBpmChanged = viewModel::setBpm
                            )
                        }
                    }
                }
            }

            // Bottom Full Virtual Keyboard
            PianoKeyboardView(
                onKeyPressed = viewModel::onVirtualKeyPressed,
                onKeyReleased = viewModel::onVirtualKeyReleased,
                activeNotes = uiState.activePressedNotes,
                noteNamingMode = uiState.userSettings.noteNamingMode,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}
