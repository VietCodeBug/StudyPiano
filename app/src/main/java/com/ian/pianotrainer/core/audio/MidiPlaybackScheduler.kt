package com.ian.pianotrainer.core.audio

import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.SongPlaybackData
import java.util.PriorityQueue

interface MidiPlaybackScheduler {
    fun load(playbackData: SongPlaybackData)
    fun play(fromPositionMs: Long = 0L)
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(multiplier: Float)
    fun setTrackRole(trackIndex: Int, role: PlaybackRole)
    fun setHandRole(hand: HandMode, role: PlaybackRole)
    fun setDemoMode(isDemo: Boolean)
    fun tick(currentPositionMs: Long)
    fun stop()
}

class DefaultMidiPlaybackScheduler(
    private val audioEngine: PianoAudioEngine
) : MidiPlaybackScheduler {
    private data class ActiveVoice(val noteIndex: Int, val offTimeMs: Long)

    private var allNotes: List<ExerciseNote> = emptyList()
    private var isPlaying = false
    private var isDemoMode = false
    private var speedMultiplier = 1.0f
    private var nextNoteIndex = 0
    private val trackRoles = mutableMapOf<Int, PlaybackRole>()
    private val handRoles = mutableMapOf<HandMode, PlaybackRole>()
    private val activeVoices = PriorityQueue<ActiveVoice>(compareBy { it.offTimeMs })

    override fun load(playbackData: SongPlaybackData) {
        stop()
        allNotes = playbackData.notes.sortedWith(compareBy({ it.startMs }, { it.midiNote }, { it.trackIndex }))
        trackRoles.clear()
        handRoles.clear()
        handRoles[HandMode.RIGHT] = PlaybackRole.PRACTICE
        handRoles[HandMode.LEFT] = PlaybackRole.PRACTICE
        handRoles[HandMode.BOTH] = PlaybackRole.PRACTICE
    }

    override fun play(fromPositionMs: Long) {
        seekTo(fromPositionMs)
        isPlaying = true
    }

    override fun pause() {
        isPlaying = false
        resetVoices()
    }

    override fun seekTo(positionMs: Long) {
        resetVoices()
        nextNoteIndex = lowerBound(positionMs.coerceAtLeast(0L))
    }

    private fun lowerBound(positionMs: Long): Int {
        var low = 0
        var high = allNotes.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (allNotes[mid].startMs < positionMs) low = mid + 1 else high = mid
        }
        return low
    }

    override fun setSpeed(multiplier: Float) {
        speedMultiplier = multiplier.coerceIn(0.25f, 2.0f)
    }

    override fun setTrackRole(trackIndex: Int, role: PlaybackRole) { trackRoles[trackIndex] = role }
    override fun setHandRole(hand: HandMode, role: PlaybackRole) { handRoles[hand] = role }
    override fun setDemoMode(isDemo: Boolean) { isDemoMode = isDemo }

    override fun tick(currentPositionMs: Long) {
        if (!isPlaying) return
        while (activeVoices.isNotEmpty()) {
            val top = activeVoices.peek() ?: break
            if (top.offTimeMs <= currentPositionMs) {
                val voice = activeVoices.poll() ?: break
                val note = allNotes[voice.noteIndex]
                audioEngine.noteOff(note.midiNote, note.trackIndex)
            } else {
                break
            }
        }
        while (nextNoteIndex < allNotes.size) {
            val note = allNotes[nextNoteIndex]
            if (note.startMs > currentPositionMs) break
            val index = nextNoteIndex++
            if (shouldPlayNote(note)) {
                audioEngine.noteOn(note.midiNote, note.velocity, note.trackIndex)
                activeVoices.add(ActiveVoice(index, note.startMs + note.durationMs))
            }
        }
    }

    private fun shouldPlayNote(note: ExerciseNote): Boolean {
        if (isDemoMode) return true
        val role = trackRoles[note.trackIndex] ?: handRoles[note.hand] ?: PlaybackRole.PRACTICE
        return role == PlaybackRole.DEMO || role == PlaybackRole.ACCOMPANIMENT
    }

    private fun resetVoices() {
        audioEngine.sustainPedal(false)
        audioEngine.allNotesOff()
        activeVoices.clear()
    }

    override fun stop() {
        isPlaying = false
        resetVoices()
        nextNoteIndex = 0
    }
}