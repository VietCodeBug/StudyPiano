package com.ian.pianotrainer.feature.practice

import com.ian.pianotrainer.core.music.BeatGridCalculator
import com.ian.pianotrainer.domain.model.SongPlaybackData
import com.ian.pianotrainer.domain.model.SongTempoInfo

internal data class PlayerBeat(val timeMs: Long, val beat: Int, val isMeasureStart: Boolean, val bpm: Int)

internal fun buildPlayerBeats(data: SongPlaybackData?, durationMs: Long, manualBpm: Int?): List<PlayerBeat> {
    val tempos = if (manualBpm != null) {
        listOf(SongTempoInfo(0L, 0L, 60_000_000L / manualBpm.coerceIn(30, 240), manualBpm.coerceIn(30, 240)))
    } else data?.tempos.orEmpty()
    val lines = BeatGridCalculator().calculate(0L, durationMs.coerceAtLeast(1L), tempos = tempos, timeSignatures = data?.timeSignatures.orEmpty())
    var beat = 0
    return lines.map { line ->
        beat = if (line.isMeasureStart) 1 else beat + 1
        val bpm = tempos.lastOrNull { it.startMs <= line.timeMs }?.bpm ?: manualBpm ?: data?.song?.defaultBpm ?: 60
        PlayerBeat(line.timeMs, beat, line.isMeasureStart, bpm)
    }
}

internal class PlayerBeatTracker(private var beats: List<PlayerBeat> = emptyList()) {
    private var lastPositionMs: Long? = null

    fun replace(newBeats: List<PlayerBeat>, positionMs: Long) {
        beats = newBeats
        reset(positionMs)
    }

    fun reset(positionMs: Long): PlayerBeat? {
        lastPositionMs = positionMs
        return beats.lastOrNull { it.timeMs <= positionMs }
    }

    fun advance(positionMs: Long): PlayerBeat? {
        val previous = lastPositionMs ?: return reset(positionMs).let { null }
        lastPositionMs = positionMs
        if (positionMs < previous) {
            return beats.lastOrNull { it.timeMs == positionMs }
        }
        return beats.lastOrNull { it.timeMs > previous && it.timeMs <= positionMs }
    }
}
