package com.ian.pianotrainer.feature.device

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.ian.pianotrainer.core.ui.AppTopBar
import com.ian.pianotrainer.core.ui.DangerButton
import com.ian.pianotrainer.core.ui.PianoOutlinedButton
import com.ian.pianotrainer.core.ui.PrimaryButton
import com.ian.pianotrainer.core.ui.SectionHeader
import com.ian.pianotrainer.domain.model.DeviceConnectionState
import com.ian.pianotrainer.domain.model.PianoDevice

@Composable
fun DeviceConnectionScreen(
    viewModel: DeviceViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.device_connection_title),
                onBackClick = onBackClick
            )
        },
        containerColor = PianoBackground,
        modifier = modifier.testTag("device_connection_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Victor VT02 Audio vs MIDI Notice Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = PianoShapes.medium,
                    colors = CardDefaults.cardColors(containerColor = PianoPrimaryContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, PianoPrimary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = PianoPrimaryDark,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Lưu ý quan trọng cho đàn Victor VT02",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = PianoTextPrimary
                            )
                            Text(
                                text = stringResource(R.string.device_victor_vt02_tip),
                                style = MaterialTheme.typography.bodySmall,
                                color = PianoTextSecondary
                            )
                        }
                    }
                }
            }

            // 2. Current Connection Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = PianoShapes.large,
                    colors = CardDefaults.cardColors(containerColor = PianoSurface),
                    border = BorderStroke(1.dp, PianoOutline)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Trạng thái thiết bị",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = PianoTextPrimary
                            )
                            Surface(
                                shape = CircleShape,
                                color = when (uiState.connectionState) {
                                    DeviceConnectionState.CONNECTED -> PianoSuccess.copy(alpha = 0.15f)
                                    DeviceConnectionState.CONNECTING, DeviceConnectionState.SCANNING -> PianoPrimary.copy(alpha = 0.15f)
                                    else -> PianoSurfaceVariant
                                }
                            ) {
                                Text(
                                    text = when (uiState.connectionState) {
                                        DeviceConnectionState.CONNECTED -> stringResource(R.string.device_connected)
                                        DeviceConnectionState.CONNECTING -> stringResource(R.string.device_connecting)
                                        DeviceConnectionState.SCANNING -> stringResource(R.string.device_status_scanning)
                                        DeviceConnectionState.DISCONNECTED -> stringResource(R.string.device_disconnected)
                                        DeviceConnectionState.ERROR -> stringResource(R.string.device_connection_failed)
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = when (uiState.connectionState) {
                                        DeviceConnectionState.CONNECTED -> PianoSuccess
                                        DeviceConnectionState.CONNECTING, DeviceConnectionState.SCANNING -> PianoPrimary
                                        else -> PianoTextSecondary
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        if (uiState.connectedDevice != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(PianoSurfaceVariant, PianoShapes.medium)
                                    .padding(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Piano,
                                    contentDescription = null,
                                    tint = PianoPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = uiState.connectedDevice?.name ?: "",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = PianoTextPrimary
                                    )
                                    Text(
                                        text = "Kiểu kết nối: ${uiState.connectedDevice?.type?.displayName} (Mô phỏng)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PianoTextSecondary
                                    )
                                }
                            }

                            DangerButton(
                                text = stringResource(R.string.device_disconnect_button),
                                onClick = viewModel::disconnectDevice,
                                tag = "disconnect_device_button"
                            )
                        } else {
                            Text(
                                text = "Hiện chưa có đàn piano nào được liên kết. Bấm quét để kết nối thiết bị mô phỏng.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PianoTextSecondary
                            )

                            if (uiState.connectionState == DeviceConnectionState.SCANNING) {
                                PianoOutlinedButton(
                                    text = "Đang quét thiết bị...",
                                    onClick = viewModel::stopScan,
                                    tag = "stop_scan_button"
                                )
                            } else {
                                PrimaryButton(
                                    text = stringResource(R.string.device_scan_button),
                                    onClick = viewModel::startScan,
                                    tag = "start_scan_button"
                                )
                            }
                        }
                    }
                }
            }

            // 3. Discovered Devices
            if (uiState.discoveredDevices.isNotEmpty()) {
                item {
                    SectionHeader(title = "Thiết bị tìm thấy (${uiState.discoveredDevices.size})")
                }

                items(uiState.discoveredDevices, key = { it.id }) { device ->
                    DeviceRowCard(
                        device = device,
                        onConnect = { viewModel.connectDevice(device) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceRowCard(
    device: PianoDevice,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PianoShapes.medium)
            .clickable { onConnect() }
            .testTag("device_item_${device.id}"),
        shape = PianoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = PianoSurface),
        border = BorderStroke(1.dp, PianoOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = PianoSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (device.type == com.ian.pianotrainer.domain.model.DeviceType.BLUETOOTH_MIDI) Icons.Default.Bluetooth else Icons.Default.Cable,
                            contentDescription = null,
                            tint = PianoPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = PianoTextPrimary
                    )
                    Text(
                        text = "${device.type.displayName} • Tín hiệu ${device.signalStrength} dBm",
                        style = MaterialTheme.typography.bodySmall,
                        color = PianoTextSecondary
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = PianoPrimaryContainer,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onConnect() }
            ) {
                Text(
                    text = "Kết nối",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = PianoPrimaryDark,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}
