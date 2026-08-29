package com.ian.pianotrainer.core.audio

import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.SongPlaybackData

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

    private var allNotes: List<ExerciseNote> = emptyList()
    private var isPlaying = false
    private var isDemoMode = false
    private var speedMultiplier = 1.0f

    // Track index -> PlaybackRole
    private val trackRoles = mutableMapOf<Int, PlaybackRole>()
    // HandMode -> PlaybackRole
    private val handRoles = mutableMapOf<HandMode, PlaybackRole>()

    // Track which notes have fired NoteOn
    private val triggeredNoteIndices = mutableSetOf<Int>()
    // Active notes waiting for NoteOff: noteIndex -> offTimeMs
    private val activeNoteOffs = mutableMapOf<Int, Long>()

    override fun load(playbackData: SongPlaybackData) {
        stop()
        allNotes = playbackData.notes.sortedWith(compareBy({ it.startMs }, { it.midiNote }))
        trackRoles.clear()
        handRoles.clear()
        // Default: Demo mode off (both hands default to PRACTICE unless specified)
        handRoles[HandMode.RIGHT] = PlaybackRole.PRACTICE
        handRoles[HandMode.LEFT] = PlaybackRole.PRACTICE
        handRoles[HandMode.BOTH] = PlaybackRole.PRACTICE
    }

    override fun play(fromPositionMs: Long) {
        isPlaying = true
        seekTo(fromPositionMs)
    }

    override fun pause() {
        isPlaying = false
        audioEngine.allNotesOff()
        activeNoteOffs.clear()
    }

    override fun seekTo(positionMs: Long) {
        audioEngine.allNotesOff()
        activeNoteOffs.clear()
        triggeredNoteIndices.clear()

        // Mark any past notes as triggered so they don't fire retroactively
        for (i in allNotes.indices) {
            if (allNotes[i].startMs < positionMs) {
                triggeredNoteIndices.add(i)
            }
        }
    }

    override fun setSpeed(multiplier: Float) {
        speedMultiplier = multiplier.coerceIn(0.25f, 2.0f)
    }

    override fun setTrackRole(trackIndex: Int, role: PlaybackRole) {
        trackRoles[trackIndex] = role
    }

    override fun setHandRole(hand: HandMode, role: PlaybackRole) {
        handRoles[hand] = role
    }

    override fun setDemoMode(isDemo: Boolean) {
        isDemoMode = isDemo
    }

    override fun tick(currentPositionMs: Long) {
        if (!isPlaying) return

        // 1. Process NoteOffs for notes that reached their end
        val endedNotes = mutableListOf<Int>()
        for ((idx, offTime) in activeNoteOffs) {
            if (currentPositionMs >= offTime) {
                val note = allNotes.getOrNull(idx)
                if (note != null) {
                    audioEngine.noteOff(note.midiNote, note.trackIndex)
                }
                endedNotes.add(idx)
            }
        }
        for (idx in endedNotes) {
            activeNoteOffs.remove(idx)
        }

        // 2. Trigger new NoteOns within timing window [currentPositionMs - 50ms, currentPositionMs]
        for (i in allNotes.indices) {
            val note = allNotes[i]
            if (note.startMs > currentPositionMs) {
                // Notes are sorted by startMs, so we can break early
                break
            }
            if (note.startMs <= currentPositionMs && !triggeredNoteIndices.contains(i)) {
                triggeredNoteIndices.add(i)

                if (shouldPlayNote(note)) {
                    audioEngine.noteOn(note.midiNote, note.velocity, note.trackIndex)
                    activeNoteOffs[i] = note.startMs + note.durationMs
                }
            }
        }
    }

    private fun shouldPlayNote(note: ExerciseNote): Boolean {
        if (isDemoMode) return true

        // Check specific track role first
        trackRoles[note.trackIndex]?.let { role ->
            return when (role) {
                PlaybackRole.DEMO, PlaybackRole.ACCOMPANIMENT -> true
                PlaybackRole.PRACTICE, PlaybackRole.MUTED -> false
            }
        }

        // Check hand role
        val handRole = handRoles[note.hand] ?: PlaybackRole.PRACTICE
        return when (handRole) {
            PlaybackRole.DEMO, PlaybackRole.ACCOMPANIMENT -> true
            PlaybackRole.PRACTICE, PlaybackRole.MUTED -> false
        }
    }

    override fun stop() {
        isPlaying = false
        audioEngine.allNotesOff()
        triggeredNoteIndices.clear()
        activeNoteOffs.clear()
    }
}
