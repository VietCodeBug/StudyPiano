package com.ian.pianotrainer.core.music.midi

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import kotlin.math.max

object MidiFileParser {

    @Throws(MidiParseException::class)
    fun parse(bytes: ByteArray): ParsedMidiFile {
        return parse(ByteArrayInputStream(bytes))
    }

    @Throws(MidiParseException::class)
    fun parse(inputStream: InputStream): ParsedMidiFile {
        val dis = DataInputStream(inputStream)

        // 1. Validate Header Chunk 'MThd'
        val headerTag = ByteArray(4)
        if (dis.read(headerTag) != 4 || String(headerTag, Charsets.US_ASCII) != "MThd") {
            throw MidiParseException("File không phải định dạng Standard MIDI hợp lệ (thiếu tiêu đề MThd).")
        }

        val headerLength = dis.readInt()
        if (headerLength < 6) {
            throw MidiParseException("Độ dài tiêu đề MIDI không hợp lệ ($headerLength bytes).")
        }

        val format = dis.readUnsignedShort()
        val numTracks = dis.readUnsignedShort()
        val division = dis.readUnsignedShort()

        // Skip any extra header bytes
        if (headerLength > 6) {
            dis.skipBytes(headerLength - 6)
        }

        if (format == 2) {
            throw MidiParseException("Định dạng MIDI Type 2 không được hỗ trợ. Vui lòng sử dụng Type 0 hoặc Type 1.")
        }

        if ((division and 0x8000) != 0) {
            throw MidiParseException("Định dạng phân chia thời gian SMPTE không được hỗ trợ.")
        }

        val ticksPerQuarterNote = division and 0x7FFF
        if (ticksPerQuarterNote <= 0) {
            throw MidiParseException("Giá trị nhịp phân giải (PPQ) không hợp lệ ($ticksPerQuarterNote).")
        }

        // Temporary data structures
        val rawTracks = mutableListOf<RawTrackData>()
        val rawTempoEvents = mutableListOf<RawTempoEvent>()
        val timeSignatureEvents = mutableListOf<ParsedTimeSignatureEvent>()

        // 2. Read each 'MTrk' chunk
        for (t in 0 until numTracks) {
            val chunkTag = ByteArray(4)
            val readTag = dis.read(chunkTag)
            if (readTag == -1) break
            if (readTag != 4 || String(chunkTag, Charsets.US_ASCII) != "MTrk") {
                // If it's another chunk (e.g. metadata chunk), read length and skip
                val len = dis.readInt()
                dis.skipBytes(len)
                continue
            }

            val trackLength = dis.readInt()
            if (trackLength < 0) {
                throw MidiParseException("Độ dài track $t không hợp lệ ($trackLength bytes).")
            }

            val trackBytes = ByteArray(trackLength)
            dis.readFully(trackBytes)

            val parsedTrack = parseTrackBytes(t, trackBytes)
            rawTracks.add(parsedTrack)
            rawTempoEvents.addAll(parsedTrack.tempoEvents)
            timeSignatureEvents.addAll(parsedTrack.timeSignatures)
        }

        if (rawTracks.isEmpty()) {
            throw MidiParseException("File MIDI không chứa track âm nhạc nào hợp lệ.")
        }

        // 3. Build Global Tempo Map
        // Ensure initial default tempo (500,000 microseconds/quarter note = 120 BPM) at tick 0 if not present
        val sortedTempos = rawTempoEvents.sortedBy { it.tick }.toMutableList()
        if (sortedTempos.isEmpty() || sortedTempos.first().tick > 0) {
            sortedTempos.add(0, RawTempoEvent(tick = 0L, mpqn = 500_000L))
        }

        val tempoSegments = buildTempoSegments(sortedTempos, ticksPerQuarterNote)

        // Default BPM based on initial tempo event
        val initialMpqn = tempoSegments.first().mpqn
        val defaultBpm = (60_000_000L / initialMpqn).toInt().coerceIn(30, 240)

        // 4. Convert all notes from ticks to milliseconds using tempoSegments
        var maxEndMs = 0L
        val processedTracks = mutableListOf<ParsedMidiTrack>()

        for (rawTrack in rawTracks) {
            val rawSorted = rawTrack.notes.sortedWith(compareBy({ it.startTick }, { it.midiNote }))
            val convertedNotes = mutableListOf<ParsedMidiNote>()

            for (rn in rawSorted) {
                val startMs = tickToMs(rn.startTick, tempoSegments, ticksPerQuarterNote)
                val endMs = tickToMs(rn.endTick, tempoSegments, ticksPerQuarterNote)
                val durationMs = max(50L, endMs - startMs) // minimum 50ms for audibility

                if (startMs + durationMs > maxEndMs) {
                    maxEndMs = startMs + durationMs
                }

                val chordId = "chord_${rn.trackIndex}_${rn.startTick}"

                convertedNotes.add(
                    ParsedMidiNote(
                        trackIndex = rn.trackIndex,
                        channel = rn.channel,
                        midiNote = rn.midiNote,
                        velocity = rn.velocity,
                        startTick = rn.startTick,
                        endTick = rn.endTick,
                        startMs = startMs,
                        durationMs = durationMs,
                        assignedHand = "BOTH",
                        chordId = chordId
                    )
                )
            }

            if (convertedNotes.isNotEmpty()) {
                val minPitch = convertedNotes.minOf { it.midiNote }
                val maxPitch = convertedNotes.maxOf { it.midiNote }

                processedTracks.add(
                    ParsedMidiTrack(
                        trackIndex = rawTrack.trackIndex,
                        trackName = rawTrack.trackName.ifBlank { "Track ${rawTrack.trackIndex + 1}" },
                        channelSummary = rawTrack.channels.joinToString(", ") { "Ch ${it + 1}" },
                        instrumentNumber = rawTrack.instrumentNumber,
                        noteCount = convertedNotes.size,
                        minMidiNote = minPitch,
                        maxMidiNote = maxPitch,
                        defaultHand = "BOTH",
                        notes = convertedNotes
                    )
                )
            }
        }

        // 5. Intelligent Hand Assignment Heuristics (no reflection)
        val finalTracks = if (processedTracks.size == 2) {
            val avg0 = processedTracks[0].notes.map { it.midiNote }.average()
            val avg1 = processedTracks[1].notes.map { it.midiNote }.average()
            if (avg0 >= avg1) {
                listOf(
                    applyHandToTrack(processedTracks[0], "RIGHT"),
                    applyHandToTrack(processedTracks[1], "LEFT")
                )
            } else {
                listOf(
                    applyHandToTrack(processedTracks[0], "LEFT"),
                    applyHandToTrack(processedTracks[1], "RIGHT")
                )
            }
        } else {
            processedTracks.map { track ->
                val avgPitch = track.notes.map { it.midiNote }.average()
                val hand = when {
                    avgPitch >= 60 -> "RIGHT"
                    avgPitch <= 55 -> "LEFT"
                    else -> "BOTH"
                }
                applyHandToTrack(track, hand)
            }
        }

        val tempoList = tempoSegments.map {
            ParsedTempoEvent(
                tick = it.startTick,
                startMs = it.startMs,
                microsecondsPerQuarterNote = it.mpqn,
                bpm = (60_000_000L / it.mpqn).toInt()
            )
        }

        return ParsedMidiFile(
            format = format,
            ticksPerQuarterNote = ticksPerQuarterNote,
            durationMs = maxEndMs,
            defaultBpm = defaultBpm,
            tracks = finalTracks,
            tempos = tempoList,
            timeSignatures = timeSignatureEvents
        )
    }

