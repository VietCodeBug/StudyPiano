package com.ian.pianotrainer

import com.ian.pianotrainer.feature.practice.PlayerTimeline
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimelineUnitTest {
    @Test fun `section timeline starts at zero and uses section duration`() {
        assertEquals(0L, PlayerTimeline.relativePositionMs(6000,6000,12000))
        assertEquals("00:00", PlayerTimeline.format(PlayerTimeline.relativePositionMs(6000,6000,12000)))
        assertEquals(6000L, PlayerTimeline.durationMs(6000,12000))
    }
    @Test fun `relative seek converts to absolute position`() {
        assertEquals(8000L, PlayerTimeline.absolutePositionMs(2000,6000,12000))
    }
}