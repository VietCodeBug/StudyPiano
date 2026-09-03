package com.ian.pianotrainer.feature.practice

import com.ian.pianotrainer.core.audio.PianoAudioAvailability
import com.ian.pianotrainer.core.audio.PianoAudioEngine
import com.ian.pianotrainer.domain.model.MidiControlEvent

class DemoTimeline(
    startPositionMs: Long,
    anchorMonotonicMs: Long,
    private var speedMultiplier: Float
) {
    var basePositionMs: Long = startPositionMs
        private set
    var anchorMs: Long = anchorMonotonicMs
        private set

    fun positionAt(monotonicMs: Long): Long =
        basePositionMs + ((monotonicMs - anchorMs).coerceAtLeast(0L) * speedMultiplier).toLong()

    fun reset(positionMs: Long, monotonicMs: Long) {
        basePositionMs = positionMs
        anchorMs = monotonicMs
    }

    fun setSpeed(multiplier: Float) { speedMultiplier = multiplier.coerceIn(0.25f, 2.0f) }
}

class MidiSustainRouter(private val audioEngine: PianoAudioEngine) {
    fun onControlEvent(event: MidiControlEvent) {
        if (event.controllerNumber == 64) audioEngine.sustainPedal(event.value >= 64)
    }
    fun reset() {
        audioEngine.sustainPedal(false)
        audioEngine.allNotesOff()
    }
}

object PianoAudioCapabilityPolicy {
    fun canEnable(availability: PianoAudioAvailability): Boolean = availability is PianoAudioAvailability.Ready
    fun demoLabel(availability: PianoAudioAvailability): String =
        if (availability is PianoAudioAvailability.Ready) "Nghe mẫu" else "Xem chuyển động mẫu"
}