package com.ian.pianotrainer.core.music

import com.ian.pianotrainer.domain.model.ExerciseNote

class VisibleNoteWindowSelector(
    private val sortedNotes: List<ExerciseNote>
) {
    val maxDurationMs: Long = sortedNotes.maxOfOrNull { it.durationMs } ?: 0L

    fun getVisibleNoteRange(
        windowStartMs: Long,
        windowEndMs: Long
    ): IntRange {
        if (sortedNotes.isEmpty()) return IntRange.EMPTY

        // Search lower bound: smallest index where note.startMs >= windowStartMs - maxDurationMs
        val minSearchStartMs = windowStartMs - maxDurationMs
        var low = 0
        var high = sortedNotes.size - 1
        var startIndex = sortedNotes.size

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (sortedNotes[mid].startMs >= minSearchStartMs) {
                startIndex = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }

        if (startIndex >= sortedNotes.size) return IntRange.EMPTY

        // Search upper bound: largest index where note.startMs <= windowEndMs
        low = startIndex
        high = sortedNotes.size - 1
        var endIndex = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (sortedNotes[mid].startMs <= windowEndMs) {
                endIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return if (endIndex >= startIndex) startIndex..endIndex else IntRange.EMPTY
    }
}
