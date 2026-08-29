package com.ian.pianotrainer.core.music

import com.ian.pianotrainer.core.music.midi.ParsedMidiNote
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Result of hand separation analysis.
 */
data class HandSeparationResult(
    val notes: List<ParsedMidiNote>,
    val leftHandNoteCount: Int,
    val rightHandNoteCount: Int,
    val isAutomatic: Boolean = true,
    val confidence: Float = 0f  // 0.0 to 1.0
)

/**
 * Separates notes from a single MIDI track into LEFT and RIGHT hand assignments.
 * Designed for MIDI Format 0 where both hands share one track.
 */
interface HandSeparationEngine {
    fun separate(notes: List<ParsedMidiNote>): HandSeparationResult
}

/**
 * Default implementation using onset-chord grouping and dynamic programming
 * to find optimal split points that minimize hand-assignment cost.
 *
 * Algorithm:
 * 1. Group notes into chords by onset time (25ms window, reusing existing chordId).
 * 2. For each chord, sort pitches ascending and try every possible split index.
 * 3. Score each split using:
 *    - continuity: distance from previous hand position
 *    - register: bass prefers LEFT, treble prefers RIGHT
 *    - crossing: penalty if LEFT goes above RIGHT
 *    - span: penalty if hand span > MAX_COMFORTABLE_SPAN semitones
 *    - jump: penalty for large jumps between consecutive chords
 * 4. Use greedy forward pass with continuity tracking.
 * 5. Single notes: assigned by register and nearest hand position.
 */
class DefaultHandSeparationEngine : HandSeparationEngine {

    companion object {
        /** Notes within this window are considered part of the same chord */
        private const val CHORD_ONSET_WINDOW_MS = 25L
        /** Maximum comfortable span for one hand (octave + minor 2nd) */
        private const val MAX_COMFORTABLE_SPAN = 14  // semitones
        /** Approximate split point — notes near here could go either way */
        private const val REGISTER_MIDPOINT = 60  // C4 (Middle C)
        /** Weight factors for the cost function */
        private const val W_CONTINUITY = 1.0f
        private const val W_REGISTER = 0.8f
        private const val W_CROSSING = 5.0f
        private const val W_SPAN = 2.0f
        private const val W_JUMP = 1.5f
    }

    override fun separate(notes: List<ParsedMidiNote>): HandSeparationResult {
        if (notes.isEmpty()) {
            return HandSeparationResult(
                notes = emptyList(),
                leftHandNoteCount = 0,
                rightHandNoteCount = 0,
                confidence = 1.0f
            )
        }

        // 1. Group notes into chords by onset time
        val chords = groupIntoChords(notes)

        // 2. Track hand positions for continuity
        var leftHandPos = -1  // last known pitch of leftmost LEFT note
        var rightHandPos = -1 // last known pitch of rightmost RIGHT note

        // Initialize hand positions from the first chord
        if (chords.isNotEmpty()) {
            val firstChord = chords[0]
            val pitches = firstChord.map { it.midiNote }.sorted()
            if (pitches.size >= 2) {
                leftHandPos = pitches.first()
                rightHandPos = pitches.last()
            } else {
                val p = pitches[0]
                if (p < REGISTER_MIDPOINT) {
                    leftHandPos = p
                    rightHandPos = REGISTER_MIDPOINT + 7
                } else {
                    rightHandPos = p
                    leftHandPos = REGISTER_MIDPOINT - 7
                }
            }
        }

        // 3. Process each chord: find optimal split
        val resultNotes = mutableListOf<ParsedMidiNote>()

        for (chord in chords) {
            val sorted = chord.sortedBy { it.midiNote }

            if (sorted.size == 1) {
                // Single note: assign by register and nearest hand position
                val note = sorted[0]
                val hand = assignSingleNote(note.midiNote, leftHandPos, rightHandPos)
                resultNotes.add(note.copy(assignedHand = hand.name))
                if (hand == AssignedHand.LEFT) {
                    leftHandPos = note.midiNote
                } else {
                    rightHandPos = note.midiNote
                }
            } else {
                // Multi-note chord: find best split point
                val bestSplit = findBestSplit(sorted, leftHandPos, rightHandPos)
                val pitches = sorted.map { it.midiNote }

                for ((i, note) in sorted.withIndex()) {
                    val hand = if (i < bestSplit) AssignedHand.LEFT else AssignedHand.RIGHT
                    resultNotes.add(note.copy(assignedHand = hand.name))
                }

                // Update hand positions
                if (bestSplit > 0) {
                    // Left hand takes the median of assigned LEFT notes
                    leftHandPos = pitches.take(bestSplit).let { lp ->
                        lp[lp.size / 2]
                    }
                }
                if (bestSplit < sorted.size) {
                    rightHandPos = pitches.drop(bestSplit).let { rp ->
                        rp[rp.size / 2]
                    }
                }
            }
        }

        val leftCount = resultNotes.count { it.assignedHand == AssignedHand.LEFT.name }
        val rightCount = resultNotes.count { it.assignedHand == AssignedHand.RIGHT.name }

        // Compute a rough confidence based on how cleanly the notes separate
        val confidence = computeConfidence(resultNotes)

        return HandSeparationResult(
            notes = resultNotes,
            leftHandNoteCount = leftCount,
            rightHandNoteCount = rightCount,
            isAutomatic = true,
            confidence = confidence
        )
    }

