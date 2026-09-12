package com.ian.pianotrainer.data.practice

internal data class MetronomeDeadline(val nextDeadlineMs: Long, val skippedBeats: Int)

internal object MetronomeTiming {
    fun intervalMs(bpm: Int): Long = 60_000L / bpm.coerceIn(30, 240)

    fun nextDeadline(previousDeadlineMs: Long, nowMs: Long, bpm: Int): MetronomeDeadline {
        val interval = intervalMs(bpm)
        var deadline = previousDeadlineMs + interval
        if (deadline > nowMs) return MetronomeDeadline(deadline, 0)
        val skipped = ((nowMs - deadline) / interval + 1L).toInt()
        deadline += skipped * interval
        return MetronomeDeadline(deadline, skipped)
    }
}

internal class TapTempoTracker {
    private val taps = ArrayDeque<Long>()

    fun tap(nowMs: Long): Int? {
        if (taps.isNotEmpty() && nowMs - taps.last() > 2_000L) taps.clear()
        taps.addLast(nowMs)
        while (taps.size > 5) taps.removeFirst()
        val intervals = taps.zipWithNext { left, right -> right - left }.filter { it in 250L..2_000L }
        return intervals.takeIf { it.isNotEmpty() }?.let { (60_000.0 / it.average()).toInt().coerceIn(30, 240) }
    }
}
