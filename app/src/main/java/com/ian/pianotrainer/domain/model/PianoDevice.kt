package com.ian.pianotrainer.domain.model

enum class DeviceType(val displayName: String) {
    BLUETOOTH_MIDI("Bluetooth LE MIDI"),
    USB_MIDI("USB MIDI"),
    MICROPHONE("Microphone Audio Beta")
}

data class PianoDevice(
    val id: String,
    val name: String,
    val type: DeviceType,
    val isSimulated: Boolean = false,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val signalStrength: Int = -55,
    val bluetoothAddress: String? = null,
    val hasBleMidiService: Boolean = true,
    val connectionError: String? = null,
    val portCount: Int = 1,
    val activePortIndex: Int = 0
)

