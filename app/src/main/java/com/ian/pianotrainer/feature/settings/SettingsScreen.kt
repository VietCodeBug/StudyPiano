package com.ian.pianotrainer.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.ian.pianotrainer.core.designsystem.PianoShapes
import com.ian.pianotrainer.core.designsystem.PianoSurface
import com.ian.pianotrainer.core.designsystem.PianoTextPrimary
import com.ian.pianotrainer.core.designsystem.PianoTextSecondary
import com.ian.pianotrainer.core.ui.AppTopBar
import com.ian.pianotrainer.core.ui.ConfirmationDialog
import com.ian.pianotrainer.core.ui.DangerButton
import com.ian.pianotrainer.core.ui.SectionHeader
import com.ian.pianotrainer.core.ui.TempoControl
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.NoteNamingMode

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_settings),
                onBackClick = onBackClick
            )
        },
        containerColor = PianoBackground,
        modifier = modifier.testTag("settings_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SectionHeader(title = stringResource(R.string.settings_note_naming))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_naming_settings_card"),
                    colors = CardDefaults.cardColors(containerColor = PianoSurface),
                    shape = PianoShapes.medium,
                    border = BorderStroke(1.dp, PianoOutline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilterChip(
                                selected = settings.noteNamingMode == NoteNamingMode.CDE,
                                onClick = { viewModel.setNoteNamingMode(NoteNamingMode.CDE) },
                                label = { Text("C – D – E") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chip_naming_cde"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PianoPrimaryContainer,
                                    selectedLabelColor = PianoPrimary
                                )
                            )
                            FilterChip(
                                selected = settings.noteNamingMode == NoteNamingMode.DOREMI,
                                onClick = { viewModel.setNoteNamingMode(NoteNamingMode.DOREMI) },
                                label = { Text("Đô – Rê – Mi") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chip_naming_doremi"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PianoPrimaryContainer,
                                    selectedLabelColor = PianoPrimary
                                )
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(title = stringResource(R.string.settings_default_display))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("display_mode_settings_card"),
                    colors = CardDefaults.cardColors(containerColor = PianoSurface),
                    shape = PianoShapes.medium,
                    border = BorderStroke(1.dp, PianoOutline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilterChip(
                                selected = settings.defaultDisplayMode == DisplayMode.FALLING_NOTES,
                                onClick = { viewModel.setDefaultDisplayMode(DisplayMode.FALLING_NOTES) },
                                label = { Text(stringResource(R.string.display_falling_notes)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chip_display_falling"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PianoPrimaryContainer,
                                    selectedLabelColor = PianoPrimary
                                )
                            )
                            FilterChip(
                                selected = settings.defaultDisplayMode == DisplayMode.SHEET_MUSIC,
                                onClick = { viewModel.setDefaultDisplayMode(DisplayMode.SHEET_MUSIC) },
                                label = { Text(stringResource(R.string.display_sheet_music)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chip_display_sheet"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PianoPrimaryContainer,
                                    selectedLabelColor = PianoPrimary
                                )
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "Âm thanh & Nhịp độ")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PianoSurface),
                    shape = PianoShapes.medium,
                    border = BorderStroke(1.dp, PianoOutline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = PianoPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.settings_virtual_sound),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = PianoTextPrimary
                                    )
                                    Text(
                                        text = "Phát âm sắc khi chạm phím đàn trên màn hình",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PianoTextSecondary
                                    )
                                }
                            }
                            Switch(
                                checked = settings.virtualPianoSoundEnabled,
                                onCheckedChange = viewModel::setSoundEnabled,
                                modifier = Modifier.testTag("virtual_sound_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PianoSurface,
                                    checkedTrackColor = PianoPrimary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.settings_metronome_volume),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = PianoTextPrimary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = PianoTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Slider(
                                value = settings.metronomeVolume,
                                onValueChange = viewModel::setMetronomeVolume,
                                valueRange = 0f..1f,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("metronome_volume_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = PianoPrimary,
                                    activeTrackColor = PianoPrimary,
                                    inactiveTrackColor = PianoOutline
                                )
                            )
                            Text(
                                text = "${(settings.metronomeVolume * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = PianoTextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TempoControl(
                            bpm = settings.defaultBpm,
                            onBpmChanged = viewModel::setDefaultBpm,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                SectionHeader(title = stringResource(R.string.settings_app_info))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PianoSurface),
                    shape = PianoShapes.medium,
                    border = BorderStroke(1.dp, PianoOutline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = PianoPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.app_version_info),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = PianoTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.app_developer_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = PianoTextSecondary
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                DangerButton(
                    text = stringResource(R.string.settings_reset_data),
                    onClick = { showResetDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_data_button")
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showResetDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.settings_reset_dialog_title),
            message = stringResource(R.string.settings_reset_dialog_msg),
            confirmText = stringResource(R.string.btn_confirm),
            dismissText = stringResource(R.string.btn_cancel),
            isDestructive = true,
            onConfirm = {
                viewModel.resetData()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false }
        )
    }
}
