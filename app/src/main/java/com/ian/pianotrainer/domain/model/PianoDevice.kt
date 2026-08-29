package com.ian.pianotrainer.domain.model

enum class DeviceType(val displayName: String) {
    BLUETOOTH_MIDI("Bluetooth LE MIDI"),
    USB_MIDI("USB MIDI"),
    MICROPHONE("Microphone Audio Beta")
}

enum class PianoDeviceCapability(val displayName: String) {
    SYSTEM_MIDI("MIDI Hệ thống"),
    BLE_MIDI_VERIFIED("BLE MIDI Chuẩn"),
    USB_MIDI("USB MIDI (OTG)"),
    BLUETOOTH_AUDIO_ONLY("Bluetooth Audio (Không có MIDI)"),
    UNKNOWN_BLUETOOTH("Bluetooth chưa xác định")
}

enum class ScanMode {
    MIDI_ONLY,
    EXTENDED
}

data class PianoDevice(
    val id: String,
    val name: String,
    val type: DeviceType,
    val capability: PianoDeviceCapability = PianoDeviceCapability.BLE_MIDI_VERIFIED,
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
