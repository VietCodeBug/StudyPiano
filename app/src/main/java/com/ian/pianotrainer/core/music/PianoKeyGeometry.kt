package com.ian.pianotrainer.core.music

import com.ian.pianotrainer.domain.model.KeyboardRangeMode

data class PianoKeyGeometry(
    val midiNote: Int,
    val isBlack: Boolean,
    val left: Float,
    val right: Float,
    val width: Float,
    val centerX: Float
)

data class PianoRangeResult(
    val startMidiNote: Int,
    val endMidiNote: Int,
    val geometries: Map<Int, PianoKeyGeometry>,
    val whiteNotes: List<Int>
)

object PianoGeometryCalculator {

    fun calculateRangeGeometries(
        startMidiNote: Int,
        endMidiNote: Int,
        totalWidth: Float
    ): PianoRangeResult {
        val clampedStart = startMidiNote.coerceIn(MidiConstants.MIN_MIDI_NOTE, MidiConstants.MAX_MIDI_NOTE)
        val clampedEnd = endMidiNote.coerceIn(clampedStart, MidiConstants.MAX_MIDI_NOTE)

        val whiteNotes = (clampedStart..clampedEnd).filter { !MidiConstants.isBlackKey(it) }
        val totalWhiteKeys = whiteNotes.size.coerceAtLeast(1)
        val whiteKeyWidth = totalWidth / totalWhiteKeys
        val blackKeyWidth = whiteKeyWidth * 0.65f

        val map = mutableMapOf<Int, PianoKeyGeometry>()
        val whiteKeyBounds = mutableMapOf<Int, Pair<Float, Float>>()

        whiteNotes.forEachIndexed { index, midiNote ->
            val left = index * whiteKeyWidth
            val right = left + whiteKeyWidth
            whiteKeyBounds[midiNote] = Pair(left, right)
            map[midiNote] = PianoKeyGeometry(
                midiNote = midiNote,
                isBlack = false,
                left = left,
                right = right,
                width = whiteKeyWidth,
                centerX = left + whiteKeyWidth / 2f
            )
        }

        for (midiNote in clampedStart..clampedEnd) {
            if (MidiConstants.isBlackKey(midiNote)) {
                val prevWhite = whiteKeyBounds[midiNote - 1]
                val nextWhite = whiteKeyBounds[midiNote + 1]
                val boundaryX = when {
                    prevWhite != null -> prevWhite.second
                    nextWhite != null -> nextWhite.first
                    else -> 0f
                }
                val left = boundaryX - (blackKeyWidth / 2f)
                val right = boundaryX + (blackKeyWidth / 2f)
                map[midiNote] = PianoKeyGeometry(
                    midiNote = midiNote,
                    isBlack = true,
                    left = left,
                    right = right,
                    width = blackKeyWidth,
                    centerX = boundaryX
                )
            }
        }

        return PianoRangeResult(
            startMidiNote = clampedStart,
            endMidiNote = clampedEnd,
            geometries = map,
            whiteNotes = whiteNotes
        )
    }

    fun calculateGeometries(
        startOctave: Int,
        octaveCount: Int,
        totalWidth: Float
    ): Map<Int, PianoKeyGeometry> {
        val startMidi = (startOctave + 1) * 12 // Octave 3 -> C3 (48)
        val endMidi = (startOctave + 1 + octaveCount) * 12 // Octave 3 + 2 -> C5 (72)
        return calculateRangeGeometries(startMidi, endMidi, totalWidth).geometries
    }

    /**
     * Compute range based on KeyboardRangeMode and viewport anchor (baseOctave)
     */
    fun computeRangeForMode(
        mode: KeyboardRangeMode,
        baseOctave: Int,
        totalWidth: Float,
        upcomingMidiNotes: List<Int> = emptyList()
    ): PianoRangeResult {
        return when (mode) {
            KeyboardRangeMode.FULL_88_KEYS -> {
                calculateRangeGeometries(
                    startMidiNote = MidiConstants.MIN_MIDI_NOTE, // 21 (A0)
                    endMidiNote = MidiConstants.MAX_MIDI_NOTE,   // 108 (C8)
                    totalWidth = totalWidth
                )
            }
            KeyboardRangeMode.SIX_OCTAVES -> {
                // ~6 octaves: C1 (24) to C7 (96)
                calculateRangeGeometries(
                    startMidiNote = 24,
                    endMidiNote = 96,
                    totalWidth = totalWidth
                )
            }
            KeyboardRangeMode.FOUR_OCTAVES -> {
                // 4 octaves centered around baseOctave (e.g. baseOctave 3 => C2 to C6)
                val startMidi = ((baseOctave - 1).coerceAtLeast(1) + 1) * 12 // e.g. Octave 2 -> C2 (36)
                val endMidi = (startMidi + 4 * 12).coerceAtMost(108) // e.g. C6 (84)
                calculateRangeGeometries(
                    startMidiNote = startMidi,
                    endMidiNote = endMidi,
                    totalWidth = totalWidth
                )
            }
            KeyboardRangeMode.TWO_OCTAVES -> {
                val startMidi = (baseOctave + 1) * 12
                val endMidi = (baseOctave + 3) * 12
                calculateRangeGeometries(
                    startMidiNote = startMidi,
                    endMidiNote = endMidi,
                    totalWidth = totalWidth
                )
            }
            KeyboardRangeMode.AUTO -> {
                if (upcomingMidiNotes.isNotEmpty()) {
                    val minNote = upcomingMidiNotes.minOrNull() ?: 60
                    val maxNote = upcomingMidiNotes.maxOrNull() ?: 72
                    val span = maxNote - minNote
                    if (span > 36) {
                        // Wide span -> use 4 or 6 octaves
                        val startOct = ((minNote / 12) - 1).coerceIn(1, 4)
                        val endOct = ((maxNote / 12) + 1).coerceIn(startOct + 3, 7)
                        val startMidi = (startOct + 1) * 12
                        val endMidi = (endOct + 1) * 12
                        calculateRangeGeometries(startMidi, endMidi, totalWidth)
                    } else {
                        // Fit into 2-3 octaves
                        val centerOct = (minNote + maxNote) / 24
                        val startOct = (centerOct - 1).coerceIn(1, 5)
                        val startMidi = (startOct + 1) * 12
                        val endMidi = (startOct + 3) * 12
                        calculateRangeGeometries(startMidi, endMidi, totalWidth)
                    }
                } else {
                    val startMidi = (baseOctave + 1) * 12
                    val endMidi = (baseOctave + 3) * 12
                    calculateRangeGeometries(startMidi, endMidi, totalWidth)
                }
            }
        }
    }
}

