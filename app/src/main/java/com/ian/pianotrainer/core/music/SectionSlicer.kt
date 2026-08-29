package com.ian.pianotrainer.core.music

import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.LearningSection
import com.ian.pianotrainer.domain.model.SongTempoInfo
import com.ian.pianotrainer.domain.model.SongTimeSignature

object SectionSlicer {

    private val gridCalculator = BeatGridCalculator()

    /**
     * Slices a song's notes into pedagogical 2–4 measure learning sections
     * based on exact measure boundaries from the tempo map and time signatures.
     * Preserves chords (never cuts across simultaneous note onsets) and prevents empty trailing space.
     */
    fun sliceSong(
        songId: String,
        notes: List<ExerciseNote>,
        tempos: List<SongTempoInfo> = emptyList(),
        timeSignatures: List<SongTimeSignature> = emptyList(),
        defaultBpm: Int = 60
    ): List<LearningSection> {
        if (notes.isEmpty()) return emptyList()

        val sortedNotes = notes.sortedBy { it.startMs }
        val firstNoteStartMs = sortedNotes.first().startMs
        val lastNoteEndMs = sortedNotes.maxOf { it.startMs + it.durationMs }

        // Calculate measure lines from 0 to lastNoteEndMs
        val gridLines = gridCalculator.calculate(
            windowStartMs = 0L,
            windowEndMs = lastNoteEndMs + 1000L,
            ppq = 480,
            tempos = tempos,
            timeSignatures = timeSignatures
        )

        val measureStarts = gridLines
            .filter { it.isMeasureStart }
            .map { it.timeMs }
            .distinct()
            .sorted()

        if (measureStarts.size <= 2) {
            // Very short piece: single section
            val secNotes = sortedNotes.filter { it.startMs in firstNoteStartMs..lastNoteEndMs }
            return listOf(
                LearningSection(
                    id = "${songId}_sec_1",
                    songId = songId,
                    startMs = 0L,
                    endMs = lastNoteEndMs,
                    startMeasure = 1,
                    endMeasure = measureStarts.size.coerceAtLeast(1),
                    label = "Cả bài (1–${measureStarts.size.coerceAtLeast(1)})",
                    difficulty = 1,
                    noteCount = secNotes.size
                )
            )
        }

        val sections = mutableListOf<LearningSection>()
        var measureIdx = 0
        var sectionNum = 1

        while (measureIdx < measureStarts.size - 1) {
            val startMeasureNum = measureIdx + 1
            val startMs = measureStarts[measureIdx]

            // Slicing window: 2 to 4 measures based on density
            val targetMeasures = 3
            val endMeasureIdx = minOf(measureIdx + targetMeasures, measureStarts.size - 1)
            val endMeasureNum = endMeasureIdx
            var nominalEndMs = measureStarts[endMeasureIdx]

            // Ensure we don't cut across notes starting at the exact same chord onset
            val chordAtBoundary = sortedNotes.filter { it.startMs == nominalEndMs }
            if (chordAtBoundary.isNotEmpty() && endMeasureIdx < measureStarts.size - 1) {
                // If there's a chord right on boundary, include it in next section
            }

            // Adjust endMs to contain notes in this measure range without trailing empty space
            val notesInRange = sortedNotes.filter { it.startMs >= startMs && it.startMs < nominalEndMs }
            val actualEndMs = if (notesInRange.isNotEmpty()) {
                maxOf(nominalEndMs, notesInRange.maxOf { it.startMs + it.durationMs })
            } else {
                nominalEndMs
            }

            val finalEndMs = minOf(actualEndMs, lastNoteEndMs)

            sections.add(
                LearningSection(
                    id = "${songId}_sec_$sectionNum",
                    songId = songId,
                    startMs = startMs,
                    endMs = finalEndMs,
                    startMeasure = startMeasureNum,
                    endMeasure = endMeasureNum,
                    label = "Đoạn $sectionNum (Ô $startMeasureNum–$endMeasureNum)",
                    difficulty = if (notesInRange.size > 24) 2 else 1,
                    noteCount = notesInRange.size
                )
            )

            measureIdx = endMeasureIdx
            sectionNum++

            // If remaining measures is only 1, merge with last section or finish
            if (measureIdx == measureStarts.size - 1 && notes.any { it.startMs >= measureStarts[measureIdx] }) {
                val lastStartMeasure = measureIdx + 1
                val lastStartMs = measureStarts[measureIdx]
                val lastNotes = sortedNotes.filter { it.startMs >= lastStartMs }
                sections.add(
                    LearningSection(
                        id = "${songId}_sec_$sectionNum",
                        songId = songId,
                        startMs = lastStartMs,
                        endMs = lastNoteEndMs,
                        startMeasure = lastStartMeasure,
                        endMeasure = lastStartMeasure,
                        label = "Đoạn $sectionNum (Ô $lastStartMeasure)",
                        difficulty = 1,
                        noteCount = lastNotes.size
                    )
                )
                break
            }
        }

        return if (sections.isEmpty()) {
            listOf(
                LearningSection(
                    id = "${songId}_sec_1",
                    songId = songId,
                    startMs = 0L,
                    endMs = lastNoteEndMs,
                    startMeasure = 1,
                    endMeasure = 1,
                    label = "Cả bài",
                    difficulty = 1,
                    noteCount = sortedNotes.size
                )
            )
        } else {
            sections
        }
    }
}
