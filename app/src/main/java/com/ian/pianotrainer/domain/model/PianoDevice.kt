package com.ian.pianotrainer.domain.model

enum class DeviceType(val displayName: String) {
    BLUETOOTH_MIDI("Bluetooth MIDI"),
    USB_MIDI("USB MIDI")
}

data class PianoDevice(
    val id: String,
    val name: String,
    val type: DeviceType,
    val isSimulated: Boolean = true,
    val isConnected: Boolean = false,
    val signalStrength: Int = -55
)
