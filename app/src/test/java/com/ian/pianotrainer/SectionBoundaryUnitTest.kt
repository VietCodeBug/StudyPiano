package com.ian.pianotrainer

import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.LearningSection
import com.ian.pianotrainer.feature.practice.SectionNoteFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionBoundaryUnitTest {
    private val notes = listOf(ExerciseNote(60, startMs=0), ExerciseNote(64, startMs=6000), ExerciseNote(67, startMs=6000), ExerciseNote(72, startMs=12000))
    private val first = LearningSection(id="s1", songId="song", startMs=0, endMs=6000, label="Section 1")
    private val second = LearningSection(id="s2", songId="song", startMs=6000, endMs=12000, label="Section 2")
    @Test fun `boundary chord belongs to exactly one section`() {
        val a=SectionNoteFilter.filter(notes,first); val b=SectionNoteFilter.filter(notes,second)
        assertFalse(a.any{it.startMs==6000L}); assertEquals(2,b.count{it.startMs==6000L})
    }
    @Test fun `final section may include exact song end`() {
        assertTrue(SectionNoteFilter.filter(notes,second,true).any{it.startMs==12000L})
    }
}