    private fun applyHandToTrack(track: ParsedMidiTrack, hand: String): ParsedMidiTrack {
        val updatedNotes = track.notes.map { it.copy(assignedHand = hand) }
        return track.copy(defaultHand = hand, notes = updatedNotes)
    }

    private fun parseTrackBytes(trackIndex: Int, bytes: ByteArray): RawTrackData {
        val stream = ByteArrayInputStream(bytes)
        var currentTick = 0L
        var runningStatus = 0
        var trackName = ""
        var instrumentNumber: Int? = null
        val channels = mutableSetOf<Int>()
        val tempoEvents = mutableListOf<RawTempoEvent>()
        val timeSignatures = mutableListOf<ParsedTimeSignatureEvent>()

        // Open notes tracking: key is (channel shl 8) or midiNote
        val openNotes = mutableMapOf<Int, ArrayDeque<RawOpenNote>>()
        val finishedNotes = mutableListOf<RawMidiNote>()

        // Sustain pedal state per channel (channel -> isPedalActive)
        val sustainPedalActive = BooleanArray(16) { false }
        // Notes waiting for pedal release: channel -> list of (rawNote, releaseTick)
        val pedalHeldNotes = mutableMapOf<Int, MutableList<RawMidiNote>>()

        while (stream.available() > 0) {
            val delta = readVlq(stream)
            currentTick += delta

            var status = stream.read()
            if (status == -1) break

            if (status < 0x80) {
                // Running status
                if (runningStatus == 0) {
                    throw MidiParseException("Lỗi running status ở track $trackIndex.")
                }
                // Push back the byte and use runningStatus
                val byte1 = status
                status = runningStatus
                handleMidiEvent(
                    status = status,
                    byte1 = byte1,
                    stream = stream,
                    currentTick = currentTick,
                    trackIndex = trackIndex,
                    channels = channels,
                    openNotes = openNotes,
                    finishedNotes = finishedNotes,
                    sustainPedalActive = sustainPedalActive,
                    pedalHeldNotes = pedalHeldNotes,
                    onInstrument = { instrumentNumber = it }
                )
            } else if (status in 0x80..0xEF) {
                runningStatus = status
                val byte1 = stream.read()
                handleMidiEvent(
                    status = status,
                    byte1 = byte1,
                    stream = stream,
                    currentTick = currentTick,
                    trackIndex = trackIndex,
                    channels = channels,
                    openNotes = openNotes,
                    finishedNotes = finishedNotes,
                    sustainPedalActive = sustainPedalActive,
                    pedalHeldNotes = pedalHeldNotes,
                    onInstrument = { instrumentNumber = it }
                )
            } else if (status == 0xFF) {
                // Meta Event (does not affect running status)
                val metaType = stream.read()
                val length = readVlq(stream).toInt()
                val data = ByteArray(length)
                if (length > 0) {
                    stream.read(data)
                }

                when (metaType) {
                    0x03 -> { // Track Name
                        trackName = String(data, Charsets.UTF_8).trim()
                    }
                    0x51 -> { // Set Tempo
                        if (data.size >= 3) {
                            val mpqn = ((data[0].toLong() and 0xFF) shl 16) or
                                    ((data[1].toLong() and 0xFF) shl 8) or
                                    (data[2].toLong() and 0xFF)
                            if (mpqn > 0) {
                                tempoEvents.add(RawTempoEvent(currentTick, mpqn))
                            }
                        }
                    }
                    0x58 -> { // Time Signature
                        if (data.size >= 2) {
                            val num = data[0].toInt() and 0xFF
                            val denomPower = data[1].toInt() and 0xFF
                            val denom = 1 shl denomPower
                            timeSignatures.add(ParsedTimeSignatureEvent(currentTick, num, denom))
                        }
                    }
                    0x2F -> { // End of Track
                        break
                    }
                }
            } else if (status == 0xF0 || status == 0xF7) {
                // SysEx
                val len = readVlq(stream).toInt()
                stream.skip(len.toLong())
            }
        }

        // Close any remaining unclosed open notes at currentTick
        for ((key, queue) in openNotes) {
            val channel = (key shr 8) and 0xFF
            val note = key and 0xFF
            while (queue.isNotEmpty()) {
                val open = queue.removeFirst()
                finishedNotes.add(
                    RawMidiNote(
                        trackIndex = trackIndex,
                        channel = channel,
                        midiNote = note,
                        velocity = open.velocity,
                        startTick = open.startTick,
                        endTick = max(open.startTick + 240, currentTick)
                    )
                )
            }
        }

        return RawTrackData(
            trackIndex = trackIndex,
            trackName = trackName,
            channels = channels.toList().sorted(),
            instrumentNumber = instrumentNumber,
            tempoEvents = tempoEvents,
            timeSignatures = timeSignatures,
            notes = finishedNotes
        )
    }

