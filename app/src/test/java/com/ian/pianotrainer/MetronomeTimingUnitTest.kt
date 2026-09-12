package com.ian.pianotrainer

import com.ian.pianotrainer.data.practice.MetronomeTiming
import com.ian.pianotrainer.data.practice.TapTempoTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetronomeTimingUnitTest {
    @Test fun supportedBpmsProduceExpectedIntervals() {
        assertEquals(1500L, MetronomeTiming.intervalMs(40))
        assertEquals(1000L, MetronomeTiming.intervalMs(60))
        assertEquals(500L, MetronomeTiming.intervalMs(120))
        assertEquals(300L, MetronomeTiming.intervalMs(200))
    }

    @Test fun lagSkipsMissedBeatsInsteadOfSchedulingABurst() {
        val result = MetronomeTiming.nextDeadline(previousDeadlineMs = 0L, nowMs = 2_200L, bpm = 120)
        assertEquals(4, result.skippedBeats)
        assertEquals(2_500L, result.nextDeadlineMs)
        assertTrue(result.nextDeadlineMs > 2_200L)
    }

    @Test fun tempoChangeUsesNewIntervalWithoutImmediateDeadline() {
        val result = MetronomeTiming.nextDeadline(previousDeadlineMs = 1_000L, nowMs = 1_050L, bpm = 200)
        assertEquals(1_300L, result.nextDeadlineMs)
        assertEquals(0, result.skippedBeats)
    }

    @Test fun tapTempoAveragesRecentValidTapsAndResetsAfterPause() {
        val tracker = TapTempoTracker()
        assertEquals(null, tracker.tap(0L))
        assertEquals(120, tracker.tap(500L))
        assertEquals(120, tracker.tap(1_000L))
        assertEquals(null, tracker.tap(4_000L))
        assertEquals(60, tracker.tap(5_000L))
    }
}
