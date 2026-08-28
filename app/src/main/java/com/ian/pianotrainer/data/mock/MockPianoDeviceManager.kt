package com.ian.pianotrainer.data.mock

import com.ian.pianotrainer.domain.model.DeviceConnectionState
import com.ian.pianotrainer.domain.model.DeviceType
import com.ian.pianotrainer.domain.model.PianoDevice
import com.ian.pianotrainer.domain.service.PianoDeviceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockPianoDeviceManager : PianoDeviceManager {

    private val _connectionState = MutableStateFlow(DeviceConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<DeviceConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<PianoDevice?>(null)
    override val connectedDevice: StateFlow<PianoDevice?> = _connectedDevice.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<PianoDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<PianoDevice>> = _discoveredDevices.asStateFlow()

    private val mockDeviceLibrary = listOf(
        PianoDevice(
            id = "mock_vt02_ble",
            name = "Victor VT02 MIDI (Giả lập Bluetooth)",
            type = DeviceType.BLUETOOTH_MIDI,
            isSimulated = true,
            signalStrength = -48
        ),
        PianoDevice(
            id = "mock_vt02_usb",
            name = "Victor VT02 (Giả lập USB MIDI)",
            type = DeviceType.USB_MIDI,
            isSimulated = true,
            signalStrength = -30
        ),
        PianoDevice(
            id = "mock_generic_ble",
            name = "Studio Piano 88 (Giả lập)",
            type = DeviceType.BLUETOOTH_MIDI,
            isSimulated = true,
            signalStrength = -62
        )
    )

    override suspend fun startScan() {
        _connectionState.value = DeviceConnectionState.SCANNING
        _discoveredDevices.value = emptyList()
        delay(800)
        _discoveredDevices.value = mockDeviceLibrary
        _connectionState.value = if (_connectedDevice.value != null) DeviceConnectionState.CONNECTED else DeviceConnectionState.DISCONNECTED
    }

    override suspend fun stopScan() {
        if (_connectionState.value == DeviceConnectionState.SCANNING) {
            _connectionState.value = if (_connectedDevice.value != null) DeviceConnectionState.CONNECTED else DeviceConnectionState.DISCONNECTED
        }
    }

    override suspend fun connectDevice(device: PianoDevice) {
        _connectionState.value = DeviceConnectionState.CONNECTING
        delay(600)
        _connectedDevice.value = device.copy(isConnected = true)
        _connectionState.value = DeviceConnectionState.CONNECTED
    }

    override suspend fun disconnectDevice() {
        _connectedDevice.value = null
        _connectionState.value = DeviceConnectionState.DISCONNECTED
    }
}
