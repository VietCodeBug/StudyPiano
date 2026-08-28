package com.ian.pianotrainer.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.MidiNoteEvent
import com.ian.pianotrainer.domain.model.NoteResultType
import com.ian.pianotrainer.domain.model.PracticeConfiguration
import com.ian.pianotrainer.domain.model.PracticeMode
import com.ian.pianotrainer.domain.model.PracticeResult
import com.ian.pianotrainer.domain.model.UserSettings
import com.ian.pianotrainer.domain.repository.CurriculumRepository
import com.ian.pianotrainer.domain.repository.ProgressRepository
import com.ian.pianotrainer.domain.repository.SettingsRepository
import com.ian.pianotrainer.domain.repository.SongRepository
import com.ian.pianotrainer.domain.service.MetronomeController
import com.ian.pianotrainer.domain.service.MidiInput
import com.ian.pianotrainer.domain.service.PracticeEngine
import com.ian.pianotrainer.domain.service.PracticeEngineState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PracticePlayerUiState(
    val title: String = "",
    val sourceType: String = "",
    val sourceId: String = "",
    val practiceMode: PracticeMode = PracticeMode.WAIT_FOR_NOTE,
    val handMode: HandMode = HandMode.RIGHT,
    val displayMode: DisplayMode = DisplayMode.FALLING_NOTES,
    val bpm: Int = 60,
    val exerciseNotes: List<ExerciseNote> = emptyList(),
    val engineState: PracticeEngineState = PracticeEngineState(),
    val activePressedNotes: Set<Int> = emptySet(),
    val currentBeat: Int = 1,
    val isMetronomeRunning: Boolean = false,
    val userSettings: UserSettings = UserSettings(),
    val isFinished: Boolean = false
)

