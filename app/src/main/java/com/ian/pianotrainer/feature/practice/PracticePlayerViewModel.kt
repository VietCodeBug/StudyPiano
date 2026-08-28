package com.ian.pianotrainer.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.KeyboardRangeMode
import com.ian.pianotrainer.domain.model.NoteDisplaySize
import com.ian.pianotrainer.domain.model.PracticeConfiguration
import com.ian.pianotrainer.domain.model.PracticeMode
import com.ian.pianotrainer.domain.model.PracticeSession
import com.ian.pianotrainer.domain.model.UserSettings
import com.ian.pianotrainer.domain.model.VisualLookAhead
import com.ian.pianotrainer.domain.repository.CurriculumRepository
import com.ian.pianotrainer.domain.repository.ExerciseRepository
import com.ian.pianotrainer.domain.repository.ProgressRepository
import com.ian.pianotrainer.domain.repository.SettingsRepository
import com.ian.pianotrainer.domain.repository.SongRepository
import com.ian.pianotrainer.domain.service.MetronomeController
import com.ian.pianotrainer.domain.service.MidiInput
import com.ian.pianotrainer.domain.service.PracticeEngine
import com.ian.pianotrainer.domain.service.PracticeEngineState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class PracticePlayerUiState(
    val title: String = "",
    val sourceType: String = "LESSON",
    val sourceId: String = "",
    val practiceMode: PracticeMode = PracticeMode.WAIT_FOR_NOTE,
    val handMode: HandMode = HandMode.RIGHT,
    val displayMode: DisplayMode = DisplayMode.FALLING_NOTES,
    val rangeMode: KeyboardRangeMode = KeyboardRangeMode.TWO_OCTAVES,
    val noteDisplaySize: NoteDisplaySize = NoteDisplaySize.AUTO,
    val visualLookAhead: VisualLookAhead = VisualLookAhead.MEDIUM,
    val isToolbarVisible: Boolean = true,
    val bpm: Int = 60,
    val startOctave: Int = 3,
    val exerciseNotes: List<ExerciseNote> = emptyList(),
    val engineState: PracticeEngineState = PracticeEngineState(),
    val activePressedNotes: Set<Int> = emptySet(),
    val currentBeat: Int = 1,
    val isMetronomeRunning: Boolean = false,
    val isMetronomeSoundEnabled: Boolean = true,
    val userSettings: UserSettings = UserSettings(),
    val isFinished: Boolean = false,
    val isLooping: Boolean = false,
    val targetDurationSeconds: Int = 0,
    val isLoadingNotes: Boolean = true
)

