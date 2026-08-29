package com.ian.pianotrainer.core.music.midi

import com.ian.pianotrainer.domain.model.RecordedMidiEvent
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MidiRecordingWriter(
    private val ppq: Int = 480
) {

    fun write(
        events: List<RecordedMidiEvent>,
        bpm: Int = 80,
        trackName: String = "Piano Recording",
        targetFile: File
    ) {
        targetFile.parentFile?.mkdirs()
        FileOutputStream(targetFile).use { fos ->
            write(events, bpm, trackName, fos)
        }
    }

    fun write(
        events: List<RecordedMidiEvent>,
        bpm: Int = 80,
        trackName: String = "Piano Recording",
        outputStream: OutputStream
    ) {
        val safeBpm = bpm.coerceIn(20, 300)
        val microsecondsPerQuarterNote = 60_000_000 / safeBpm

        val trackData = ByteArrayOutputStream()

        // 1. Set Tempo Meta Event (Delta = 0)
        writeVariableLengthQuantity(0L, trackData)
        trackData.write(0xFF)
        trackData.write(0x51)
        trackData.write(0x03)
        trackData.write((microsecondsPerQuarterNote shr 16) and 0xFF)
        trackData.write((microsecondsPerQuarterNote shr 8) and 0xFF)
        trackData.write(microsecondsPerQuarterNote and 0xFF)

        // 2. Track Name Meta Event (Delta = 0)
        val nameBytes = trackName.toByteArray(Charsets.UTF_8)
        writeVariableLengthQuantity(0L, trackData)
        trackData.write(0xFF)
        trackData.write(0x03)
        writeVariableLengthQuantity(nameBytes.size.toLong(), trackData)
        trackData.write(nameBytes)

        // 3. Prepare, sort and normalize MIDI events
        val sortedEvents = mutableListOf<InternalMidiEvent>()
        val openNotes = mutableMapOf<Pair<Int, Int>, Long>() // (channel, note) -> startTick

        for (event in events) {
            val tick = (event.timestampMs * safeBpm * ppq) / 60_000L
            if (event.isControlChange) {
                sortedEvents.add(
                    InternalMidiEvent(
                        tick = tick,
                        priority = 1, // CC after Note-Off, before Note-On
                        statusByte = (0xB0 or (event.channel and 0x0F)),
                        data1 = event.controlNumber and 0x7F,
                        data2 = event.controlValue and 0x7F
                    )
                )
            } else if (event.isNoteOn && event.velocity > 0) {
                openNotes[Pair(event.channel, event.note)] = tick
                sortedEvents.add(
                    InternalMidiEvent(
                        tick = tick,
                        priority = 2, // Note-On
                        statusByte = (0x90 or (event.channel and 0x0F)),
                        data1 = event.note and 0x7F,
                        data2 = event.velocity and 0x7F
                    )
                )
            } else {
                openNotes.remove(Pair(event.channel, event.note))
                sortedEvents.add(
                    InternalMidiEvent(
                        tick = tick,
                        priority = 0, // Note-Off has highest priority on identical tick
                        statusByte = (0x80 or (event.channel and 0x0F)),
                        data1 = event.note and 0x7F,
                        data2 = 0
                    )
                )
            }
        }

        // Close any dangling open notes at the last tick
        val maxTick = sortedEvents.maxOfOrNull { it.tick } ?: 0L
        val closeTick = maxTick + ppq / 2 // give at least eighth note duration
        for ((channelNote, _) in openNotes) {
            sortedEvents.add(
                InternalMidiEvent(
                    tick = closeTick,
                    priority = 0,
                    statusByte = (0x80 or (channelNote.first and 0x0F)),
                    data1 = channelNote.second and 0x7F,
                    data2 = 0
                )
            )
        }

        // Sort by tick ascending, then by priority (Note-Off first)
        sortedEvents.sortWith(compareBy<InternalMidiEvent> { it.tick }.thenBy { it.priority })

        // Write events with delta ticks
        var lastTick = 0L
        for (event in sortedEvents) {
            val delta = (event.tick - lastTick).coerceAtLeast(0L)
            writeVariableLengthQuantity(delta, trackData)
            trackData.write(event.statusByte)
            trackData.write(event.data1)
            trackData.write(event.data2)
            lastTick = event.tick
        }

        // 4. End of Track Meta Event (Delta = 0)
        writeVariableLengthQuantity(0L, trackData)
        trackData.write(0xFF)
        trackData.write(0x2F)
        trackData.write(0x00)

        val trackBytes = trackData.toByteArray()

        // 5. Output Header Chunk: MThd
        outputStream.write("MThd".toByteArray(Charsets.US_ASCII))
        outputStream.write(byteArrayOf(0x00, 0x00, 0x00, 0x06)) // Header length = 6
        outputStream.write(byteArrayOf(0x00, 0x00)) // Format 0 (single track)
        outputStream.write(byteArrayOf(0x00, 0x01)) // Track count = 1
        outputStream.write((ppq shr 8) and 0xFF)
        outputStream.write(ppq and 0xFF)

        // 6. Output Track Chunk: MTrk
        outputStream.write("MTrk".toByteArray(Charsets.US_ASCII))
        val len = trackBytes.size
        outputStream.write((len shr 24) and 0xFF)
        outputStream.write((len shr 16) and 0xFF)
        outputStream.write((len shr 8) and 0xFF)
        outputStream.write(len and 0xFF)
        outputStream.write(trackBytes)
        outputStream.flush()
    }

    private fun writeVariableLengthQuantity(value: Long, output: OutputStream) {
        var v = value
        var buffer = v and 0x7F
        while (v shr 7 > 0) {
            v = v shr 7
            buffer = buffer shl 8
            buffer = buffer or ((v and 0x7F) or 0x80)
        }
        while (true) {
            output.write((buffer and 0xFF).toInt())
            if ((buffer and 0x80L) != 0L) {
                buffer = buffer shr 8
            } else {
                break
            }
        }
    }

    private data class InternalMidiEvent(
        val tick: Long,
        val priority: Int,
        val statusByte: Int,
        val data1: Int,
        val data2: Int
    )
}
