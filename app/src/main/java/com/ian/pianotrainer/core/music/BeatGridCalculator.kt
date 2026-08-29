package com.ian.pianotrainer.core.music

import com.ian.pianotrainer.domain.model.SongTempoInfo
import com.ian.pianotrainer.domain.model.SongTimeSignature

data class BeatGridLine(
    val timeMs: Long,
    val isMeasureStart: Boolean,
    val measureNumber: Int? = null
)

class BeatGridCalculator {
    fun calculate(
        windowStartMs: Long,
        windowEndMs: Long,
        ppq: Int = 480,
        tempos: List<SongTempoInfo> = emptyList(),
        timeSignatures: List<SongTimeSignature> = emptyList()
    ): List<BeatGridLine> {
        if (windowEndMs <= windowStartMs) return emptyList()

        val safeTempos = if (tempos.isEmpty()) {
            listOf(SongTempoInfo(startTick = 0L, startMs = 0L, microsecondsPerQuarterNote = 500000L, bpm = 120))
        } else {
            tempos.sortedBy { it.startMs }
        }

        val safeSignatures = if (timeSignatures.isEmpty()) {
            listOf(SongTimeSignature(startTick = 0L, startMs = 0L, numerator = 4, denominator = 4))
        } else {
            timeSignatures.sortedBy { it.startMs }
        }

        val lines = mutableListOf<BeatGridLine>()
        var curMs = 0L
        var beatCounter = 0
        var measureCounter = 1

        var currentTempoIdx = 0
        var currentSigIdx = 0

        var curTempo = safeTempos[0]
        var curSig = safeSignatures[0]

        var msPerBeat = 60000.0 / curTempo.bpm.coerceIn(30, 240)
        var beatsPerMeasure = curSig.numerator.coerceAtLeast(1)

        while (curMs <= windowEndMs + 2000L) {
            // Update tempo segment if passed
            while (currentTempoIdx + 1 < safeTempos.size && safeTempos[currentTempoIdx + 1].startMs <= curMs) {
                currentTempoIdx++
                curTempo = safeTempos[currentTempoIdx]
                msPerBeat = 60000.0 / curTempo.bpm.coerceIn(30, 240)
            }
            // Update time signature if passed
            while (currentSigIdx + 1 < safeSignatures.size && safeSignatures[currentSigIdx + 1].startMs <= curMs) {
                currentSigIdx++
                curSig = safeSignatures[currentSigIdx]
                beatsPerMeasure = curSig.numerator.coerceAtLeast(1)
                beatCounter = 0 // reset measure alignment at signature change
            }

            if (curMs >= windowStartMs - 500L) {
                val isMeasure = (beatCounter % beatsPerMeasure == 0)
                lines.add(
                    BeatGridLine(
                        timeMs = curMs,
                        isMeasureStart = isMeasure,
                        measureNumber = if (isMeasure) measureCounter else null
                    )
                )
            }

            if (beatCounter % beatsPerMeasure == 0) {
                measureCounter++
            }
            beatCounter++
            curMs += msPerBeat.toLong().coerceAtLeast(50L)
        }

        return lines
    }
}
