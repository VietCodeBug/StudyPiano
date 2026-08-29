package com.ian.pianotrainer.domain.model

enum class MidiInputSource(val displayName: String) {
    BLUETOOTH_LE("Bluetooth LE MIDI"),
    USB("USB MIDI"),
    MICROPHONE("Microphone (Beta)"),
    VIRTUAL_KEYBOARD("Bàn phím ảo")
}

data class MidiNoteEvent(
    val channel: Int = 0,
    val note: Int,
    val velocity: Int,
    val isNoteOn: Boolean,
    val timestampMs: Long = System.currentTimeMillis(),
    val inputSource: MidiInputSource = MidiInputSource.VIRTUAL_KEYBOARD,
    val deviceId: String? = null
)

data class MidiControlEvent(
    val channel: Int = 0,
    val controllerNumber: Int,
    val value: Int,
    val timestampMs: Long = System.currentTimeMillis(),
    val inputSource: MidiInputSource = MidiInputSource.VIRTUAL_KEYBOARD,
    val deviceId: String? = null
)

