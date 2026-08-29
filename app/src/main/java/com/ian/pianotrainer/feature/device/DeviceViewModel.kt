package com.ian.pianotrainer.feature.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.core.midi.AndroidMidiDriver
import com.ian.pianotrainer.core.midi.MidiErrorCodes
import com.ian.pianotrainer.domain.model.DeviceConnectionState
import com.ian.pianotrainer.domain.model.PianoDevice
import com.ian.pianotrainer.domain.model.ScanMode
import com.ian.pianotrainer.domain.service.PianoDeviceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeviceUiState(
    val connectionState: DeviceConnectionState = DeviceConnectionState.DISCONNECTED,
    val connectedDevice: PianoDevice? = null,
    val discoveredDevices: List<PianoDevice> = emptyList(),
    val isBleScanning: Boolean = false,
    val isMicListening: Boolean = false,
    val micDetectedNote: Int? = null,
    val micAudioLevelRms: Float = 0f,
    val micConfidence: Float = 0f,
    val pendingScanMode: ScanMode? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null
)

class DeviceViewModel(
    private val deviceManager: PianoDeviceManager
) : ViewModel() {

    private val midiDriver = deviceManager as? AndroidMidiDriver

    private val _pendingScanMode = MutableStateFlow<ScanMode?>(null)
    val pendingScanMode: StateFlow<ScanMode?> = _pendingScanMode

    private val _userErrorMessage = MutableStateFlow<String?>(null)
    private val _userErrorCode = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DeviceUiState> = combine(
        deviceManager.connectionState,
        deviceManager.connectedDevice,
        deviceManager.discoveredDevices,
        midiDriver?.isBleScanning ?: MutableStateFlow(false),
        midiDriver?.micPitchDetector?.isListening ?: MutableStateFlow(false),
        midiDriver?.micPitchDetector?.currentDetectedNote ?: MutableStateFlow(null),
        midiDriver?.micPitchDetector?.audioLevelRms ?: MutableStateFlow(0f),
        midiDriver?.micPitchDetector?.confidence ?: MutableStateFlow(0f),
        _pendingScanMode,
        midiDriver?.lastErrorCode ?: _userErrorCode,
        midiDriver?.lastErrorMessage ?: _userErrorMessage
    ) { args: Array<Any?> ->
        DeviceUiState(
            connectionState = args[0] as DeviceConnectionState,
            connectedDevice = args[1] as? PianoDevice,
            discoveredDevices = args[2] as List<PianoDevice>,
            isBleScanning = args[3] as Boolean,
            isMicListening = args[4] as Boolean,
            micDetectedNote = args[5] as? Int,
            micAudioLevelRms = args[6] as Float,
            micConfidence = args[7] as Float,
            pendingScanMode = args[8] as? ScanMode,
            errorCode = args[9] as? String,
            errorMessage = args[10] as? String
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceUiState()
    )

    /**
     * Proper State Machine to request Bluetooth Scan:
     * 1. Check BT hardware support
     * 2. Check BT enabled
     * 3. Check permissions
     * 4. If missing permission -> set pendingScanMode & call requestPermissionLauncher
     * 5. When permission callback fires -> onPermissionResult(granted)
     */
    fun requestScan(
        mode: ScanMode,
        isBluetoothSupported: Boolean,
        isBluetoothEnabled: Boolean,
        hasPermission: Boolean,
        onLaunchPermissionRequest: () -> Unit
    ) {
        _userErrorCode.value = null
        _userErrorMessage.value = null

        if (!isBluetoothSupported) {
            _userErrorCode.value = MidiErrorCodes.BT_NOT_SUPPORTED
            _userErrorMessage.value = "Thiết bị không hỗ trợ phần cứng Bluetooth."
            return
        }

        if (!isBluetoothEnabled) {
            _userErrorCode.value = MidiErrorCodes.BT_DISABLED
            _userErrorMessage.value = "Bluetooth đang tắt. Hãy bật Bluetooth trong Cài đặt để quét tìm đàn."
            return
        }

        if (!hasPermission) {
            _pendingScanMode.value = mode
            onLaunchPermissionRequest()
            return
        }

        executeScan(mode)
    }

    fun onPermissionResult(allGranted: Boolean) {
        val mode = _pendingScanMode.value
        _pendingScanMode.value = null

        if (allGranted && mode != null) {
            executeScan(mode)
        } else if (!allGranted) {
            _userErrorCode.value = MidiErrorCodes.BT_PERMISSION_DENIED
            _userErrorMessage.value = "Cần cấp quyền Bluetooth để quét và kết nối với đàn MIDI."
        }
    }

    private fun executeScan(mode: ScanMode) {
        viewModelScope.launch {
            if (midiDriver != null) {
                midiDriver.startScanWithMode(mode)
            } else {
                deviceManager.startScan()
            }
        }
    }

    fun stopScan() {
        _pendingScanMode.value = null
        viewModelScope.launch {
            deviceManager.stopScan()
        }
    }

    fun connectDevice(device: PianoDevice) {
        viewModelScope.launch {
            deviceManager.connectDevice(device)
        }
    }

    fun disconnectDevice() {
        viewModelScope.launch {
            deviceManager.disconnectDevice()
        }
    }

    fun clearError() {
        _userErrorCode.value = null
        _userErrorMessage.value = null
    }

    fun toggleMicrophoneInput(enabled: Boolean): Boolean {
        return if (enabled) {
            midiDriver?.micPitchDetector?.startListening() ?: false
        } else {
            midiDriver?.micPitchDetector?.stopListening()
            true
        }
    }

    class Factory(
        private val deviceManager: PianoDeviceManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeviceViewModel(deviceManager) as T
        }
    }
}
