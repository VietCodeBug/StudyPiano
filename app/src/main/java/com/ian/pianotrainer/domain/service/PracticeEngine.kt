package com.ian.pianotrainer.domain.service

import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.NoteResultType
import com.ian.pianotrainer.domain.model.PracticeConfiguration
import com.ian.pianotrainer.domain.model.PracticeMode
import com.ian.pianotrainer.domain.model.PracticeResult
import kotlinx.coroutines.flow.StateFlow

data class PracticeEngineState(
    val currentNoteIndex: Int = 0,
    val totalNotes: Int = 0,
    val currentExpectedNote: ExerciseNote? = null,
    val currentExpectedNotes: List<ExerciseNote> = emptyList(),
    val correctNotesCount: Int = 0,
    val wrongNotesCount: Int = 0,
    val missedNotesCount: Int = 0,
    val earlyNotesCount: Int = 0,
    val lateNotesCount: Int = 0,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val lastEvaluatedResult: NoteResultType? = null,
    val lastPlayedNote: Int? = null,
    val isFinished: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedActiveSeconds: Long = 0L,
    val elapsedActiveMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val songDurationMs: Long = 0L,
    val speedMultiplier: Float = 1.0f,
    val loopStartMs: Long? = null,
    val loopEndMs: Long? = null,
    val lapCount: Int = 1,
    val isTargetDurationReached: Boolean = false,
    val targetDurationSeconds: Int = 0,
    val practiceMode: PracticeMode = PracticeMode.WAIT_FOR_NOTE,
    val activeFeedback: com.ian.pianotrainer.domain.model.VisualNoteFeedback? = null
)

interface PracticeEngine {
    val state: StateFlow<PracticeEngineState>

    fun startPractice(
        configuration: PracticeConfiguration,
        isLoopingEnabled: Boolean = false,
        targetDurationSeconds: Int = 0
    )
    fun processPlayedNote(midiNote: Int, velocity: Int)
    fun setLooping(enabled: Boolean)
    fun setLoopRange(startIndex: Int, endIndex: Int)
    fun setLoopRangeMs(startMs: Long, endMs: Long)
    fun clearLoop()
    fun seekTo(targetMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun tickTimer()
    fun pause()
    fun resume()
    fun restart()
    fun stop(): PracticeResult
}
