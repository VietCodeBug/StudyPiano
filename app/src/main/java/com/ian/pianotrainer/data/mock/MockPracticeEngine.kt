package com.ian.pianotrainer.data.mock

import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.NoteResultType
import com.ian.pianotrainer.domain.model.PracticeConfiguration
import com.ian.pianotrainer.domain.model.PracticeNoteResult
import com.ian.pianotrainer.domain.model.PracticeResult
import com.ian.pianotrainer.domain.model.PracticeSession
import com.ian.pianotrainer.domain.service.PracticeEngine
import com.ian.pianotrainer.domain.service.PracticeEngineState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class MockPracticeEngine : PracticeEngine {

    private val _state = MutableStateFlow(PracticeEngineState())
    override val state: StateFlow<PracticeEngineState> = _state.asStateFlow()

    private var currentConfig: PracticeConfiguration? = null
    private var startTimeMs: Long = 0L
    private val recordedNoteResults = mutableListOf<PracticeNoteResult>()

    override fun startPractice(configuration: PracticeConfiguration) {
        currentConfig = configuration
        startTimeMs = System.currentTimeMillis()
        recordedNoteResults.clear()

        val firstNote = configuration.notes.firstOrNull()
        _state.value = PracticeEngineState(
            currentNoteIndex = 0,
            totalNotes = configuration.notes.size,
            currentExpectedNote = firstNote,
            correctNotesCount = 0,
            wrongNotesCount = 0,
            missedNotesCount = 0,
            currentStreak = 0,
            maxStreak = 0,
            lastEvaluatedResult = null,
            lastPlayedNote = null,
            isFinished = configuration.notes.isEmpty(),
            isPaused = false
        )
    }

    override fun processPlayedNote(midiNote: Int, velocity: Int) {
        val currentState = _state.value
        if (currentState.isFinished || currentState.isPaused) return

        val expected = currentState.currentExpectedNote ?: return
        val isCorrect = (expected.midiNote == midiNote)
        val now = System.currentTimeMillis()
        val offsetMs = now - startTimeMs

        val resultType = if (isCorrect) NoteResultType.CORRECT else NoteResultType.WRONG

        recordedNoteResults.add(
            PracticeNoteResult(
                sessionId = "", // filled when session is created
                expectedMidiNote = expected.midiNote,
                playedMidiNote = midiNote,
                timingOffsetMs = 0L,
                resultType = resultType,
                occurredAtOffsetMs = offsetMs
            )
        )

        if (isCorrect) {
            val nextIndex = currentState.currentNoteIndex + 1
            val config = currentConfig
            val nextNote = if (config != null && nextIndex < config.notes.size) {
                config.notes[nextIndex]
            } else {
                null
            }

            val newCorrectCount = currentState.correctNotesCount + 1
            val newStreak = currentState.currentStreak + 1
            val newMaxStreak = maxOf(newStreak, currentState.maxStreak)
            val isFinished = (nextNote == null)

            _state.value = currentState.copy(
                currentNoteIndex = nextIndex,
                currentExpectedNote = nextNote,
                correctNotesCount = newCorrectCount,
                currentStreak = newStreak,
                maxStreak = newMaxStreak,
                lastEvaluatedResult = NoteResultType.CORRECT,
                lastPlayedNote = midiNote,
                isFinished = isFinished
            )
        } else {
            val newWrongCount = currentState.wrongNotesCount + 1
            _state.value = currentState.copy(
                wrongNotesCount = newWrongCount,
                currentStreak = 0,
                lastEvaluatedResult = NoteResultType.WRONG,
                lastPlayedNote = midiNote
            )
        }
    }

    override fun pause() {
        _state.value = _state.value.copy(isPaused = true)
    }

    override fun resume() {
        _state.value = _state.value.copy(isPaused = false)
    }

    override fun restart() {
        currentConfig?.let { startPractice(it) }
    }

    override fun stop(): PracticeResult {
        val duration = System.currentTimeMillis() - startTimeMs
        val currentState = _state.value
        val totalExpected = currentState.totalNotes
        val correct = currentState.correctNotesCount
        val wrong = currentState.wrongNotesCount
        val missed = if (currentState.isFinished) 0 else (totalExpected - correct).coerceAtLeast(0)

        val accuracy = if (totalExpected > 0) {
            ((correct.toFloat() / (correct + wrong + missed).coerceAtLeast(1)) * 100f).coerceIn(0f, 100f)
        } else {
            100f
        }

        val totalScore = (accuracy * 10).toInt() + (currentState.maxStreak * 5)
        val sessionId = UUID.randomUUID().toString()

        val config = currentConfig
        val session = PracticeSession(
            id = sessionId,
            sourceType = config?.sourceType ?: "FREE_PRACTICE",
            sourceId = config?.sourceId,
            practiceMode = config?.practiceMode ?: com.ian.pianotrainer.domain.model.PracticeMode.WAIT_FOR_NOTE,
            handMode = config?.handMode ?: com.ian.pianotrainer.domain.model.HandMode.RIGHT,
            displayMode = config?.displayMode ?: com.ian.pianotrainer.domain.model.DisplayMode.FALLING_NOTES,
            bpm = config?.bpm ?: 60,
            startedAt = startTimeMs,
            durationMs = duration,
            totalExpectedNotes = totalExpected,
            correctNotes = correct,
            wrongNotes = wrong,
            missedNotes = missed,
            earlyNotes = 0,
            lateNotes = 0,
            accuracy = accuracy,
            noteResults = recordedNoteResults.map { it.copy(sessionId = sessionId) }
        )

        return PracticeResult(
            totalScore = totalScore,
            accuracy = accuracy,
            totalExpectedNotes = totalExpected,
            correctNotes = correct,
            wrongNotes = wrong,
            missedNotes = missed,
            earlyNotes = 0,
            lateNotes = 0,
            maxStreak = currentState.maxStreak,
            durationMs = duration,
            session = session
        )
    }
}
