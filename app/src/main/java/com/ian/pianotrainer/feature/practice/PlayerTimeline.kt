package com.ian.pianotrainer.feature.practice

object PlayerTimeline {
    fun durationMs(sectionStartMs: Long, sectionEndMs: Long): Long =
        (sectionEndMs - sectionStartMs).coerceAtLeast(0L)

    fun relativePositionMs(absolutePositionMs: Long, sectionStartMs: Long, sectionEndMs: Long): Long =
        (absolutePositionMs - sectionStartMs).coerceIn(0L, durationMs(sectionStartMs, sectionEndMs))

    fun absolutePositionMs(relativePositionMs: Long, sectionStartMs: Long, sectionEndMs: Long): Long =
        sectionStartMs + relativePositionMs.coerceIn(0L, durationMs(sectionStartMs, sectionEndMs))

    fun format(ms: Long): String {
        val seconds = ms.coerceAtLeast(0L) / 1000L
        return "%02d:%02d".format(seconds / 60L, seconds % 60L)
    }
}