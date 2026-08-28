package com.ian.pianotrainer.domain.service

import com.ian.pianotrainer.domain.model.DeviceConnectionState
import com.ian.pianotrainer.domain.model.PianoDevice
import kotlinx.coroutines.flow.StateFlow

interface PianoDeviceManager {
    val connectionState: StateFlow<DeviceConnectionState>
    val connectedDevice: StateFlow<PianoDevice?>
    val discoveredDevices: StateFlow<List<PianoDevice>>

    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connectDevice(device: PianoDevice)
    suspend fun disconnectDevice()
}