    /**
     * Group notes into chords by onset time.
     * Notes whose startMs falls within CHORD_ONSET_WINDOW_MS of the chord anchor
     * belong to the same chord.
     */
    private fun groupIntoChords(notes: List<ParsedMidiNote>): List<List<ParsedMidiNote>> {
        if (notes.isEmpty()) return emptyList()

        val sorted = notes.sortedWith(compareBy({ it.startMs }, { it.midiNote }))
        val chords = mutableListOf<MutableList<ParsedMidiNote>>()
        var currentChord = mutableListOf(sorted[0])
        var anchorMs = sorted[0].startMs

        for (i in 1 until sorted.size) {
            val note = sorted[i]
            if (note.startMs - anchorMs <= CHORD_ONSET_WINDOW_MS) {
                currentChord.add(note)
            } else {
                chords.add(currentChord)
                currentChord = mutableListOf(note)
                anchorMs = note.startMs
            }
        }
        chords.add(currentChord)

        return chords
    }

    /**
     * For a single note, decide LEFT or RIGHT based on register and proximity
     * to the last known hand positions.
     */
    private fun assignSingleNote(
        pitch: Int,
        leftHandPos: Int,
        rightHandPos: Int
    ): AssignedHand {
        // Strong register bias
        if (pitch < REGISTER_MIDPOINT - 12) return AssignedHand.LEFT
        if (pitch > REGISTER_MIDPOINT + 12) return AssignedHand.RIGHT

        // Near the middle: use proximity to last hand positions
        if (leftHandPos >= 0 && rightHandPos >= 0) {
            val distToLeft = abs(pitch - leftHandPos)
            val distToRight = abs(pitch - rightHandPos)

            // Avoid crossing: if this note is below leftHandPos, prefer LEFT
            if (pitch < leftHandPos - 2) return AssignedHand.LEFT
            // If above rightHandPos, prefer RIGHT
            if (pitch > rightHandPos + 2) return AssignedHand.RIGHT

            // Pure proximity with slight register bias
            val leftScore = distToLeft.toFloat() + if (pitch >= REGISTER_MIDPOINT) 2f else -2f
            val rightScore = distToRight.toFloat() + if (pitch < REGISTER_MIDPOINT) 2f else -2f

            return if (leftScore <= rightScore) AssignedHand.LEFT else AssignedHand.RIGHT
        }

        // Fallback: pure register
        return if (pitch < REGISTER_MIDPOINT) AssignedHand.LEFT else AssignedHand.RIGHT
    }