    private fun handleMidiEvent(
        status: Int,
        byte1: Int,
        stream: InputStream,
        currentTick: Long,
        trackIndex: Int,
        channels: MutableSet<Int>,
        openNotes: MutableMap<Int, ArrayDeque<RawOpenNote>>,
        finishedNotes: MutableList<RawMidiNote>,
        sustainPedalActive: BooleanArray,
        pedalHeldNotes: MutableMap<Int, MutableList<RawMidiNote>>,
        onInstrument: (Int) -> Unit
    ) {
        val eventType = status and 0xF0
        val channel = status and 0x0F
        channels.add(channel)

        when (eventType) {
            0x80 -> { // Note Off
                val note = byte1 and 0x7F
                val velocity = stream.read() and 0x7F
                closeNote(trackIndex, channel, note, currentTick, openNotes, finishedNotes, sustainPedalActive, pedalHeldNotes)
            }
            0x90 -> { // Note On
                val note = byte1 and 0x7F
                val velocity = stream.read() and 0x7F
                if (velocity == 0) {
                    closeNote(trackIndex, channel, note, currentTick, openNotes, finishedNotes, sustainPedalActive, pedalHeldNotes)
                } else {
                    val key = (channel shl 8) or note
                    val queue = openNotes.getOrPut(key) { ArrayDeque() }
                    queue.addLast(RawOpenNote(currentTick, velocity))
                }
            }
            0xA0 -> { // Polyphonic Aftertouch
                stream.read()
            }
            0xB0 -> { // Control Change
                val ccNumber = byte1 and 0x7F
                val ccValue = stream.read() and 0x7F

                if (ccNumber == 64) { // Sustain Pedal (Damper)
                    val isDown = ccValue >= 64
                    sustainPedalActive[channel] = isDown
                    if (!isDown) {
                        // Pedal released: release all held notes for this channel
                        val held = pedalHeldNotes.remove(channel)
                        if (held != null) {
                            for (rn in held) {
                                finishedNotes.add(rn.copy(endTick = currentTick))
                            }
                        }
                    }
                }
            }
            0xC0 -> { // Program Change
                onInstrument(byte1 and 0x7F)
            }
            0xD0 -> { // Channel Pressure
                // 1 data byte already in byte1
            }
            0xE0 -> { // Pitch Bend
                stream.read()
            }
        }
    }

