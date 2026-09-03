package com.ian.pianotrainer.feature.practice

import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.LearningSection

object SectionNoteFilter {
    fun filter(notes: List<ExerciseNote>, section: LearningSection, isFinalSection: Boolean = false): List<ExerciseNote> =
        notes.filter { note ->
            note.startMs >= section.startMs &&
                (note.startMs < section.endMs || (isFinalSection && note.startMs == section.endMs))
        }
}