    /**
     * Find the best split index for a chord (sorted by pitch ascending).
     * splitIndex = 0 means all RIGHT, splitIndex = size means all LEFT.
     * Returns the index where RIGHT hand begins.
     */
    private fun findBestSplit(
        sortedNotes: List<ParsedMidiNote>,
        leftHandPos: Int,
        rightHandPos: Int
    ): Int {
        val pitches = sortedNotes.map { it.midiNote }
        val n = pitches.size

        var bestSplit = 0
        var bestCost = Float.MAX_VALUE

        // Try every split point from 0 (all RIGHT) to n (all LEFT)
        for (split in 0..n) {
            val leftPitches = pitches.take(split)
            val rightPitches = pitches.drop(split)

            var cost = 0f

            // --- Continuity cost ---
            if (leftPitches.isNotEmpty() && leftHandPos >= 0) {
                val leftCenter = leftPitches[leftPitches.size / 2]
                cost += W_CONTINUITY * abs(leftCenter - leftHandPos)
            }
            if (rightPitches.isNotEmpty() && rightHandPos >= 0) {
                val rightCenter = rightPitches[rightPitches.size / 2]
                cost += W_CONTINUITY * abs(rightCenter - rightHandPos)
            }

            // --- Register cost ---
            // LEFT notes above midpoint are penalized, RIGHT notes below midpoint are penalized
            for (p in leftPitches) {
                if (p > REGISTER_MIDPOINT + 5) {
                    cost += W_REGISTER * (p - REGISTER_MIDPOINT - 5)
                }
            }
            for (p in rightPitches) {
                if (p < REGISTER_MIDPOINT - 5) {
                    cost += W_REGISTER * (REGISTER_MIDPOINT - 5 - p)
                }
            }

            // --- Crossing penalty ---
            if (leftPitches.isNotEmpty() && rightPitches.isNotEmpty()) {
                val leftMax = leftPitches.max()
                val rightMin = rightPitches.min()
                if (leftMax > rightMin) {
                    cost += W_CROSSING * (leftMax - rightMin) * 2
                }
            }

            // --- Span penalty ---
            if (leftPitches.size >= 2) {
                val span = leftPitches.last() - leftPitches.first()
                if (span > MAX_COMFORTABLE_SPAN) {
                    cost += W_SPAN * (span - MAX_COMFORTABLE_SPAN)
                }
            }
            if (rightPitches.size >= 2) {
                val span = rightPitches.last() - rightPitches.first()
                if (span > MAX_COMFORTABLE_SPAN) {
                    cost += W_SPAN * (span - MAX_COMFORTABLE_SPAN)
                }
            }

            // --- Penalty for all one hand (mild preference for split) ---
            if (n >= 3 && (split == 0 || split == n)) {
                val span = pitches.last() - pitches.first()
                if (span > MAX_COMFORTABLE_SPAN) {
                    cost += W_SPAN * (span - MAX_COMFORTABLE_SPAN) * 0.5f
                }
            }

            if (cost < bestCost) {
                bestCost = cost
                bestSplit = split
            }
        }

        return bestSplit
    }

    /**
     * Compute a rough confidence score for the separation.
     * Higher confidence when hands don't overlap much and pitches are well separated.
     */
    private fun computeConfidence(notes: List<ParsedMidiNote>): Float {
        val leftNotes = notes.filter { it.assignedHand == AssignedHand.LEFT.name }
        val rightNotes = notes.filter { it.assignedHand == AssignedHand.RIGHT.name }

        if (leftNotes.isEmpty() || rightNotes.isEmpty()) return 0.5f

        val leftMax = leftNotes.maxOf { it.midiNote }
        val rightMin = rightNotes.minOf { it.midiNote }

        // If there's clear separation, high confidence
        val overlap = max(0, leftMax - rightMin)
        val totalRange = max(1, notes.maxOf { it.midiNote } - notes.minOf { it.midiNote })

        val overlapRatio = overlap.toFloat() / totalRange
        return (1.0f - overlapRatio * 0.5f).coerceIn(0.3f, 0.95f)
    }
}