class PracticePlayerViewModel(
    private val title: String,
    private val sourceType: String,
    private val sourceId: String,
    private val handModeStr: String,
    private val displayModeStr: String,
    private val initialBpm: Int,
    private val practiceEngine: PracticeEngine,
    private val midiInput: MidiInput,
    private val metronomeController: MetronomeController,
    private val curriculumRepository: CurriculumRepository,
    private val songRepository: SongRepository,
    private val progressRepository: ProgressRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _activeNotes = MutableStateFlow<Set<Int>>(emptySet())
    private val _exerciseNotes = MutableStateFlow<List<ExerciseNote>>(emptyList())
    private val _navigateToResult = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToResult: SharedFlow<String> = _navigateToResult.asSharedFlow()

    private val handMode = runCatching { HandMode.valueOf(handModeStr) }.getOrDefault(HandMode.RIGHT)
    private val displayMode = runCatching { DisplayMode.valueOf(displayModeStr) }.getOrDefault(DisplayMode.FALLING_NOTES)

    val uiState: StateFlow<PracticePlayerUiState> = combine(
        practiceEngine.state,
        _activeNotes,
        _exerciseNotes,
        combine(
            metronomeController.currentBeat,
            metronomeController.isRunning,
            settingsRepository.userSettings
        ) { beat, isMetroRunning, settings ->
            Triple(beat, isMetroRunning, settings)
        }
    ) { engineState, activeNotes, notes, (beat, isMetroRunning, settings) ->
        PracticePlayerUiState(
            title = title,
            sourceType = sourceType,
            sourceId = sourceId,
            practiceMode = PracticeMode.WAIT_FOR_NOTE,
            handMode = handMode,
            displayMode = displayMode,
            bpm = initialBpm,
            exerciseNotes = notes,
            engineState = engineState,
            activePressedNotes = activeNotes,
            currentBeat = beat,
            isMetronomeRunning = isMetroRunning,
            userSettings = settings,
            isFinished = engineState.isFinished
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PracticePlayerUiState(
            title = title,
            sourceType = sourceType,
            sourceId = sourceId,
            bpm = initialBpm
        )
    )

    init {
        loadNotesAndStart()
        observeMidiInput()
    }

    private fun loadNotesAndStart() {
        viewModelScope.launch {
            val notes = resolveExerciseNotes()
            _exerciseNotes.value = notes

            val config = PracticeConfiguration(
                title = title,
                sourceId = sourceId,
                sourceType = sourceType,
                practiceMode = PracticeMode.WAIT_FOR_NOTE,
                handMode = handMode,
                displayMode = displayMode,
                bpm = initialBpm,
                notes = notes
            )
            practiceEngine.startPractice(config)
            metronomeController.start(initialBpm)
        }
    }

    private suspend fun resolveExerciseNotes(): List<ExerciseNote> {
        if (sourceId.startsWith("lesson_")) {
            val lesson = curriculumRepository.getLessonById(sourceId)
            if (lesson?.exercise != null && lesson.exercise.notes.isNotEmpty()) {
                return lesson.exercise.notes
            }
        } else if (sourceId.startsWith("song_")) {
            val song = songRepository.getSongById(sourceId)
            if (song != null && song.notes.isNotEmpty()) {
                return song.notes
            }
        }

        // Default / Quick drill fallbacks
        return when (sourceId) {
            "drill_c_major_5finger" -> listOf(
                ExerciseNote(60, "C4", 1.0, 1, HandMode.RIGHT),
                ExerciseNote(62, "D4", 1.0, 2, HandMode.RIGHT),
                ExerciseNote(64, "E4", 1.0, 3, HandMode.RIGHT),
                ExerciseNote(65, "F4", 1.0, 4, HandMode.RIGHT),
                ExerciseNote(67, "G4", 2.0, 5, HandMode.RIGHT)
            )
            "drill_thirds_rh" -> listOf(
                ExerciseNote(60, "C4", 1.0, 1, HandMode.RIGHT),
                ExerciseNote(64, "E4", 1.0, 3, HandMode.RIGHT),
                ExerciseNote(62, "D4", 1.0, 2, HandMode.RIGHT),
                ExerciseNote(65, "F4", 1.0, 4, HandMode.RIGHT),
                ExerciseNote(64, "E4", 1.0, 3, HandMode.RIGHT),
                ExerciseNote(67, "G4", 2.0, 5, HandMode.RIGHT)
            )
            "drill_lh_bass" -> listOf(
                ExerciseNote(48, "C3", 1.0, 5, HandMode.LEFT),
                ExerciseNote(50, "D3", 1.0, 4, HandMode.LEFT),
                ExerciseNote(52, "E3", 1.0, 3, HandMode.LEFT),
                ExerciseNote(53, "F3", 1.0, 2, HandMode.LEFT),
                ExerciseNote(55, "G3", 2.0, 1, HandMode.LEFT)
            )
            "drill_ode_to_joy", "song_demo_canon_d" -> listOf(
                ExerciseNote(64, "E4", 1.0, 3, HandMode.RIGHT),
                ExerciseNote(64, "E4", 1.0, 3, HandMode.RIGHT),
                ExerciseNote(65, "F4", 1.0, 4, HandMode.RIGHT),
                ExerciseNote(67, "G4", 1.0, 5, HandMode.RIGHT),
                ExerciseNote(67, "G4", 1.0, 5, HandMode.RIGHT),
                ExerciseNote(65, "F4", 1.0, 4, HandMode.RIGHT),
                ExerciseNote(64, "E4", 1.0, 3, HandMode.RIGHT),
                ExerciseNote(62, "D4", 1.0, 2, HandMode.RIGHT),
                ExerciseNote(60, "C4", 1.0, 1, HandMode.RIGHT),
                ExerciseNote(60, "C4", 1.0, 1, HandMode.RIGHT),
                ExerciseNote(62, "D4", 1.0, 2, HandMode.RIGHT),
                ExerciseNote(64, "E4", 1.5, 3, HandMode.RIGHT),
                ExerciseNote(62, "D4", 0.5, 2, HandMode.RIGHT),
                ExerciseNote(62, "D4", 2.0, 2, HandMode.RIGHT)
            )
            else -> listOf(
                ExerciseNote(60, "C4", 1.0, 1, HandMode.RIGHT),
                ExerciseNote(62, "D4", 1.0, 2, HandMode.RIGHT),
                ExerciseNote(64, "E4", 1.0, 3, HandMode.RIGHT),
                ExerciseNote(65, "F4", 1.0, 4, HandMode.RIGHT),
                ExerciseNote(67, "G4", 1.0, 5, HandMode.RIGHT),
                ExerciseNote(65, "F4", 1.0, 4, HandMode.RIGHT),
                ExerciseNote(64, "E4", 1.0, 3, HandMode.RIGHT),
                ExerciseNote(62, "D4", 1.0, 2, HandMode.RIGHT),
                ExerciseNote(60, "C4", 2.0, 1, HandMode.RIGHT)
            )
        }
    }

    private fun observeMidiInput() {
        viewModelScope.launch {
            midiInput.noteEvents.collect { event ->
                if (event.isNoteOn && event.velocity > 0) {
                    _activeNotes.value = _activeNotes.value + event.note
                    practiceEngine.processPlayedNote(event.note, event.velocity)
                } else {
                    _activeNotes.value = _activeNotes.value - event.note
                }
            }
        }
    }

    fun onVirtualKeyPressed(midiNote: Int) {
        midiInput.onVirtualKeyPressed(midiNote, 80)
    }

    fun onVirtualKeyReleased(midiNote: Int) {
        midiInput.onVirtualKeyReleased(midiNote)
    }

    fun togglePause() {
        if (practiceEngine.state.value.isPaused) {
            practiceEngine.resume()
            metronomeController.start(initialBpm)
        } else {
            practiceEngine.pause()
            metronomeController.stop()
        }
    }

    fun restart() {
        loadNotesAndStart()
    }

    fun finishAndSaveSession() {
        viewModelScope.launch {
            metronomeController.stop()
            val result = practiceEngine.stop()
            val session = result.session
            if (session != null) {
                progressRepository.savePracticeSession(session, session.noteResults)

                if (sourceType == "LESSON" && sourceId.isNotBlank()) {
                    val isComplete = result.accuracy >= 70f
                    curriculumRepository.updateLessonProgress(
                        lessonId = sourceId,
                        isCompleted = isComplete,
                        accuracy = result.accuracy,
                        bpm = session.bpm
                    )
                } else if (sourceType == "SONG" && sourceId.isNotBlank()) {
                    songRepository.updateLastPracticed(sourceId)
                }

                _navigateToResult.emit(session.id)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        metronomeController.stop()
    }

    class Factory(
        private val title: String,
        private val sourceType: String,
        private val sourceId: String,
        private val handMode: String,
        private val displayMode: String,
        private val bpm: Int,
        private val practiceEngine: PracticeEngine,
        private val midiInput: MidiInput,
        private val metronomeController: MetronomeController,
        private val curriculumRepository: CurriculumRepository,
        private val songRepository: SongRepository,
        private val progressRepository: ProgressRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PracticePlayerViewModel(
                title, sourceType, sourceId, handMode, displayMode, bpm,
                practiceEngine, midiInput, metronomeController,
                curriculumRepository, songRepository, progressRepository, settingsRepository
            ) as T
        }
    }
}
