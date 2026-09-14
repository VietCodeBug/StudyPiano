package com.ian.pianotrainer

import com.ian.pianotrainer.domain.model.ImportedSong
import com.ian.pianotrainer.domain.model.SongPlaybackData
import com.ian.pianotrainer.domain.model.SongTempoInfo
import com.ian.pianotrainer.domain.model.SongTimeSignature
import com.ian.pianotrainer.feature.practice.PlayerBeatTracker
import com.ian.pianotrainer.feature.practice.buildPlayerBeats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMetronomeTimelineUnitTest {
    private val data = SongPlaybackData(
        song = ImportedSong("s", "Song", "s.mid", defaultBpm = 60), notes = emptyList(), tracks = emptyList(),
        tempos = listOf(SongTempoInfo(0, 0, 1_000_000, 60), SongTempoInfo(1920, 4_000, 500_000, 120)),
        timeSignatures = listOf(SongTimeSignature(0, 0, 4, 4))
    )

    @Test fun naturalPlaybackCrossesTempoChangeAndKeepsMeasureAccent() {
        val beats = buildPlayerBeats(data, 6_000, null)
        val tracker = PlayerBeatTracker(beats)
        tracker.reset(3_990)
        val changed = tracker.advance(4_010)!!
        assertEquals(120, changed.bpm)
        assertTrue(changed.isMeasureStart)
        assertEquals(1, changed.beat)
    }

    @Test fun seekBetweenBeatsDoesNotEmitAndNextCrossingIsAligned() {
        val tracker = PlayerBeatTracker(buildPlayerBeats(data, 6_000, null))
        tracker.reset(4_250)
        assertNull(tracker.advance(4_490))
        val next = tracker.advance(4_510)!!
        assertEquals(2, next.beat)
        assertFalse(next.isMeasureStart)
    }

    @Test fun loopBackToMeasureStartEmitsOneAccentedBeat() {
        val tracker = PlayerBeatTracker(buildPlayerBeats(data, 6_000, null))
        tracker.reset(5_900)
        val loopBeat = tracker.advance(4_000)!!
        assertTrue(loopBeat.isMeasureStart)
        assertEquals(1, loopBeat.beat)
        assertNull(tracker.advance(4_010))
    }

    @Test fun manualBpmRemainsConstantInsteadOfBeingOverwrittenByMap() {
        val beats = buildPlayerBeats(data, 8_000, 80)
        assertTrue(beats.all { it.bpm == 80 })
        assertEquals(listOf(0L, 750L, 1500L), beats.take(3).map { it.timeMs })
    }
}
