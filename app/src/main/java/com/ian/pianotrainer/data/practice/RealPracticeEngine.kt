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

data class ExpectedChord(
    val chordId: String,
    val startMs: Long,
    val notes: List<ExerciseNote>,
    val expectedPitches: Set<Int>
)

class RealPracticeEngine(
    private val clock: PracticeClock = SystemPracticeClock()
) : PracticeEngine {

    companion object {
        const val DEFAULT_RHYTHM_TOLERANCE_MS = 150L
    }

    fun getDynamicToleranceMs(): Long {
        val effectiveBpm = ((currentConfig?.bpm ?: 60) * speedMultiplier).coerceIn(30f, 300f)
        val beatDurationMs = 60_000.0 / effectiveBpm
        return (0.30 * beatDurationMs).toLong().coerceIn(80L, 250L)
    }

    private val _state = MutableStateFlow(PracticeEngineState())
    override val state: StateFlow<PracticeEngineState> = _state.asStateFlow()

    private var currentConfig: PracticeConfiguration? = null
    private var isLooping: Boolean = false
    private var targetDurationSec: Int = 0
    private var speedMultiplier: Float = 1.0f

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
    private var loopStartMs: Long? = null
    private var loopEndMs: Long? = null

    // Chord structures
    private var expectedChords: List<ExpectedChord> = emptyList()
    private var currentChordIndex: Int = 0
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
        loopStartMs = null
        loopEndMs = null
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

        // Build ExpectedChords from notes
        expectedChords = buildExpectedChords(notes)
        currentChordIndex = 0

        val initialChord = expectedChords.firstOrNull()
        val firstNote = initialChord?.notes?.firstOrNull()

        _state.value = PracticeEngineState(
            currentNoteIndex = 0,
            totalNotes = notes.size,
            currentExpectedNote = firstNote,
            currentExpectedNotes = initialChord?.notes ?: emptyList(),
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
            songDurationMs = songDurationMs,
            speedMultiplier = speedMultiplier,
            loopStartMs = null,
            loopEndMs = null,
            lapCount = 1,
            isTargetDurationReached = false,
            targetDurationSeconds = targetDurationSeconds,
            practiceMode = configuration.practiceMode
        )
    }

    private fun buildExpectedChords(notes: List<ExerciseNote>): List<ExpectedChord> {
        if (notes.isEmpty()) return emptyList()

        val sortedNotes = notes.sortedWith(compareBy({ it.startMs }, { it.midiNote }))
        val chords = mutableListOf<ExpectedChord>()
        var currentGroup = mutableListOf<ExerciseNote>()
        var currentChordId = ""
        var anchorStartMs = -1000L

        for (note in sortedNotes) {
            val noteChordId = note.chordId
            val isSameChord = if (!noteChordId.isNullOrBlank() && currentChordId.isNotBlank()) {
                noteChordId == currentChordId
            } else {
                anchorStartMs >= 0 && abs(note.startMs - anchorStartMs) <= 25L
            }

            if (isSameChord && currentGroup.isNotEmpty()) {
                currentGroup.add(note)
            } else {
                if (currentGroup.isNotEmpty()) {
                    val minStart = currentGroup.minOf { it.startMs }
                    chords.add(
                        ExpectedChord(
                            chordId = currentChordId.ifBlank { "chord_${chords.size + 1}" },
                            startMs = minStart,
                            notes = currentGroup.toList(),
                            expectedPitches = currentGroup.map { it.midiNote }.toSet()
                        )
                    )
                }
                currentGroup = mutableListOf(note)
                currentChordId = noteChordId ?: ""
                anchorStartMs = note.startMs
            }
        }

        if (currentGroup.isNotEmpty()) {
            val minStart = currentGroup.minOf { it.startMs }
            chords.add(
                ExpectedChord(
                    chordId = currentChordId.ifBlank { "chord_${chords.size + 1}" },
                    startMs = minStart,
                    notes = currentGroup.toList(),
                    expectedPitches = currentGroup.map { it.midiNote }.toSet()
                )
            )
        }

        return chords
    }

    private fun anchorTimeline(nowMonotonicMs: Long) {
        val deltaRealMs = nowMonotonicMs - lastResumeMonotonicMs
        accumulatedActiveMs += deltaRealMs
        if (currentConfig?.practiceMode == PracticeMode.RHYTHM) {
            basePositionMs += (deltaRealMs * speedMultiplier).toLong()
        }
        lastResumeMonotonicMs = nowMonotonicMs
    }

    override fun setPlaybackSpeed(speed: Float) {
        val now = clock.elapsedRealtime()
        if (!isCurrentlyPaused && !_state.value.isFinished) {
            anchorTimeline(now)
        }
        speedMultiplier = speed.coerceIn(0.25f, 1.5f)
        _state.value = _state.value.copy(speedMultiplier = speedMultiplier)
    }

    override fun setLooping(enabled: Boolean) {
        isLooping = enabled
    }

    override fun setLoopRange(startIndex: Int, endIndex: Int) {
        val notes = currentConfig?.notes ?: return
        loopStartIndex = startIndex.coerceIn(0, notes.lastIndex)
        loopEndIndex = endIndex.coerceIn(loopStartIndex, notes.lastIndex)
        loopStartMs = notes.getOrNull(loopStartIndex)?.startMs
        loopEndMs = notes.getOrNull(loopEndIndex)?.startMs
        isLooping = true
        _state.value = _state.value.copy(
            loopStartMs = loopStartMs,
            loopEndMs = loopEndMs
        )
    }

    override fun setLoopRangeMs(startMs: Long, endMs: Long) {
        if (endMs <= startMs) return
        loopStartMs = startMs
        loopEndMs = endMs
        isLooping = true

        val notes = currentConfig?.notes ?: emptyList()
        loopStartIndex = notes.indexOfFirst { it.startMs >= startMs }.takeIf { it != -1 } ?: 0
        loopEndIndex = notes.indexOfLast { it.startMs <= endMs }.takeIf { it != -1 } ?: notes.lastIndex

        _state.value = _state.value.copy(
            loopStartMs = loopStartMs,
            loopEndMs = loopEndMs
        )
    }

    override fun clearLoop() {
        isLooping = false
        loopStartMs = null
        loopEndMs = null
        loopStartIndex = 0
        loopEndIndex = Int.MAX_VALUE
        _state.value = _state.value.copy(
            loopStartMs = null,
            loopEndMs = null
        )
    }

    override fun seekTo(targetMs: Long) {
        val now = clock.elapsedRealtime()
        if (!isCurrentlyPaused) {
            anchorTimeline(now)
        }
        val clampedMs = targetMs.coerceIn(0L, songDurationMs)
        basePositionMs = clampedMs
        currentChordHitNotes.clear()

        // Find index of first chord at or after clampedMs
        currentChordIndex = expectedChords.indexOfFirst { it.startMs >= clampedMs }.let {
            if (it == -1) expectedChords.size else it
        }

        val chord = expectedChords.getOrNull(currentChordIndex)
        val notes = currentConfig?.notes ?: emptyList()
        val noteIdx = if (chord != null) {
            notes.indexOfFirst { it.startMs >= chord.startMs }.coerceAtLeast(0)
        } else {
            notes.size
        }

        _state.value = _state.value.copy(
            currentNoteIndex = noteIdx,
            currentExpectedNote = chord?.notes?.firstOrNull(),
            currentExpectedNotes = chord?.notes ?: emptyList(),
            currentPositionMs = clampedMs,
            isFinished = (currentChordIndex >= expectedChords.size && !isLooping),
            lastEvaluatedResult = null,
            lastPlayedNote = null
        )
    }

    override fun tickTimer() {
        val currentState = _state.value
        if (currentState.isFinished || currentState.isPaused) return

        val nowMonotonic = clock.elapsedRealtime()
        val deltaRealMs = nowMonotonic - lastResumeMonotonicMs
        val currentActiveMs = accumulatedActiveMs + deltaRealMs
        val elapsedSec = currentActiveMs / 1000L
        val targetReached = targetDurationSec > 0 && elapsedSec >= targetDurationSec

        val config = currentConfig ?: return
        val notes = config.notes

        if (config.practiceMode == PracticeMode.RHYTHM) {
            val deltaScaledMs = (deltaRealMs * speedMultiplier).toLong()
            val currentPos = basePositionMs + deltaScaledMs

            var newMissed = currentState.missedNotesCount
            val toleranceMs = getDynamicToleranceMs()

            // Check if current chord(s) expired past dynamic tolerance window
            while (currentChordIndex < expectedChords.size &&
                (expectedChords[currentChordIndex].startMs + toleranceMs) < currentPos
            ) {
                val expiredChord = expectedChords[currentChordIndex]
                val unhitPitches = expiredChord.expectedPitches - currentChordHitNotes

                for (unhit in unhitPitches) {
                    newMissed++
                    recordedNoteResults.add(
                        PracticeNoteResult(
                            sessionId = "",
                            expectedMidiNote = unhit,
                            playedMidiNote = 0,
                            timingOffsetMs = 0L,
                            resultType = NoteResultType.MISSED,
                            occurredAtOffsetMs = currentActiveMs
                        )
                    )
                }

                currentChordHitNotes.clear()
                currentChordIndex++
            }

            val effectiveEndMs = loopEndMs ?: songDurationMs

            // Check if playhead has reached loop end or song duration
            if (currentPos >= effectiveEndMs && effectiveEndMs > 0) {
                if (isLooping) {
                    lapCounter++
                    val restartPos = loopStartMs ?: 0L
                    basePositionMs = restartPos
                    lastResumeMonotonicMs = nowMonotonic
                    accumulatedActiveMs = currentActiveMs
                    currentChordHitNotes.clear()

                    currentChordIndex = if (loopStartMs != null) {
                        expectedChords.indexOfFirst { it.startMs >= loopStartMs!! }.coerceAtLeast(0)
                    } else {
                        0
                    }
                    val chord = expectedChords.getOrNull(currentChordIndex)
                    val noteIdx = if (chord != null) {
                        notes.indexOfFirst { it.startMs >= chord.startMs }.coerceAtLeast(0)
                    } else {
                        0
                    }

                    _state.value = currentState.copy(
                        currentNoteIndex = noteIdx,
                        currentExpectedNote = chord?.notes?.firstOrNull(),
                        currentExpectedNotes = chord?.notes ?: emptyList(),
                        missedNotesCount = newMissed,
                        elapsedActiveSeconds = elapsedSec,
                        elapsedActiveMs = currentActiveMs,
                        currentPositionMs = restartPos,
                        lapCount = lapCounter,
                        isTargetDurationReached = targetReached
                    )
                    return
                } else {
                    _state.value = currentState.copy(
                        currentNoteIndex = notes.size,
                        currentExpectedNote = null,
                        currentExpectedNotes = emptyList(),
                        missedNotesCount = newMissed,
                        elapsedActiveSeconds = elapsedSec,
                        elapsedActiveMs = currentActiveMs,
                        currentPositionMs = songDurationMs,
                        isFinished = true,
                        isTargetDurationReached = targetReached
                    )
                    return
                }
            }

            val activeChord = expectedChords.getOrNull(currentChordIndex)
            val noteIdx = if (activeChord != null) {
                notes.indexOfFirst { it.startMs >= activeChord.startMs }.coerceAtLeast(0)
            } else {
                notes.size
            }

            _state.value = currentState.copy(
                currentNoteIndex = noteIdx,
                currentExpectedNote = activeChord?.notes?.firstOrNull(),
                currentExpectedNotes = activeChord?.notes ?: emptyList(),
                missedNotesCount = newMissed,
                elapsedActiveSeconds = elapsedSec,
                elapsedActiveMs = currentActiveMs,
                currentPositionMs = currentPos,
                isTargetDurationReached = targetReached
            )
        } else {
            // WAIT_FOR_NOTE mode: Playhead sits at expected chord startMs
            val activeChord = expectedChords.getOrNull(currentChordIndex)
            val currentPos = activeChord?.startMs ?: 0L
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
        if (notes.isEmpty() || expectedChords.isEmpty()) return

        val nowMonotonic = clock.elapsedRealtime()
        val currentActiveMs = accumulatedActiveMs + (nowMonotonic - lastResumeMonotonicMs)

        if (config.practiceMode == PracticeMode.RHYTHM) {
            val deltaScaledMs = ((nowMonotonic - lastResumeMonotonicMs) * speedMultiplier).toLong()
            val currentPos = basePositionMs + deltaScaledMs
            val chord = expectedChords.getOrNull(currentChordIndex)

            if (chord != null) {
                val timingDiff = currentPos - chord.startMs
                val isPitchMatch = (midiNote in chord.expectedPitches)
                val toleranceMs = getDynamicToleranceMs()

                if (isPitchMatch && abs(timingDiff) <= toleranceMs) {
                    if (midiNote !in currentChordHitNotes) {
                        currentChordHitNotes.add(midiNote)

                        val resultType = when {
                            timingDiff < -45L -> NoteResultType.EARLY
                            timingDiff > 45L -> NoteResultType.LATE
                            else -> NoteResultType.CORRECT
                        }

                        val feedback = com.ian.pianotrainer.domain.model.VisualNoteFeedback(
                            chordId = chord.chordId,
                            midiNote = midiNote,
                            startMs = chord.startMs,
                            result = resultType,
                            eventTimestampMs = currentActiveMs
                        )

                        recordedNoteResults.add(
                            PracticeNoteResult(
                                sessionId = "",
                                expectedMidiNote = midiNote,
                                playedMidiNote = midiNote,
                                timingOffsetMs = timingDiff,
                                resultType = resultType,
                                occurredAtOffsetMs = currentActiveMs
                            )
                        )

                        val newCorrect = currentState.correctNotesCount + 1
                        val newStreak = currentState.currentStreak + 1
                        val newMaxStreak = maxOf(newStreak, currentState.maxStreak)
                        val newEarly = if (resultType == NoteResultType.EARLY) currentState.earlyNotesCount + 1 else currentState.earlyNotesCount
                        val newLate = if (resultType == NoteResultType.LATE) currentState.lateNotesCount + 1 else currentState.lateNotesCount

                        // If all expected pitches for chord are satisfied, advance to next chord immediately
                        if (currentChordHitNotes.containsAll(chord.expectedPitches)) {
                            currentChordHitNotes.clear()
                            currentChordIndex++
                            val nextChord = expectedChords.getOrNull(currentChordIndex)
                            val nextNoteIdx = if (nextChord != null) {
                                notes.indexOfFirst { it.startMs >= nextChord.startMs }.coerceAtLeast(0)
                            } else {
                                notes.size
                            }

                            _state.value = currentState.copy(
                                currentNoteIndex = nextNoteIdx,
                                currentExpectedNote = nextChord?.notes?.firstOrNull(),
                                currentExpectedNotes = nextChord?.notes ?: emptyList(),
                                correctNotesCount = newCorrect,
                                earlyNotesCount = newEarly,
                                lateNotesCount = newLate,
                                currentStreak = newStreak,
                                maxStreak = newMaxStreak,
                                lastEvaluatedResult = resultType,
                                lastPlayedNote = midiNote,
                                activeFeedback = feedback
                            )
                        } else {
                            // Partial chord hit: stay at current chord
                            _state.value = currentState.copy(
                                correctNotesCount = newCorrect,
                                earlyNotesCount = newEarly,
                                lateNotesCount = newLate,
                                currentStreak = newStreak,
                                maxStreak = newMaxStreak,
                                lastEvaluatedResult = resultType,
                                lastPlayedNote = midiNote,
                                activeFeedback = feedback
                            )
                        }
                    }
                } else {
                    val feedback = com.ian.pianotrainer.domain.model.VisualNoteFeedback(
                        chordId = chord.chordId,
                        midiNote = midiNote,
                        startMs = chord.startMs,
                        result = NoteResultType.WRONG,
                        eventTimestampMs = currentActiveMs
                    )

                    // Wrong pitch or played out of tolerance window
                    recordedNoteResults.add(
                        PracticeNoteResult(
                            sessionId = "",
                            expectedMidiNote = chord.expectedPitches.firstOrNull() ?: 0,
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
                        lastPlayedNote = midiNote,
                        activeFeedback = feedback
                    )
                }
            }
        } else {
            // WAIT_FOR_NOTE mode:
            val chord = expectedChords.getOrNull(currentChordIndex) ?: return

            if (midiNote in chord.expectedPitches) {
                if (midiNote !in currentChordHitNotes) {
                    currentChordHitNotes.add(midiNote)

                    val feedback = com.ian.pianotrainer.domain.model.VisualNoteFeedback(
                        chordId = chord.chordId,
                        midiNote = midiNote,
                        startMs = chord.startMs,
                        result = NoteResultType.CORRECT,
                        eventTimestampMs = currentActiveMs
                    )

                    recordedNoteResults.add(
                        PracticeNoteResult(
                            sessionId = "",
                            expectedMidiNote = midiNote,
                            playedMidiNote = midiNote,
                            timingOffsetMs = 0L,
                            resultType = NoteResultType.CORRECT,
                            occurredAtOffsetMs = currentActiveMs
                        )
                    )

                    val newCorrect = currentState.correctNotesCount + 1
                    val newStreak = currentState.currentStreak + 1
                    val newMaxStreak = maxOf(newStreak, currentState.maxStreak)

                    if (currentChordHitNotes.containsAll(chord.expectedPitches)) {
                        // Full chord complete
                        currentChordHitNotes.clear()
                        currentChordIndex++

                        val isBeyondLoopEnd = if (isLooping) {
                            if (loopEndMs != null) {
                                val nextChord = expectedChords.getOrNull(currentChordIndex)
                                nextChord == null || nextChord.startMs > loopEndMs!!
                            } else {
                                currentChordIndex >= expectedChords.size
                            }
                        } else {
                            currentChordIndex >= expectedChords.size
                        }

                        if (!isBeyondLoopEnd) {
                            val nextChord = expectedChords[currentChordIndex]
                            val nextNoteIdx = notes.indexOfFirst { it.startMs >= nextChord.startMs }.coerceAtLeast(0)
                            _state.value = currentState.copy(
                                currentNoteIndex = nextNoteIdx,
                                currentExpectedNote = nextChord.notes.firstOrNull(),
                                currentExpectedNotes = nextChord.notes,
                                currentPositionMs = nextChord.startMs,
                                correctNotesCount = newCorrect,
                                currentStreak = newStreak,
                                maxStreak = newMaxStreak,
                                lastEvaluatedResult = NoteResultType.CORRECT,
                                lastPlayedNote = midiNote,
                                activeFeedback = feedback
                            )
                        } else {
                            if (isLooping) {
                                lapCounter++
                                currentChordIndex = if (loopStartMs != null) {
                                    val idx = expectedChords.indexOfFirst { it.startMs >= loopStartMs!! }
                                    if (idx >= 0) idx else 0
                                } else {
                                    0
                                }
                                val firstChord = expectedChords.getOrNull(currentChordIndex)
                                val firstNoteIdx = if (firstChord != null) {
                                    notes.indexOfFirst { it.startMs >= firstChord.startMs }.coerceAtLeast(0)
                                } else 0

                                _state.value = currentState.copy(
                                    currentNoteIndex = firstNoteIdx,
                                    currentExpectedNote = firstChord?.notes?.firstOrNull(),
                                    currentExpectedNotes = firstChord?.notes ?: emptyList(),
                                    currentPositionMs = firstChord?.startMs ?: (loopStartMs ?: 0L),
                                    correctNotesCount = newCorrect,
                                    currentStreak = newStreak,
                                    maxStreak = newMaxStreak,
                                    lastEvaluatedResult = NoteResultType.CORRECT,
                                    lastPlayedNote = midiNote,
                                    lapCount = lapCounter,
                                    activeFeedback = feedback,
                                    isFinished = false
                                )
                            } else {
                                _state.value = currentState.copy(
                                    currentNoteIndex = notes.size,
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
                        // Partial chord hit: stay at current chord, keep hit notes
                        _state.value = currentState.copy(
                            correctNotesCount = newCorrect,
                            currentStreak = newStreak,
                            maxStreak = newMaxStreak,
                            lastEvaluatedResult = NoteResultType.CORRECT,
                            lastPlayedNote = midiNote
                        )
                    }
                }
            } else {
                // Wrong note hit (extra note)
                recordedNoteResults.add(
                    PracticeNoteResult(
                        sessionId = "",
                        expectedMidiNote = chord.expectedPitches.firstOrNull() ?: 0,
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
        anchorTimeline(clock.elapsedRealtime())
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
            anchorTimeline(clock.elapsedRealtime())
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
