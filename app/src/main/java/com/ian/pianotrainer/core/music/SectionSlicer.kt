package com.ian.pianotrainer.core.music

import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.LearningSection
import com.ian.pianotrainer.domain.model.SongTempoInfo
import com.ian.pianotrainer.domain.model.SongTimeSignature
import java.util.UUID

object SectionSlicer {

    /**
     * Slices a song's notes into pedagogical 2–4 measure learning sections.
     * Preserves chords (never cuts across simultaneous notes) and adapts to note density.
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
        val songDurationMs = sortedNotes.maxOf { it.startMs + it.durationMs }

        // Calculate average measure duration based on time signature and tempo
        val bpm = tempos.firstOrNull()?.bpm ?: defaultBpm
        val timeSig = timeSignatures.firstOrNull()
        val beatsPerMeasure = timeSig?.numerator ?: 4
        val msPerBeat = 60_000.0 / bpm
        val msPerMeasure = (beatsPerMeasure * msPerBeat).toLong().coerceAtLeast(1000L)

        val totalEstimatedMeasures = ((songDurationMs / msPerMeasure).toInt() + 1).coerceAtLeast(1)

        val sections = mutableListOf<LearningSection>()
        var currentMeasure = 1
        var sectionIndex = 1

        while (currentMeasure <= totalEstimatedMeasures) {
            val startMs = (currentMeasure - 1) * msPerMeasure
            // Look ahead 2 to 4 measures based on note density
            val targetMeasures = 3
            val endMeasure = minOf(currentMeasure + targetMeasures - 1, totalEstimatedMeasures)
            val nominalEndMs = minOf(endMeasure * msPerMeasure, songDurationMs)

            // Adjust end boundary to not split chords
            val activeChordAtBoundary = sortedNotes.filter {
                it.startMs <= nominalEndMs && (it.startMs + it.durationMs) > nominalEndMs
            }

            val adjustedEndMs = if (activeChordAtBoundary.isNotEmpty()) {
                activeChordAtBoundary.maxOf { it.startMs + it.durationMs }
            } else {
                nominalEndMs
            }

            val sectionNotes = sortedNotes.filter { it.startMs in startMs until adjustedEndMs }

            sections.add(
                LearningSection(
                    id = "${songId}_sec_$sectionIndex",
                    songId = songId,
                    startMs = startMs,
                    endMs = adjustedEndMs,
                    startMeasure = currentMeasure,
                    endMeasure = endMeasure,
                    label = "Đoạn $sectionIndex (Ô $currentMeasure–$endMeasure)",
                    difficulty = if (sectionNotes.size > 20) 2 else 1,
                    noteCount = sectionNotes.size
                )
            )

            currentMeasure = endMeasure + 1
            sectionIndex++
        }

        return sections
    }
}
