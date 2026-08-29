package com.ian.pianotrainer.feature.device

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
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
import com.ian.pianotrainer.core.designsystem.PianoWarning
import com.ian.pianotrainer.core.music.NoteHelper
import com.ian.pianotrainer.core.ui.AppTopBar
import com.ian.pianotrainer.core.ui.DangerButton
import com.ian.pianotrainer.core.ui.PianoOutlinedButton
import com.ian.pianotrainer.core.ui.PrimaryButton
import com.ian.pianotrainer.core.ui.SectionHeader
import com.ian.pianotrainer.domain.model.DeviceConnectionState
import com.ian.pianotrainer.domain.model.DeviceType
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.domain.model.PianoDevice

@Composable
fun DeviceConnectionScreen(
    viewModel: DeviceViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasBluetoothPermission by remember { mutableStateOf(false) }
    var hasMicPermission by remember { mutableStateOf(false) }
    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }

    val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        hasBluetoothPermission = allGranted
        if (allGranted) {
            permissionDeniedMessage = null
            viewModel.startScan(useMidiFilter = true)
        } else {
            permissionDeniedMessage = "Cần cấp quyền Bluetooth/Vị trí để tìm kiếm đàn MIDI qua Bluetooth LE."
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            viewModel.toggleMicrophoneInput(true)
        } else {
            permissionDeniedMessage = "Cần cấp quyền Microphone để sử dụng tính năng nhận diện nốt qua âm thanh."
        }
    }

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
            // 1. Permission Denied Warning Banner
            if (permissionDeniedMessage != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = PianoShapes.medium,
                        colors = CardDefaults.cardColors(containerColor = PianoError.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, PianoError.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = PianoError,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Yêu cầu quyền truy cập",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PianoError
                                )
                            }
                            Text(
                                text = permissionDeniedMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = PianoTextPrimary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PianoOutlinedButton(
                                    text = "Cài đặt ứng dụng",
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                PrimaryButton(
                                    text = "Thử lại",
                                    onClick = {
                                        blePermissionLauncher.launch(blePermissions)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Victor VT02 Audio vs MIDI Notice Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = PianoShapes.medium,
                    colors = CardDefaults.cardColors(containerColor = PianoPrimaryContainer.copy(alpha = 0.35f)),
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
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Phân biệt Bluetooth Audio & Bluetooth MIDI (Victor VT02)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = PianoTextPrimary
                            )
                            Text(
                                text = "• \"Piano Audio\" trong Cài đặt Bluetooth điện thoại chỉ truyền âm thanh ra loa đàn.\n" +
                                        "• Để nhận diện phím đàn, ứng dụng này kết nối trực tiếp cổng Bluetooth LE MIDI của đàn hoặc qua cáp USB MIDI / OTG.",
                                style = MaterialTheme.typography.bodySmall,
                                color = PianoTextSecondary
                            )
                        }
                    }
                }
            }

            // 3. Current Connection Status Card
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Trạng thái kết nối",
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
                                        DeviceConnectionState.CONNECTED -> "Đã kết nối MIDI"
                                        DeviceConnectionState.CONNECTING -> "Đang kết nối…"
                                        DeviceConnectionState.SCANNING -> "Đang quét…"
                                        DeviceConnectionState.DISCONNECTED -> "Chưa kết nối"
                                        DeviceConnectionState.ERROR -> "Lỗi kết nối"
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
                                        text = "Loại: ${uiState.connectedDevice?.type?.displayName} • Cổng: ${uiState.connectedDevice?.activePortIndex}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PianoTextSecondary
                                    )
                                }
                            }

                            DangerButton(
                                text = "Ngắt kết nối",
                                onClick = viewModel::disconnectDevice,
                                tag = "disconnect_device_button"
                            )
                        } else {
                            Text(
                                text = "Bật Bluetooth trên đàn piano điện và bấm quét để kết nối. Bạn cũng có thể cắm cáp USB MIDI / Type-C OTG trực tiếp.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PianoTextSecondary
                            )

                            if (uiState.isBleScanning) {
                                PianoOutlinedButton(
                                    text = "Dừng quét BLE",
                                    onClick = viewModel::stopScan,
                                    tag = "stop_scan_button"
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    PrimaryButton(
                                        text = "Quét BLE MIDI",
                                        onClick = {
                                            blePermissionLauncher.launch(blePermissions)
                                        },
                                        modifier = Modifier.weight(1f),
                                        tag = "start_scan_button"
                                    )
                                    PianoOutlinedButton(
                                        text = "Quét mở rộng",
                                        onClick = {
                                            blePermissionLauncher.launch(blePermissions)
                                            viewModel.startScan(useMidiFilter = false)
                                        },
                                        modifier = Modifier.weight(1f),
                                        tag = "extended_scan_button"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Microphone Beta Pitch Detection Section
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (uiState.isMicListening) Icons.Default.Mic else Icons.Default.MicOff,
                                    contentDescription = null,
                                    tint = if (uiState.isMicListening) PianoSuccess else PianoTextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Microphone nhận diện nốt (Beta)",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = PianoTextPrimary
                                    )
                                    Text(
                                        text = "Dành cho đàn piano cơ không có cổng MIDI",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PianoTextSecondary
                                    )
                                }
                            }

                            Switch(
                                checked = uiState.isMicListening,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        viewModel.toggleMicrophoneInput(false)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PianoPrimary,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = PianoSurfaceVariant
                                )
                            )
                        }

                        if (uiState.isMicListening) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(PianoPrimaryContainer.copy(alpha = 0.3f), PianoShapes.medium)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Mức âm lượng Mic:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PianoTextSecondary
                                    )
                                    Text(
                                        text = "${(uiState.micAudioLevelRms * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = PianoPrimaryDark
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { uiState.micAudioLevelRms.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = PianoPrimary,
                                    trackColor = PianoOutline
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Nốt nhận diện:",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = PianoTextPrimary
                                    )
                                    val note = uiState.micDetectedNote
                                    if (note != null) {
                                        val cde = NoteHelper.formatNoteName(note, NoteNamingMode.CDE)
                                        val doremi = NoteHelper.formatNoteName(note, NoteNamingMode.DOREMI)
                                        Text(
                                            text = "$cde ($doremi) • MIDI $note",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = PianoSuccess
                                        )
                                    } else {
                                        Text(
                                            text = "Đang lắng nghe...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = PianoTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Discovered Devices
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
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (device.type) {
                                DeviceType.BLUETOOTH_MIDI -> Icons.Default.Bluetooth
                                DeviceType.USB_MIDI -> Icons.Default.Cable
                                else -> Icons.Default.Mic
                            },
                            contentDescription = null,
                            tint = PianoPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = PianoTextPrimary
                        )
                        if (device.hasBleMidiService) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = PianoSuccess.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "BLE MIDI",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PianoSuccess,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${device.type.displayName}${if (device.bluetoothAddress != null) " • ${device.bluetoothAddress}" else ""} • ${device.signalStrength} dBm",
                        style = MaterialTheme.typography.bodySmall,
                        color = PianoTextSecondary
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = if (device.isConnected) PianoSuccess.copy(alpha = 0.15f) else PianoPrimaryContainer,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onConnect() }
            ) {
                Text(
                    text = if (device.isConnected) "Đã nối" else "Kết nối",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (device.isConnected) PianoSuccess else PianoPrimaryDark,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}