class PracticePlayerViewModel(
    private val title: String,
    private val sourceType: String,
    private val sourceId: String,
    private val initialHand: HandMode,
    private val initialBpm: Int,
    private val practiceEngine: PracticeEngine,
    private val midiInput: MidiInput,
    private val metronomeController: MetronomeController,
    private val curriculumRepository: CurriculumRepository,
    private val exerciseRepository: ExerciseRepository,
    private val songRepository: SongRepository,
    private val progressRepository: ProgressRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _activeNotes = MutableStateFlow<Set<Int>>(emptySet())
    private val _exerciseNotes = MutableStateFlow<List<ExerciseNote>>(emptyList())
    private val _currentHandMode = MutableStateFlow(initialHand)
    private val _currentDisplayMode = MutableStateFlow(DisplayMode.FALLING_NOTES)
    private val _currentPracticeMode = MutableStateFlow(PracticeMode.WAIT_FOR_NOTE)
    private val _rangeMode = MutableStateFlow(KeyboardRangeMode.TWO_OCTAVES)
    private val _noteDisplaySize = MutableStateFlow(NoteDisplaySize.AUTO)
    private val _visualLookAhead = MutableStateFlow(VisualLookAhead.MEDIUM)
    private val _isToolbarVisible = MutableStateFlow(true)
    private val _currentBpm = MutableStateFlow(initialBpm)
    private val _startOctave = MutableStateFlow(if (initialHand == HandMode.LEFT) 2 else 3)
    private val _isLooping = MutableStateFlow(false)
    private val _targetDurationSeconds = MutableStateFlow(0)
    private val _isMetronomeSoundEnabled = MutableStateFlow(true)
    private val _isLoading = MutableStateFlow(true)

    private var tickJob: Job? = null

    private val _navigateToResult = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToResult: SharedFlow<String> = _navigateToResult.asSharedFlow()

    private val _configState = combine(
        _currentHandMode,
        _currentDisplayMode,
        _currentPracticeMode,
        _currentBpm,
        _startOctave,
        _rangeMode,
        _noteDisplaySize,
        _visualLookAhead,
        _isToolbarVisible
    ) { args ->
        args
    }

    val uiState: StateFlow<PracticePlayerUiState> = combine(
        practiceEngine.state,
        _activeNotes,
        _exerciseNotes,
        _configState,
        _isLooping,
        _targetDurationSeconds,
        metronomeController.currentBeat,
        metronomeController.isRunning,
        _isMetronomeSoundEnabled,
        settingsRepository.userSettings,
        _isLoading
    ) { args ->
        val engineState = args[0] as PracticeEngineState
        val activeNotes = args[1] as Set<Int>
        val notes = args[2] as List<ExerciseNote>
        val isLoop = args[4] as Boolean
        val targetSec = args[5] as Int
        val currentBeat = args[6] as Int
        val isMetro = args[7] as Boolean
        val isSound = args[8] as Boolean
        val settings = args[9] as UserSettings
        val loading = args[10] as Boolean

        val hand = _currentHandMode.value
        val display = _currentDisplayMode.value
        val mode = _currentPracticeMode.value
        val bpm = _currentBpm.value
        val octave = _startOctave.value
        val range = _rangeMode.value
        val noteSize = _noteDisplaySize.value
        val lookAhead = _visualLookAhead.value
        val toolbarVisible = _isToolbarVisible.value

        PracticePlayerUiState(
            title = title,
            sourceType = sourceType,
            sourceId = sourceId,
            practiceMode = mode,
            handMode = hand,
            displayMode = display,
            rangeMode = range,
            noteDisplaySize = noteSize,
            visualLookAhead = lookAhead,
            isToolbarVisible = toolbarVisible,
            bpm = bpm,
            startOctave = octave,
            exerciseNotes = notes,
            engineState = engineState,
            activePressedNotes = activeNotes,
            currentBeat = currentBeat,
            isMetronomeRunning = isMetro,
            isMetronomeSoundEnabled = isSound,
            userSettings = settings,
            isFinished = engineState.isFinished,
            isLooping = isLoop,
            targetDurationSeconds = targetSec,
            isLoadingNotes = loading
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
        startEngineTicker()
    }

    private fun startEngineTicker() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                delay(25) // High-precision 40Hz tick for smooth monotonic playhead updates
                if (!practiceEngine.state.value.isPaused && !practiceEngine.state.value.isFinished) {
                    practiceEngine.tickTimer()
                }
            }
        }
    }

    private fun loadNotesAndStart() {
        viewModelScope.launch {
            _isLoading.value = true
            val loadedNotes = fetchNotesForSource(sourceType, sourceId, _currentHandMode.value)
            _exerciseNotes.value = loadedNotes
            _isLoading.value = false

            if (loadedNotes.isNotEmpty()) {
                // Auto-tune start octave if all notes are high or low
                val minPitch = loadedNotes.minOf { it.midiNote }
                val maxPitch = loadedNotes.maxOf { it.midiNote }
                if (minPitch >= 60) {
                    _startOctave.value = 4
                } else if (maxPitch <= 60) {
                    _startOctave.value = 2
                }

                val config = PracticeConfiguration(
                    title = title,
                    sourceId = sourceId,
                    sourceType = sourceType,
                    practiceMode = _currentPracticeMode.value,
                    handMode = _currentHandMode.value,
                    displayMode = _currentDisplayMode.value,
                    bpm = _currentBpm.value,
                    notes = loadedNotes
                )
                practiceEngine.startPractice(
                    configuration = config,
                    isLoopingEnabled = _isLooping.value,
                    targetDurationSeconds = _targetDurationSeconds.value
                )
                if (_isMetronomeSoundEnabled.value) {
                    metronomeController.start(_currentBpm.value)
                }
            }
        }
    }

    private suspend fun fetchNotesForSource(
        sourceType: String,
        sourceId: String,
        filteredHand: HandMode
    ): List<ExerciseNote> {
        val loadedNotes: List<ExerciseNote> = when {
            sourceType == "LESSON" || sourceId.startsWith("lesson_") -> {
                val lesson = curriculumRepository.getLessonById(sourceId)
                lesson?.exercise?.notes ?: emptyList()
            }
            sourceType == "EXERCISE" || sourceType == "QUICK_DRILL" || sourceId.startsWith("ex_") -> {
                val exercise = exerciseRepository.getExerciseById(sourceId)
                exercise?.notes ?: emptyList()
            }
            sourceType == "SONG" || sourceId.startsWith("song_") -> {
                val song = songRepository.getSongById(sourceId)
                song?.notes ?: emptyList()
            }
            else -> {
                exerciseRepository.getExerciseById(sourceId)?.notes
                    ?: curriculumRepository.getLessonById(sourceId)?.exercise?.notes
                    ?: songRepository.getSongById(sourceId)?.notes
                    ?: emptyList()
            }
        }

        return if (filteredHand == HandMode.BOTH) {
            loadedNotes
        } else {
            loadedNotes.filter { it.hand == filteredHand || it.hand == HandMode.BOTH }
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

    fun setHandMode(hand: HandMode) {
        if (_currentHandMode.value != hand) {
            _currentHandMode.value = hand
            loadNotesAndStart()
        }
    }

    fun setPracticeMode(mode: PracticeMode) {
        if (_currentPracticeMode.value != mode) {
            _currentPracticeMode.value = mode
            loadNotesAndStart()
        }
    }

    fun setDisplayMode(mode: DisplayMode) {
        _currentDisplayMode.value = mode
    }

    fun setOctave(octave: Int) {
        _startOctave.value = octave.coerceIn(1, 6)
    }

    fun setRangeMode(rangeMode: KeyboardRangeMode) {
        _rangeMode.value = rangeMode
    }

    fun setNoteDisplaySize(size: NoteDisplaySize) {
        _noteDisplaySize.value = size
    }

    fun setVisualLookAhead(lookAhead: VisualLookAhead) {
        _visualLookAhead.value = lookAhead
    }

    fun toggleToolbar() {
        _isToolbarVisible.value = !_isToolbarVisible.value
    }

    fun setToolbarVisible(visible: Boolean) {
        _isToolbarVisible.value = visible
    }

    fun setBpm(newBpm: Int) {
        val clamped = newBpm.coerceIn(30, 240)
        _currentBpm.value = clamped
        if (_isMetronomeSoundEnabled.value && !practiceEngine.state.value.isPaused) {
            metronomeController.start(clamped)
        }
    }

    fun toggleMetronome() {
        val current = _isMetronomeSoundEnabled.value
        val next = !current
        _isMetronomeSoundEnabled.value = next
        if (next && !practiceEngine.state.value.isPaused) {
            metronomeController.start(_currentBpm.value)
        } else {
            metronomeController.stop()
        }
    }

    fun toggleLooping() {
        val nextState = !_isLooping.value
        _isLooping.value = nextState
        practiceEngine.setLooping(nextState)
    }

    fun togglePause() {
        val currentEngine = practiceEngine.state.value
        if (currentEngine.isPaused) {
            practiceEngine.resume()
            if (_isMetronomeSoundEnabled.value) {
                metronomeController.start(_currentBpm.value)
            }
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
            tickJob?.cancel()
            val result = practiceEngine.stop()
            val session = result.session
            if (session != null && (session.durationMs >= 2000L || session.correctNotes > 0 || session.wrongNotes > 0)) {
                progressRepository.savePracticeSession(session, session.noteResults)
                _navigateToResult.emit(session.id)
            } else {
                _navigateToResult.emit("")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        metronomeController.stop()
        tickJob?.cancel()
    }

    class Factory(
        private val title: String,
        private val sourceType: String,
        private val sourceId: String,
        private val initialHand: HandMode,
        private val initialBpm: Int,
        private val practiceEngine: PracticeEngine,
        private val midiInput: MidiInput,
        private val metronomeController: MetronomeController,
        private val curriculumRepository: CurriculumRepository,
        private val exerciseRepository: ExerciseRepository,
        private val songRepository: SongRepository,
        private val progressRepository: ProgressRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PracticePlayerViewModel(
                title = title,
                sourceType = sourceType,
                sourceId = sourceId,
                initialHand = initialHand,
                initialBpm = initialBpm,
                practiceEngine = practiceEngine,
                midiInput = midiInput,
                metronomeController = metronomeController,
                curriculumRepository = curriculumRepository,
                exerciseRepository = exerciseRepository,
                songRepository = songRepository,
                progressRepository = progressRepository,
                settingsRepository = settingsRepository
            ) as T
        }
    }
}
