package com.ian.pianotrainer.feature.diagnostics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.runtime.getValue
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
import com.ian.pianotrainer.core.designsystem.PianoPrimaryDark
import com.ian.pianotrainer.core.designsystem.PianoShapes
import com.ian.pianotrainer.core.designsystem.PianoSuccess
import com.ian.pianotrainer.core.designsystem.PianoSurface
import com.ian.pianotrainer.core.designsystem.PianoSurfaceVariant
import com.ian.pianotrainer.core.designsystem.PianoTextPrimary
import com.ian.pianotrainer.core.designsystem.PianoTextSecondary
import com.ian.pianotrainer.core.music.NoteHelper
import com.ian.pianotrainer.core.ui.AppTopBar
import com.ian.pianotrainer.core.ui.SectionHeader
import com.ian.pianotrainer.domain.model.MidiNoteEvent
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.feature.practice.PianoKeyboardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MidiDiagnosticScreen(
    viewModel: MidiDiagnosticViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val last = uiState.lastEvent

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_midi_diagnostic),
                onBackClick = onBackClick,
                actions = {
                    IconButton(
                        onClick = viewModel::clearLogs,
                        modifier = Modifier.testTag("clear_midi_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.diag_clear_logs),
                            tint = PianoTextSecondary
                        )
                    }
                }
            )
        },
        containerColor = PianoBackground,
        modifier = modifier.testTag("midi_diagnostic_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Live Gauge Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = PianoShapes.large,
                        colors = CardDefaults.cardColors(containerColor = PianoSurface),
                        border = BorderStroke(1.dp, PianoOutline)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Tín hiệu phím bấm gần nhất",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = PianoTextPrimary
                            )

                            if (last != null) {
                                val cdeName = NoteHelper.formatNoteName(last.note, NoteNamingMode.CDE)
                                val doremiName = NoteHelper.formatNoteName(last.note, NoteNamingMode.DOREMI)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "$cdeName ($doremiName)",
                                            style = MaterialTheme.typography.headlineLarge,
                                            color = PianoPrimaryDark
                                        )
                                        Text(
                                            text = "Mã MIDI: ${last.note} • ${if (last.isNoteOn) "Note On" else "Note Off"}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (last.isNoteOn) PianoSuccess else PianoTextSecondary
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = PianoPrimaryContainer,
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${last.velocity}",
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                color = PianoPrimaryDark
                                            )
                                        }
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = stringResource(R.string.diag_velocity),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PianoTextSecondary
                                        )
                                        Text(
                                            text = "${last.velocity} / 127",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = PianoPrimary
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { (last.velocity / 127f).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(CircleShape),
                                        color = PianoPrimary,
                                        trackColor = PianoSurfaceVariant
                                    )
                                }
                            } else {
                                Text(
                                    text = "Chưa có tín hiệu MIDI. Hãy bấm thử phím trên bàn phím ảo bên dưới.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PianoTextSecondary
                                )
                            }
                        }
                    }
                }

                // 2. Logs header
                item {
                    SectionHeader(title = stringResource(R.string.diag_event_log))
                }

                // 3. Event Log Rows
                items(uiState.eventLogs) { event ->
                    MidiLogRow(event = event)
                }
            }

            // Interactive Mini Keyboard for Diagnostic testing
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

@Composable
private fun MidiLogRow(event: MidiNoteEvent) {
    val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(event.timestampMs))
    val noteName = NoteHelper.formatNoteName(event.note, NoteNamingMode.CDE)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("midi_log_row_${event.timestampMs}"),
        shape = PianoShapes.small,
        colors = CardDefaults.cardColors(containerColor = PianoSurface),
        border = BorderStroke(1.dp, PianoOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (event.isNoteOn) PianoSuccess.copy(alpha = 0.15f) else PianoSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = if (event.isNoteOn) "ON" else "OFF",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = if (event.isNoteOn) PianoSuccess else PianoTextSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "$noteName (MIDI ${event.note})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = PianoTextPrimary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "V: ${event.velocity}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = PianoPrimary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = PianoTextSecondary
                )
            }
        }
    }
}