    private fun closeNote(
        trackIndex: Int,
        channel: Int,
        note: Int,
        currentTick: Long,
        openNotes: MutableMap<Int, ArrayDeque<RawOpenNote>>,
        finishedNotes: MutableList<RawMidiNote>,
        sustainPedalActive: BooleanArray,
        pedalHeldNotes: MutableMap<Int, MutableList<RawMidiNote>>
    ) {
        val key = (channel shl 8) or note
        val queue = openNotes[key]
        if (queue != null && queue.isNotEmpty()) {
            val open = queue.removeFirst()
            val raw = RawMidiNote(
                trackIndex = trackIndex,
                channel = channel,
                midiNote = note,
                velocity = open.velocity,
                startTick = open.startTick,
                endTick = max(open.startTick + 1, currentTick)
            )

            if (sustainPedalActive[channel]) {
                pedalHeldNotes.getOrPut(channel) { mutableListOf() }.add(raw)
            } else {
                finishedNotes.add(raw)
            }
        }
    }

    private fun readVlq(stream: InputStream): Long {
        var value = 0L
        while (true) {
            val byte = stream.read()
            if (byte == -1) {
                throw EOFException("Kết thúc dữ liệu đột ngột khi đang đọc biến độ dài VLQ.")
            }
            value = (value shl 7) or (byte.toLong() and 0x7F)
            if ((byte and 0x80) == 0) {
                break
            }
        }
        return value
    }

    private fun buildTempoSegments(
        tempos: List<RawTempoEvent>,
        ticksPerQuarterNote: Int
    ): List<TempoSegment> {
        val segments = mutableListOf<TempoSegment>()
        var accumulatedMs = 0L

        for (i in tempos.indices) {
            val curr = tempos[i]
            if (i == 0) {
                segments.add(
                    TempoSegment(
                        startTick = curr.tick,
                        startMs = 0L,
                        mpqn = curr.mpqn
                    )
                )
            } else {
                val prev = tempos[i - 1]
                val deltaTicks = curr.tick - prev.tick
                val deltaMs = (deltaTicks * prev.mpqn) / (ticksPerQuarterNote * 1000L)
                accumulatedMs += deltaMs
                segments.add(
                    TempoSegment(
                        startTick = curr.tick,
                        startMs = accumulatedMs,
                        mpqn = curr.mpqn
                    )
                )
            }
        }
        return segments
    }

    private fun tickToMs(
        tick: Long,
        segments: List<TempoSegment>,
        ticksPerQuarterNote: Int
    ): Long {
        var seg = segments.first()
        for (i in segments.indices.reversed()) {
            if (tick >= segments[i].startTick) {
                seg = segments[i]
                break
            }
        }
        val deltaTicks = tick - seg.startTick
        val deltaMs = (deltaTicks * seg.mpqn) / (ticksPerQuarterNote * 1000L)
        return seg.startMs + deltaMs
    }

    private data class RawTrackData(
        val trackIndex: Int,
        val trackName: String,
        val channels: List<Int>,
        val instrumentNumber: Int?,
        val tempoEvents: List<RawTempoEvent>,
        val timeSignatures: List<ParsedTimeSignatureEvent>,
        val notes: List<RawMidiNote>
    )

    private data class RawTempoEvent(
        val tick: Long,
        val mpqn: Long
    )

    private data class RawOpenNote(
        val startTick: Long,
        val velocity: Int
    )

    private data class RawMidiNote(
        val trackIndex: Int,
        val channel: Int,
        val midiNote: Int,
        val velocity: Int,
        val startTick: Long,
        val endTick: Long
    )

    private data class TempoSegment(
        val startTick: Long,
        val startMs: Long,
        val mpqn: Long
    )
}

class MidiParseException(message: String) : Exception(message)
