package com.ian.pianotrainer

import com.ian.pianotrainer.domain.model.PracticeMode
import com.ian.pianotrainer.feature.practice.DemoTimeline
import com.ian.pianotrainer.feature.practice.PracticeRestoreSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoTransportUnitTest {
    @Test fun `loop reset anchors second lap without jumping to end`() {
        val timeline = DemoTimeline(6000L, 1000L, 1f)
        assertEquals(12000L, timeline.positionAt(7000L))
        timeline.reset(6000L, 7000L)
        assertEquals(6000L, timeline.positionAt(7000L))
        assertEquals(9000L, timeline.positionAt(10000L))
    }
    @Test fun `speed multiplier uses monotonic elapsed time`() {
        val timeline = DemoTimeline(6000L, 1000L, 0.25f)
        assertEquals(7000L, timeline.positionAt(5000L))
        timeline.setSpeed(2f)
        assertEquals(14000L, timeline.positionAt(5000L))
    }
    @Test fun `restore snapshot retains position paused mode and section`() {
        val snapshot = PracticeRestoreSnapshot(8500L, true, PracticeMode.RHYTHM, "section_2", false)
        assertEquals(8500L, snapshot.positionMs)
        assertTrue(snapshot.wasPaused)
        assertEquals("section_2", snapshot.selectedSectionId)
    }
}