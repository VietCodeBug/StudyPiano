package com.ian.pianotrainer.data.practice

import com.ian.pianotrainer.core.music.PracticeClock
import com.ian.pianotrainer.core.music.SystemPracticeClock
import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.NoteResultType
import com.ian.pianotrainer.domain.model.PracticeConfiguration
import com.ian.pianotrainer.domain.model.PracticeMode
import com.ian.pianotrainer.domain.model.PracticeNoteResult
import com.ian.pianotrainer.domain.model.PracticeResult
import com.ian.pianotrainer.domain.model.PracticeSession
import com.ian.pianotrainer.domain.service.PracticeEngine
import com.ian.pianotrainer.domain.service.PracticeEngineState
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RealPracticeEngine(
    private val clock: PracticeClock = SystemPracticeClock()
) : PracticeEngine {

    companion object {
        const val RHYTHM_TOLERANCE_MS = 180L
    }

    private val _state = MutableStateFlow(PracticeEngineState())
    override val state: StateFlow<PracticeEngineState> = _state.asStateFlow()

    private var currentConfig: PracticeConfiguration? = null
    private var isLooping: Boolean = false
    private var targetDurationSec: Int = 0

    private var sessionWallStartTimeMs: Long = 0L
    private var lastResumeMonotonicMs: Long = 0L
    private var accumulatedActiveMs: Long = 0L
    private var isCurrentlyPaused: Boolean = false

    private var songDurationMs: Long = 0L
    private var basePositionMs: Long = 0L
    private var lapCounter: Int = 1
    private val recordedNoteResults = mutableListOf<PracticeNoteResult>()

    private var loopStartIndex: Int = 0
    private var loopEndIndex: Int = Int.MAX_VALUE

    // Chord tracking: keeps track of MIDI notes in the current expected chord that have been hit
    private val currentChordHitNotes = mutableSetOf<Int>()

    override fun startPractice(
        configuration: PracticeConfiguration,
        isLoopingEnabled: Boolean,
        targetDurationSeconds: Int
    ) {
        currentConfig = configuration
        isLooping = isLoopingEnabled
        targetDurationSec = targetDurationSeconds
        loopStartIndex = 0
        loopEndIndex = Int.MAX_VALUE
        currentChordHitNotes.clear()

        sessionWallStartTimeMs = clock.currentTimeMillis()
        lastResumeMonotonicMs = clock.elapsedRealtime()
        accumulatedActiveMs = 0L
        basePositionMs = 0L
        isCurrentlyPaused = false
        lapCounter = 1
        recordedNoteResults.clear()

        val rawNotes = configuration.notes
        val notes = if (rawNotes.size > 1 && rawNotes.all { it.startMs == 0L }) {
            val msPerBeat = (60000.0 / configuration.bpm.coerceIn(30, 240))
            var curMs = 0L
            rawNotes.map { n ->
                val durMs = (n.durationBeats * msPerBeat).toLong().coerceAtLeast(150L)
                val note = n.copy(startMs = curMs, durationMs = durMs)
                curMs += durMs
                note
            }
        } else {
            rawNotes
        }
        currentConfig = configuration.copy(notes = notes)
        songDurationMs = if (notes.isNotEmpty()) {
            val lastNote = notes.maxByOrNull { it.startMs + it.durationMs }
            (lastNote?.let { it.startMs + it.durationMs } ?: 0L).coerceAtLeast(1000L)
        } else {
            0L
        }

        val firstNote = notes.firstOrNull()
        val initialExpectedNotes = getExpectedNotesForIndex(0, notes)

        _state.value = PracticeEngineState(
            currentNoteIndex = 0,
            totalNotes = notes.size,
            currentExpectedNote = firstNote,
            currentExpectedNotes = initialExpectedNotes,
            correctNotesCount = 0,
            wrongNotesCount = 0,
            missedNotesCount = 0,
            earlyNotesCount = 0,
            lateNotesCount = 0,
            currentStreak = 0,
            maxStreak = 0,
            lastEvaluatedResult = null,
            lastPlayedNote = null,
            isFinished = notes.isEmpty(),
            isPaused = false,
            elapsedActiveSeconds = 0L,
            elapsedActiveMs = 0L,
            currentPositionMs = firstNote?.startMs ?: 0L,
            lapCount = 1,
            isTargetDurationReached = false,
            targetDurationSeconds = targetDurationSeconds,
            practiceMode = configuration.practiceMode
        )
    }

    override fun setLooping(enabled: Boolean) {
        isLooping = enabled
    }

    override fun setLoopRange(startIndex: Int, endIndex: Int) {
        loopStartIndex = startIndex.coerceAtLeast(0)
        loopEndIndex = endIndex.coerceAtLeast(loopStartIndex)
        isLooping = true
    }

    private fun getExpectedNotesForIndex(index: Int, notes: List<ExerciseNote>): List<ExerciseNote> {
        if (index !in notes.indices) return emptyList()
        val anchor = notes[index]
        // Group all notes that start within 25ms of the anchor note (chord)
        val chordNotes = mutableListOf<ExerciseNote>()
        var i = index
        while (i < notes.size && abs(notes[i].startMs - anchor.startMs) <= 25L) {
            chordNotes.add(notes[i])
            i++
        }
        return chordNotes
    }

    override fun tickTimer() {
        val currentState = _state.value
        if (currentState.isFinished || currentState.isPaused) return

        val nowMonotonic = clock.elapsedRealtime()
        val currentActiveMs = accumulatedActiveMs + (nowMonotonic - lastResumeMonotonicMs)
        val elapsedSec = currentActiveMs / 1000L
        val targetReached = targetDurationSec > 0 && elapsedSec >= targetDurationSec

        val config = currentConfig ?: return
        val notes = config.notes

        if (config.practiceMode == PracticeMode.RHYTHM) {
            val currentPos = basePositionMs + (nowMonotonic - lastResumeMonotonicMs)

            // Check if playhead has reached or exceeded song duration
            if (currentPos >= songDurationMs && songDurationMs > 0) {
                if (isLooping) {
                    lapCounter++
                    basePositionMs = 0L
                    lastResumeMonotonicMs = nowMonotonic
                    accumulatedActiveMs = currentActiveMs
                    _state.value = currentState.copy(
                        currentNoteIndex = 0,
                        currentExpectedNote = notes.firstOrNull(),
                        elapsedActiveSeconds = elapsedSec,
                        elapsedActiveMs = currentActiveMs,
                        currentPositionMs = 0L,
                        lapCount = lapCounter,
                        isTargetDurationReached = targetReached
                    )
                    return
                } else {
                    _state.value = currentState.copy(
                        elapsedActiveSeconds = elapsedSec,
                        elapsedActiveMs = currentActiveMs,
                        currentPositionMs = songDurationMs,
                        isFinished = true,
                        isTargetDurationReached = targetReached
                    )
                    return
                }
            }

            // In Rhythm mode, advance expected note and record misses for notes whose window expired
            var activeIndex = currentState.currentNoteIndex
            var newMissed = currentState.missedNotesCount

            while (activeIndex < notes.size && (notes[activeIndex].startMs + RHYTHM_TOLERANCE_MS) < currentPos) {
                newMissed++
                recordedNoteResults.add(
                    PracticeNoteResult(
                        sessionId = "",
                        expectedMidiNote = notes[activeIndex].midiNote,
                        playedMidiNote = 0,
                        timingOffsetMs = 0L,
                        resultType = NoteResultType.MISSED,
                        occurredAtOffsetMs = currentActiveMs
                    )
                )
                activeIndex++
            }

            _state.value = currentState.copy(
                currentNoteIndex = activeIndex,
                currentExpectedNote = notes.getOrNull(activeIndex),
                missedNotesCount = newMissed,
                elapsedActiveSeconds = elapsedSec,
                elapsedActiveMs = currentActiveMs,
                currentPositionMs = currentPos,
                isTargetDurationReached = targetReached
            )
        } else {
            // WAIT_FOR_NOTE mode: Playhead sits at expected note startMs
            val currentPos = currentState.currentExpectedNote?.startMs ?: 0L
            _state.value = currentState.copy(
                elapsedActiveSeconds = elapsedSec,
                elapsedActiveMs = currentActiveMs,
                currentPositionMs = currentPos,
                isTargetDurationReached = targetReached
            )
        }
    }

    override fun processPlayedNote(midiNote: Int, velocity: Int) {
        val currentState = _state.value
        if (currentState.isFinished || currentState.isPaused) return

        val config = currentConfig ?: return
        val notes = config.notes
        if (notes.isEmpty()) return

        val nowMonotonic = clock.elapsedRealtime()
        val currentActiveMs = accumulatedActiveMs + (nowMonotonic - lastResumeMonotonicMs)

        if (config.practiceMode == PracticeMode.RHYTHM) {
            val currentPos = basePositionMs + (nowMonotonic - lastResumeMonotonicMs)
            val expected = currentState.currentExpectedNote

            if (expected != null) {
                val timingDiff = currentPos - expected.startMs
                val isPitchMatch = (expected.midiNote == midiNote)

                if (isPitchMatch && abs(timingDiff) <= RHYTHM_TOLERANCE_MS) {
                    val resultType = when {
                        timingDiff < -50L -> NoteResultType.EARLY
                        timingDiff > 50L -> NoteResultType.LATE
                        else -> NoteResultType.CORRECT
                    }

                    recordedNoteResults.add(
                        PracticeNoteResult(
                            sessionId = "",
                            expectedMidiNote = expected.midiNote,
                            playedMidiNote = midiNote,
                            timingOffsetMs = timingDiff,
                            resultType = resultType,
                            occurredAtOffsetMs = currentActiveMs
                        )
                    )

                    val nextIndex = currentState.currentNoteIndex + 1
                    val newCorrect = currentState.correctNotesCount + 1
                    val newStreak = currentState.currentStreak + 1
                    val newMaxStreak = maxOf(newStreak, currentState.maxStreak)
                    val newEarly = if (resultType == NoteResultType.EARLY) currentState.earlyNotesCount + 1 else currentState.earlyNotesCount
                    val newLate = if (resultType == NoteResultType.LATE) currentState.lateNotesCount + 1 else currentState.lateNotesCount

                    _state.value = currentState.copy(
                        currentNoteIndex = nextIndex,
                        currentExpectedNote = notes.getOrNull(nextIndex),
                        correctNotesCount = newCorrect,
                        earlyNotesCount = newEarly,
                        lateNotesCount = newLate,
                        currentStreak = newStreak,
                        maxStreak = newMaxStreak,
                        lastEvaluatedResult = resultType,
                        lastPlayedNote = midiNote
                    )
                } else {
                    // Wrong pitch or played out of sync
                    recordedNoteResults.add(
                        PracticeNoteResult(
                            sessionId = "",
                            expectedMidiNote = expected.midiNote,
                            playedMidiNote = midiNote,
                            timingOffsetMs = timingDiff,
                            resultType = NoteResultType.WRONG,
                            occurredAtOffsetMs = currentActiveMs
                        )
                    )

                    _state.value = currentState.copy(
                        wrongNotesCount = currentState.wrongNotesCount + 1,
                        currentStreak = 0,
                        lastEvaluatedResult = NoteResultType.WRONG,
                        lastPlayedNote = midiNote
                    )
                }
            }
        } else {
            // WAIT_FOR_NOTE mode:
            val expectedChord = currentState.currentExpectedNotes.ifEmpty {
                listOfNotNull(currentState.currentExpectedNote)
            }
            if (expectedChord.isEmpty()) return

            // Check if played note matches any remaining expected note in current chord
            val matchingNote = expectedChord.firstOrNull { it.midiNote == midiNote && it.midiNote !in currentChordHitNotes }

            if (matchingNote != null) {
                // Correct key for part of the chord
                currentChordHitNotes.add(midiNote)

                recordedNoteResults.add(
                    PracticeNoteResult(
                        sessionId = "",
                        expectedMidiNote = matchingNote.midiNote,
                        playedMidiNote = midiNote,
                        timingOffsetMs = 0L,
                        resultType = NoteResultType.CORRECT,
                        occurredAtOffsetMs = currentActiveMs
                    )
                )

                val newCorrect = currentState.correctNotesCount + 1
                val newStreak = currentState.currentStreak + 1
                val newMaxStreak = maxOf(newStreak, currentState.maxStreak)

                val allChordHit = expectedChord.all { it.midiNote in currentChordHitNotes }

                if (allChordHit) {
                    // Full chord completed, advance to next chord / note
                    currentChordHitNotes.clear()
                    val chordSize = expectedChord.size
                    var nextIndex = currentState.currentNoteIndex + chordSize

                    // Check loop range limit
                    val isBeyondLoopEnd = nextIndex > loopEndIndex || nextIndex >= notes.size

                    if (!isBeyondLoopEnd) {
                        val nextNote = notes[nextIndex]
                        val nextChord = getExpectedNotesForIndex(nextIndex, notes)
                        _state.value = currentState.copy(
                            currentNoteIndex = nextIndex,
                            currentExpectedNote = nextNote,
                            currentExpectedNotes = nextChord,
                            currentPositionMs = nextNote.startMs,
                            correctNotesCount = newCorrect,
                            currentStreak = newStreak,
                            maxStreak = newMaxStreak,
                            lastEvaluatedResult = NoteResultType.CORRECT,
                            lastPlayedNote = midiNote
                        )
                    } else {
                        if (isLooping) {
                            lapCounter++
                            val targetStart = loopStartIndex.coerceIn(0, notes.lastIndex)
                            val first = notes.getOrNull(targetStart)
                            val firstChord = getExpectedNotesForIndex(targetStart, notes)
                            _state.value = currentState.copy(
                                currentNoteIndex = targetStart,
                                currentExpectedNote = first,
                                currentExpectedNotes = firstChord,
                                currentPositionMs = first?.startMs ?: 0L,
                                correctNotesCount = newCorrect,
                                currentStreak = newStreak,
                                maxStreak = newMaxStreak,
                                lastEvaluatedResult = NoteResultType.CORRECT,
                                lastPlayedNote = midiNote,
                                lapCount = lapCounter,
                                isFinished = false
                            )
                        } else {
                            _state.value = currentState.copy(
                                currentNoteIndex = nextIndex,
                                currentExpectedNote = null,
                                currentExpectedNotes = emptyList(),
                                currentPositionMs = songDurationMs,
                                correctNotesCount = newCorrect,
                                currentStreak = newStreak,
                                maxStreak = newMaxStreak,
                                lastEvaluatedResult = NoteResultType.CORRECT,
                                lastPlayedNote = midiNote,
                                isFinished = true
                            )
                        }
                    }
                } else {
                    // Partial chord hit: stay at current chord, update streak & stats
                    _state.value = currentState.copy(
                        correctNotesCount = newCorrect,
                        currentStreak = newStreak,
                        maxStreak = newMaxStreak,
                        lastEvaluatedResult = NoteResultType.CORRECT,
                        lastPlayedNote = midiNote
                    )
                }
            } else {
                // Wrong note
                val expectedNote = currentState.currentExpectedNote
                recordedNoteResults.add(
                    PracticeNoteResult(
                        sessionId = "",
                        expectedMidiNote = expectedNote?.midiNote ?: 0,
                        playedMidiNote = midiNote,
                        timingOffsetMs = 0L,
                        resultType = NoteResultType.WRONG,
                        occurredAtOffsetMs = currentActiveMs
                    )
                )

                _state.value = currentState.copy(
                    wrongNotesCount = currentState.wrongNotesCount + 1,
                    currentStreak = 0,
                    lastEvaluatedResult = NoteResultType.WRONG,
                    lastPlayedNote = midiNote
                )
            }
        }
    }

    override fun pause() {
        if (isCurrentlyPaused) return
        val now = clock.elapsedRealtime()
        val delta = (now - lastResumeMonotonicMs)
        accumulatedActiveMs += delta
        basePositionMs += delta
        isCurrentlyPaused = true
        _state.value = _state.value.copy(
            isPaused = true,
            elapsedActiveSeconds = accumulatedActiveMs / 1000L,
            elapsedActiveMs = accumulatedActiveMs
        )
    }

    override fun resume() {
        if (!isCurrentlyPaused) return
        lastResumeMonotonicMs = clock.elapsedRealtime()
        isCurrentlyPaused = false
        _state.value = _state.value.copy(isPaused = false)
    }

    override fun restart() {
        currentConfig?.let { startPractice(it, isLooping, targetDurationSec) }
    }

    override fun stop(): PracticeResult {
        if (!isCurrentlyPaused) {
            val now = clock.elapsedRealtime()
            val delta = (now - lastResumeMonotonicMs)
            accumulatedActiveMs += delta
            basePositionMs += delta
            isCurrentlyPaused = true
        }

        val currentState = _state.value
        val totalExpected = if (isLooping) {
            currentState.correctNotesCount + currentState.wrongNotesCount + currentState.missedNotesCount
        } else {
            currentState.totalNotes
        }

        val correct = currentState.correctNotesCount
        val wrong = currentState.wrongNotesCount
        val missed = if (currentState.isFinished || isLooping) {
            currentState.missedNotesCount
        } else {
            (totalExpected - correct).coerceAtLeast(0)
        }

        val evaluatedTotal = (correct + wrong + missed).coerceAtLeast(1)
        val accuracy = if (correct + wrong + missed == 0) {
            100f
        } else {
            ((correct.toFloat() / evaluatedTotal) * 100f).coerceIn(0f, 100f)
        }

        val totalScore = (accuracy * 10).toInt() + (currentState.maxStreak * 5)
        val sessionId = UUID.randomUUID().toString()

        val config = currentConfig
        val session = PracticeSession(
            id = sessionId,
            sourceType = config?.sourceType ?: "EXERCISE",
            sourceId = config?.sourceId,
            practiceMode = config?.practiceMode ?: PracticeMode.WAIT_FOR_NOTE,
            handMode = config?.handMode ?: com.ian.pianotrainer.domain.model.HandMode.RIGHT,
            displayMode = config?.displayMode ?: com.ian.pianotrainer.domain.model.DisplayMode.FALLING_NOTES,
            bpm = config?.bpm ?: 60,
            startedAt = sessionWallStartTimeMs,
            durationMs = accumulatedActiveMs,
            totalExpectedNotes = totalExpected,
            correctNotes = correct,
            wrongNotes = wrong,
            missedNotes = missed,
            earlyNotes = currentState.earlyNotesCount,
            lateNotes = currentState.lateNotesCount,
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
            earlyNotes = currentState.earlyNotesCount,
            lateNotes = currentState.lateNotesCount,
            maxStreak = currentState.maxStreak,
            durationMs = accumulatedActiveMs,
            session = session
        )
    }
}
