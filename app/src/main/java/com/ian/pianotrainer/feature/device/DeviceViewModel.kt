package com.ian.pianotrainer.feature.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.DeviceConnectionState
import com.ian.pianotrainer.domain.model.PianoDevice
import com.ian.pianotrainer.domain.service.PianoDeviceManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeviceUiState(
    val connectionState: DeviceConnectionState = DeviceConnectionState.DISCONNECTED,
    val connectedDevice: PianoDevice? = null,
    val discoveredDevices: List<PianoDevice> = emptyList()
)

class DeviceViewModel(
    private val deviceManager: PianoDeviceManager
) : ViewModel() {

    val uiState: StateFlow<DeviceUiState> = combine(
        deviceManager.connectionState,
        deviceManager.connectedDevice,
        deviceManager.discoveredDevices
    ) { state, connected, discovered ->
        DeviceUiState(
            connectionState = state,
            connectedDevice = connected,
            discoveredDevices = discovered
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceUiState()
    )

    fun startScan() {
        viewModelScope.launch {
            deviceManager.startScan()
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

    class Factory(
        private val deviceManager: PianoDeviceManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeviceViewModel(deviceManager) as T
        }
    }
}
