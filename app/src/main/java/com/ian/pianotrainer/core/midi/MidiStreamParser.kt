package com.ian.pianotrainer.core.midi

import com.ian.pianotrainer.domain.model.MidiControlEvent
import com.ian.pianotrainer.domain.model.MidiInputSource
import com.ian.pianotrainer.domain.model.MidiNoteEvent

sealed interface ParsedMidiEvent {
    val rawBytes: ByteArray
    val timestampMs: Long

    data class Note(
        val noteEvent: MidiNoteEvent,
        override val rawBytes: ByteArray,
        override val timestampMs: Long
    ) : ParsedMidiEvent

    data class Control(
        val controlEvent: MidiControlEvent,
        override val rawBytes: ByteArray,
        override val timestampMs: Long
    ) : ParsedMidiEvent
}

class MidiStreamParser(
    private var inputSource: MidiInputSource = MidiInputSource.BLUETOOTH_LE,
    private var deviceId: String? = null
) {
    private var runningStatus = 0
    private val packetBuffer = IntArray(3)
    private var packetIndex = 0
    private var expectedBytes = 0

    fun updateContext(source: MidiInputSource, id: String?) {
        this.inputSource = source
        this.deviceId = id
    }

    fun parse(
        msg: ByteArray,
        offset: Int,
        count: Int,
        timestampMs: Long
    ): List<ParsedMidiEvent> {
        val results = mutableListOf<ParsedMidiEvent>()
        val safeOffset = offset.coerceIn(0, msg.size)
        val safeCount = count.coerceIn(0, msg.size - safeOffset)

        for (i in 0 until safeCount) {
            val b = msg[safeOffset + i].toInt() and 0xFF

            // Realtime bytes (0xF8..0xFF) can appear anywhere; do not interrupt running status
            if (b in 0xF8..0xFF) {
                continue
            }

            if (b >= 0x80) {
                if (b in 0x80..0xEF) {
                    runningStatus = b
                    packetIndex = 0
                    val type = b and 0xF0
                    expectedBytes = if (type == 0xC0 || type == 0xD0) 1 else 2
                } else {
                    // System Common / SysEx (0xF0..0xF7): reset running status
                    runningStatus = 0
                    packetIndex = 0
                    expectedBytes = 0
                }
            } else if (runningStatus != 0) {
                packetBuffer[packetIndex++] = b
                if (packetIndex >= expectedBytes) {
                    val status = runningStatus
                    val b1 = packetBuffer[0]
                    val b2 = if (expectedBytes > 1) packetBuffer[1] else 0
                    val type = status and 0xF0
                    val channel = status and 0x0F

                    val raw = if (expectedBytes == 1) {
                        byteArrayOf(status.toByte(), b1.toByte())
                    } else {
                        byteArrayOf(status.toByte(), b1.toByte(), b2.toByte())
                    }

                    when (type) {
                        0x80 -> { // Note Off
                            results.add(
                                ParsedMidiEvent.Note(
                                    noteEvent = MidiNoteEvent(
                                        channel = channel,
                                        note = b1,
                                        velocity = b2,
                                        isNoteOn = false,
                                        timestampMs = timestampMs,
                                        inputSource = inputSource,
                                        deviceId = deviceId
                                    ),
                                    rawBytes = raw,
                                    timestampMs = timestampMs
                                )
                            )
                        }
                        0x90 -> { // Note On (velocity 0 is Note Off)
                            val isNoteOn = (b2 > 0)
                            results.add(
                                ParsedMidiEvent.Note(
                                    noteEvent = MidiNoteEvent(
                                        channel = channel,
                                        note = b1,
                                        velocity = b2,
                                        isNoteOn = isNoteOn,
                                        timestampMs = timestampMs,
                                        inputSource = inputSource,
                                        deviceId = deviceId
                                    ),
                                    rawBytes = raw,
                                    timestampMs = timestampMs
                                )
                            )
                        }
                        0xB0 -> { // Control Change (e.g. Sustain CC64)
                            results.add(
                                ParsedMidiEvent.Control(
                                    controlEvent = MidiControlEvent(
                                        channel = channel,
                                        controllerNumber = b1,
                                        value = b2,
                                        timestampMs = timestampMs,
                                        inputSource = inputSource,
                                        deviceId = deviceId
                                    ),
                                    rawBytes = raw,
                                    timestampMs = timestampMs
                                )
                            )
                        }
                    }
                    packetIndex = 0
                }
            }
        }
        return results
    }

    fun reset() {
        runningStatus = 0
        packetIndex = 0
        expectedBytes = 0
    }
}
