package com.ian.pianotrainer.feature.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.ian.pianotrainer.domain.model.NoteNamingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }

    // SAF Launchers
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(context, uri, includeAudio = true)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreBackup(context, uri)
        }
    }

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
            // 1. Note Naming
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

            // 2. Practice Goal & Count-In
            item {
                SectionHeader(title = "Mục tiêu & Đếm nhịp trước khi tập")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PianoSurface),
                    shape = PianoShapes.medium,
                    border = BorderStroke(1.dp, PianoOutline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Daily Goal
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = PianoPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Mục tiêu hàng ngày",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = PianoTextPrimary
                                )
                            }
                            Text(
                                text = "${settings.dailyGoalMinutes} phút",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = PianoPrimary
                            )
                        }

                        Slider(
                            value = settings.dailyGoalMinutes.toFloat(),
                            onValueChange = { viewModel.setDailyGoalMinutes(it.toInt()) },
                            valueRange = 5f..120f,
                            steps = 22, // 5, 10, 15... 120
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = PianoPrimary,
                                activeTrackColor = PianoPrimary,
                                inactiveTrackColor = PianoOutline
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Count-In Selector
                        Text(
                            text = "Đếm nhịp trước khi bắt đầu bài (Count-In)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = PianoTextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "OFF" to "Tắt",
                                "1_MEASURE" to "1 Ô nhịp",
                                "2_MEASURES" to "2 Ô nhịp"
                            ).forEach { (code, label) ->
                                FilterChip(
                                    selected = settings.countInOption == code,
                                    onClick = { viewModel.setCountInOption(code) },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PianoPrimaryContainer,
                                        selectedLabelColor = PianoPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 3. Metronome & Tempo
            item {
                SectionHeader(title = "Metronome & Âm lượng")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PianoSurface),
                    shape = PianoShapes.medium,
                    border = BorderStroke(1.dp, PianoOutline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
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

                        Spacer(modifier = Modifier.height(14.dp))

                        TempoControl(
                            bpm = settings.defaultBpm,
                            onBpmChanged = viewModel::setDefaultBpm,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 4. MIDI Connection Settings
            item {
                SectionHeader(title = "Kết nối MIDI")
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
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.BluetoothSearching,
                                    contentDescription = null,
                                    tint = PianoPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Tự động kết nối lại MIDI",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = PianoTextPrimary
                                    )
                                    Text(
                                        text = "Tự động thử kết nối 1 lần với đàn đã kết nối gần nhất khi mở ứng dụng",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PianoTextSecondary
                                    )
                                }
                            }
                            Switch(
                                checked = settings.autoReconnectMidi,
                                onCheckedChange = viewModel::setAutoReconnectMidi,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PianoSurface,
                                    checkedTrackColor = PianoPrimary
                                )
                            )
                        }
                    }
                }
            }

            // 5. Backup & Restore (Gate F)
            item {
                SectionHeader(title = "Sao lưu & Khôi phục dữ liệu")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PianoSurface),
                    shape = PianoShapes.medium,
                    border = BorderStroke(1.dp, PianoOutline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Xuất toàn bộ bài hát, preset, lịch sử luyện tập và bản thu thành tệp .zip an toàn hoặc khôi phục từ tệp có sẵn.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PianoTextSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val dateStr = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
                                    backupLauncher.launch("piano-trainer-backup-$dateStr.zip")
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = PianoPrimary)
                            ) {
                                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sao lưu .zip")
                            }

                            OutlinedButton(
                                onClick = {
                                    restoreLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                                },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, PianoPrimary)
                            ) {
                                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp), tint = PianoPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Khôi phục", color = PianoPrimary)
                            }
                        }
                    }
                }
            }

            // 6. App Info
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
                                text = "Phiên bản 2.1.0 (Build 3)",
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

            // 7. Danger Zone: Reset All Data
            item {
                Spacer(modifier = Modifier.height(8.dp))
                DangerButton(
                    text = "Xóa toàn bộ dữ liệu người dùng",
                    onClick = { showResetDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_data_button")
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Reset confirmation dialog
    if (showResetDialog) {
        ConfirmationDialog(
            title = "Xóa toàn bộ dữ liệu?",
            message = "Hành động này sẽ xóa vĩnh viễn tất cả bài hát đã nhập, nhật ký luyện tập, preset và bản thu cá nhân. Bạn có chắc chắn muốn tiếp tục?",
            confirmText = "Xác nhận xóa",
            dismissText = "Hủy",
            isDestructive = true,
            onConfirm = {
                viewModel.resetData()
                showResetDialog = false
                Toast.makeText(context, "Đã xóa toàn bộ dữ liệu!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showResetDialog = false }
        )
    }

    // Backup State Dialog / Progress
    when (val state = backupState) {
        is BackupUiState.InProgress -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = PianoPrimary, modifier = Modifier.size(32.dp))
                        Text("Đang xử lý sao lưu / khôi phục dữ liệu...", color = PianoTextPrimary)
                    }
                },
                containerColor = PianoSurface
            )
        }
        is BackupUiState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissBackupState() },
                title = { Text("Thành công", fontWeight = FontWeight.Bold, color = PianoTextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(state.message, color = PianoTextPrimary)
                        Text("• Bài hát: ${state.manifest.songCount}", color = PianoTextSecondary)
                        Text("• Phiên luyện: ${state.manifest.sessionCount}", color = PianoTextSecondary)
                        Text("• Bản thu: ${state.manifest.recordingCount}", color = PianoTextSecondary)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissBackupState() }) {
                        Text("Đóng", color = PianoPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = PianoSurface
            )
        }
        is BackupUiState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissBackupState() },
                title = { Text("Lỗi thao tác", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                text = { Text(state.message, color = PianoTextPrimary) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissBackupState() }) {
                        Text("Đóng", color = PianoPrimary)
                    }
                },
                containerColor = PianoSurface
            )
        }
        BackupUiState.Idle -> {}
    }
}
