package com.ian.pianotrainer.feature.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.core.midi.AndroidMidiDriver
import com.ian.pianotrainer.domain.model.DeviceConnectionState
import com.ian.pianotrainer.domain.model.PianoDevice
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
    val micConfidence: Float = 0f
)

class DeviceViewModel(
    private val deviceManager: PianoDeviceManager
) : ViewModel() {

    private val midiDriver = deviceManager as? AndroidMidiDriver

    val uiState: StateFlow<DeviceUiState> = combine(
        deviceManager.connectionState,
        deviceManager.connectedDevice,
        deviceManager.discoveredDevices,
        midiDriver?.isBleScanning ?: MutableStateFlow(false),
        midiDriver?.micPitchDetector?.isListening ?: MutableStateFlow(false),
        midiDriver?.micPitchDetector?.currentDetectedNote ?: MutableStateFlow(null),
        midiDriver?.micPitchDetector?.audioLevelRms ?: MutableStateFlow(0f),
        midiDriver?.micPitchDetector?.confidence ?: MutableStateFlow(0f)
    ) { args: Array<Any?> ->
        DeviceUiState(
            connectionState = args[0] as DeviceConnectionState,
            connectedDevice = args[1] as? PianoDevice,
            discoveredDevices = args[2] as List<PianoDevice>,
            isBleScanning = args[3] as Boolean,
            isMicListening = args[4] as Boolean,
            micDetectedNote = args[5] as? Int,
            micAudioLevelRms = args[6] as Float,
            micConfidence = args[7] as Float
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceUiState()
    )

    fun startScan(useMidiFilter: Boolean = true) {
        viewModelScope.launch {
            if (midiDriver != null) {
                midiDriver.startScanWithFilter(useMidiFilter)
            } else {
                deviceManager.startScan()
            }
        }
    }

    fun stopScan() {
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
