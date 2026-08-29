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
import com.ian.pianotrainer.domain.model.SongPlaybackData
import com.ian.pianotrainer.domain.model.SongPracticePreset
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class PracticePlayerUiState(
    val title: String = "",
    val sourceType: String = "LESSON",
    val sourceId: String = "",
    val practiceMode: PracticeMode = PracticeMode.RHYTHM,
    val handMode: HandMode = HandMode.RIGHT,
    val displayMode: DisplayMode = DisplayMode.FALLING_NOTES,
    val rangeMode: KeyboardRangeMode = KeyboardRangeMode.TWO_OCTAVES,
    val noteDisplaySize: NoteDisplaySize = NoteDisplaySize.AUTO,
    val visualLookAhead: VisualLookAhead = VisualLookAhead.MEDIUM,
    val isToolbarVisible: Boolean = true,
    val bpm: Int = 60,
    val speedMultiplier: Float = 1.0f,
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
    val loopPointA: Long? = null,
    val loopPointB: Long? = null,
    val targetDurationSeconds: Int = 0,
    val isLoadingNotes: Boolean = true,
    val songPlaybackData: SongPlaybackData? = null,
    val isCountInActive: Boolean = false,
    val countInBeatsRemaining: Int = 0,
    val availablePresets: List<SongPracticePreset> = emptyList(),
    val gradualSpeedUpEnabled: Boolean = false,
    val isDemoMode: Boolean = false,
    val isAppSoundEnabled: Boolean = true
)

class PracticePlayerViewModel(
    private val title: String,
    private val sourceType: String,
    private val sourceId: String,
    private val initialHand: HandMode,
    private val initialBpm: Int,
    private val initialPracticeMode: PracticeMode = PracticeMode.RHYTHM,
    private val practiceEngine: PracticeEngine,
    private val midiInput: MidiInput,
    private val metronomeController: MetronomeController,
    private val curriculumRepository: CurriculumRepository,
    private val exerciseRepository: ExerciseRepository,
    private val songRepository: SongRepository,
    private val progressRepository: ProgressRepository,
    private val settingsRepository: SettingsRepository,
    private val pianoAudioEngine: com.ian.pianotrainer.core.audio.PianoAudioEngine,
    private val midiPlaybackScheduler: com.ian.pianotrainer.core.audio.MidiPlaybackScheduler
) : ViewModel() {

    private val _activeNotes = MutableStateFlow<Set<Int>>(emptySet())
    private val _exerciseNotes = MutableStateFlow<List<ExerciseNote>>(emptyList())
    private val _currentHandMode = MutableStateFlow(initialHand)
    private val _currentDisplayMode = MutableStateFlow(DisplayMode.FALLING_NOTES)
    private val _currentPracticeMode = MutableStateFlow(initialPracticeMode)
    private val _rangeMode = MutableStateFlow(KeyboardRangeMode.TWO_OCTAVES)
    private val _noteDisplaySize = MutableStateFlow(NoteDisplaySize.AUTO)
    private val _visualLookAhead = MutableStateFlow(VisualLookAhead.MEDIUM)
    private val _isToolbarVisible = MutableStateFlow(true)
    private val _currentBpm = MutableStateFlow(initialBpm)
    private val _speedMultiplier = MutableStateFlow(1.0f)
    private val _startOctave = MutableStateFlow(if (initialHand == HandMode.LEFT) 2 else 3)
    private val _isLooping = MutableStateFlow(false)
    private val _loopPointA = MutableStateFlow<Long?>(null)
    private val _loopPointB = MutableStateFlow<Long?>(null)
    private val _targetDurationSeconds = MutableStateFlow(0)
    private val _isMetronomeSoundEnabled = MutableStateFlow(true)
    private val _isLoading = MutableStateFlow(true)
    private val _songPlaybackData = MutableStateFlow<SongPlaybackData?>(null)
    private val _isCountInActive = MutableStateFlow(false)
    private val _countInBeatsRemaining = MutableStateFlow(0)
    private val _gradualSpeedUpEnabled = MutableStateFlow(false)
    private val _availablePresets = MutableStateFlow<List<SongPracticePreset>>(emptyList())
    private val _isDemoMode = MutableStateFlow(false)
    private val _isAppSoundEnabled = MutableStateFlow(true)

    private var tickJob: Job? = null
    private var countInJob: Job? = null
    private var lastActiveMetronomeBpm: Int = -1

    private val _navigateToResult = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToResult: SharedFlow<String> = _navigateToResult.asSharedFlow()

    // 1. Engine & Notes Group
    private data class EngineNotesGroup(
        val engineState: PracticeEngineState,
        val activeNotes: Set<Int>,
        val notes: List<ExerciseNote>
    )

    private val engineNotesFlow = combine(
        practiceEngine.state,
        _activeNotes,
        _exerciseNotes
    ) { state, active, notes ->
        EngineNotesGroup(state, active, notes)
    }

    // 2. Playback Config Group
    private data class PlaybackConfigGroup(
        val hand: HandMode,
        val display: DisplayMode,
        val mode: PracticeMode,
        val bpm: Int,
        val speed: Float,
        val isDemo: Boolean,
        val soundEnabled: Boolean
    )

    private val playbackConfigFlow = combine(
        combine(_currentHandMode, _currentDisplayMode, _currentPracticeMode) { hand, display, mode ->
            Triple(hand, display, mode)
        },
        combine(_currentBpm, _speedMultiplier, _isDemoMode, _isAppSoundEnabled) { bpm, speed, isDemo, sound ->
            listOf(bpm, speed, isDemo, sound)
        }
    ) { p1, p2 ->
        PlaybackConfigGroup(
            hand = p1.first,
            display = p1.second,
            mode = p1.third,
            bpm = p2[0] as Int,
            speed = p2[1] as Float,
            isDemo = p2[2] as Boolean,
            soundEnabled = p2[3] as Boolean
        )
    }

    // 3. Layout & Geometry Config Group
    private data class LayoutConfigGroup(
        val octave: Int,
        val range: KeyboardRangeMode,
        val noteSize: NoteDisplaySize,
        val lookAhead: VisualLookAhead,
        val toolbarVisible: Boolean
    )

    private val layoutConfigFlow = combine(
        _startOctave,
        _rangeMode,
        _noteDisplaySize,
        _visualLookAhead,
        _isToolbarVisible
    ) { octave, range, noteSize, lookAhead, toolbarVisible ->
        LayoutConfigGroup(octave, range, noteSize, lookAhead, toolbarVisible)
    }

    // 4. Loop & Flow Control Group
    private data class LoopFlowGroup(
        val isLoop: Boolean,
        val ptA: Long?,
        val ptB: Long?,
        val targetSec: Int,
        val countInActive: Boolean
    )

    private val loopFlowGroup = combine(
        _isLooping,
        _loopPointA,
        _loopPointB,
        _targetDurationSeconds,
        _isCountInActive
    ) { isLoop, ptA, ptB, targetSec, countInActive ->
        LoopFlowGroup(isLoop, ptA, ptB, targetSec, countInActive)
    }

    // 5. Metronome & External Group
    private data class ExternalDataGroup(
        val currentBeat: Int,
        val isMetro: Boolean,
        val soundEnabled: Boolean,
        val countInBeats: Int,
        val gradualSpeedUp: Boolean
    )

    private val externalDataFlow = combine(
        metronomeController.currentBeat,
        metronomeController.isRunning,
        _isMetronomeSoundEnabled,
        _countInBeatsRemaining,
        _gradualSpeedUpEnabled
    ) { beat, isMetro, soundEnabled, countInBeats, gradualSpeedUp ->
        ExternalDataGroup(beat, isMetro, soundEnabled, countInBeats, gradualSpeedUp)
    }

    private data class ContextDataGroup(
        val settings: UserSettings,
        val loading: Boolean,
        val playbackData: SongPlaybackData?,
        val presets: List<SongPracticePreset>
    )

    private val contextDataFlow = combine(
        settingsRepository.userSettings,
        _isLoading,
        _songPlaybackData,
        _availablePresets
    ) { settings, loading, playbackData, presets ->
        ContextDataGroup(settings, loading, playbackData, presets)
    }

    val uiState: StateFlow<PracticePlayerUiState> = combine(
        engineNotesFlow,
        playbackConfigFlow,
        layoutConfigFlow,
        loopFlowGroup,
        combine(externalDataFlow, contextDataFlow) { ext, ctx -> Pair(ext, ctx) }
    ) { engineNotes, playConfig, layoutConfig, loopGroup, extraPair ->
        val ext = extraPair.first
        val ctx = extraPair.second

        PracticePlayerUiState(
            title = title,
            sourceType = sourceType,
            sourceId = sourceId,
            practiceMode = playConfig.mode,
            handMode = playConfig.hand,
            displayMode = playConfig.display,
            rangeMode = layoutConfig.range,
            noteDisplaySize = layoutConfig.noteSize,
            visualLookAhead = layoutConfig.lookAhead,
            isToolbarVisible = layoutConfig.toolbarVisible,
            bpm = playConfig.bpm,
            speedMultiplier = playConfig.speed,
            startOctave = layoutConfig.octave,
            exerciseNotes = engineNotes.notes,
            engineState = engineNotes.engineState,
            activePressedNotes = engineNotes.activeNotes,
            currentBeat = ext.currentBeat,
            isMetronomeRunning = ext.isMetro,
            isMetronomeSoundEnabled = ext.soundEnabled,
            userSettings = ctx.settings,
            isFinished = engineNotes.engineState.isFinished,
            isLooping = loopGroup.isLoop,
            loopPointA = loopGroup.ptA,
            loopPointB = loopGroup.ptB,
            targetDurationSeconds = loopGroup.targetSec,
            isLoadingNotes = ctx.loading,
            songPlaybackData = ctx.playbackData,
            isCountInActive = loopGroup.countInActive,
            countInBeatsRemaining = ext.countInBeats,
            gradualSpeedUpEnabled = ext.gradualSpeedUp,
            availablePresets = ctx.presets,
            isDemoMode = playConfig.isDemo,
            isAppSoundEnabled = playConfig.soundEnabled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PracticePlayerUiState(title = title, sourceType = sourceType, sourceId = sourceId)
    )

    init {
        viewModelScope.launch {
            pianoAudioEngine.prepare()
        }
        loadPresetsIfSong()
        loadNotesAndStart()
        observeMidiInput()
        observeEngineFinished()
        startTicker()
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                val state = practiceEngine.state.value
                if (!state.isPaused && !state.isFinished && !_isCountInActive.value) {
                    practiceEngine.tickTimer()
                    midiPlaybackScheduler.tick(state.currentPositionMs)
                }
                delay(16)
            }
        }
    }

    private fun loadPresetsIfSong() {
        if (sourceType == "SONG" && sourceId.isNotBlank()) {
            viewModelScope.launch {
                songRepository.getPracticePresets(sourceId).collect { presets ->
                    _availablePresets.value = presets
                }
            }
        }
    }

    private fun loadNotesAndStart() {
        viewModelScope.launch {
            _isLoading.value = true
            _activeNotes.value = emptySet()
            val playbackData = if (sourceType == "SONG" && sourceId.isNotBlank()) {
                val data = songRepository.getSongPlaybackData(sourceId)
                _songPlaybackData.value = data
                data
            } else null

            if (playbackData != null) {
                midiPlaybackScheduler.load(playbackData)
                updateSchedulerRoles()
            }

            val rawNotes = loadRawNotesForSource(sourceType, sourceId, _currentHandMode.value, playbackData)
            val baseBpm = playbackData?.song?.defaultBpm ?: _currentBpm.value
            _currentBpm.value = baseBpm

            _exerciseNotes.value = rawNotes
            _isLoading.value = false

            if (rawNotes.isNotEmpty()) {
                val targetDuration = ((rawNotes.maxOf { it.startMs + it.durationMs }) / 1000).toInt() + 1
                _targetDurationSeconds.value = targetDuration

                val config = PracticeConfiguration(
                    title = title,
                    sourceId = sourceId,
                    sourceType = sourceType,
                    practiceMode = _currentPracticeMode.value,
                    handMode = _currentHandMode.value,
                    displayMode = _currentDisplayMode.value,
                    bpm = _currentBpm.value,
                    notes = rawNotes
                )
                practiceEngine.startPractice(
                    configuration = config,
                    isLoopingEnabled = _isLooping.value,
                    targetDurationSeconds = _targetDurationSeconds.value
                )
                if (_isLooping.value && _loopPointA.value != null && _loopPointB.value != null) {
                    practiceEngine.setLoopRangeMs(_loopPointA.value!!, _loopPointB.value!!)
                }

                // Check count-in on start
                val countInOpt = settingsRepository.userSettings.stateIn(viewModelScope).value.countInOption
                if (_currentPracticeMode.value != PracticeMode.WAIT_FOR_NOTE && countInOpt != "OFF") {
                    startCountIn(countInOpt)
                } else {
                    startMetronomeIfNeeded()
                }
            }
        }
    }

    private fun startCountIn(countInOption: String) {
        val totalBeats = if (countInOption == "2_MEASURES") 8 else 4
        practiceEngine.pause()
        _isCountInActive.value = true
        _countInBeatsRemaining.value = totalBeats

        countInJob?.cancel()
        countInJob = viewModelScope.launch {
            val effectiveBpm = (_currentBpm.value * _speedMultiplier.value).toInt().coerceIn(30, 300)
            val beatIntervalMs = 60_000L / effectiveBpm
            metronomeController.start(effectiveBpm)

            for (beat in totalBeats downTo 1) {
                _countInBeatsRemaining.value = beat
                delay(beatIntervalMs)
            }

            _isCountInActive.value = false
            _countInBeatsRemaining.value = 0
            practiceEngine.resume()
        }
    }

    private fun startMetronomeIfNeeded() {
        if (_isMetronomeSoundEnabled.value && !practiceEngine.state.value.isPaused) {
            val effectiveBpm = (_currentBpm.value * _speedMultiplier.value).toInt().coerceIn(30, 300)
            lastActiveMetronomeBpm = effectiveBpm
            metronomeController.start(effectiveBpm)
        }
    }

    private fun observeEngineFinished() {
        viewModelScope.launch {
            practiceEngine.state.collect { state ->
                if (state.isFinished) {
                    metronomeController.stop()
                    lastActiveMetronomeBpm = -1
                    finishAndSaveSession()
                }
            }
        }
    }

    private suspend fun loadRawNotesForSource(
        sourceType: String,
        sourceId: String,
        filteredHand: HandMode,
        playbackData: SongPlaybackData?
    ): List<ExerciseNote> {
        val loadedNotes: List<ExerciseNote> = when {
            playbackData != null -> playbackData.notes
            sourceType == "LESSON" || sourceId.startsWith("lesson_") -> {
                val lesson = curriculumRepository.getLessonById(sourceId)
                lesson?.exercise?.notes ?: emptyList()
            }
            sourceType == "EXERCISE" || sourceType == "QUICK_DRILL" || sourceId.startsWith("ex_") -> {
                val exercise = exerciseRepository.getExerciseById(sourceId)
                exercise?.notes ?: emptyList()
            }
            sourceType == "SONG" || sourceId.startsWith("song_") || sourceId.startsWith("curriculum_") -> {
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

        return when (filteredHand) {
            HandMode.RIGHT -> loadedNotes.filter { it.hand == HandMode.RIGHT }
            HandMode.LEFT -> loadedNotes.filter { it.hand == HandMode.LEFT }
            HandMode.BOTH -> loadedNotes
        }
    }

    private fun updateSchedulerRoles() {
        if (_isDemoMode.value) {
            midiPlaybackScheduler.setDemoMode(true)
            return
        }
        midiPlaybackScheduler.setDemoMode(false)
        when (_currentHandMode.value) {
            HandMode.RIGHT -> {
                midiPlaybackScheduler.setHandRole(HandMode.RIGHT, com.ian.pianotrainer.core.audio.PlaybackRole.PRACTICE)
                midiPlaybackScheduler.setHandRole(HandMode.LEFT, com.ian.pianotrainer.core.audio.PlaybackRole.ACCOMPANIMENT)
                midiPlaybackScheduler.setHandRole(HandMode.BOTH, com.ian.pianotrainer.core.audio.PlaybackRole.PRACTICE)
            }
            HandMode.LEFT -> {
                midiPlaybackScheduler.setHandRole(HandMode.LEFT, com.ian.pianotrainer.core.audio.PlaybackRole.PRACTICE)
                midiPlaybackScheduler.setHandRole(HandMode.RIGHT, com.ian.pianotrainer.core.audio.PlaybackRole.ACCOMPANIMENT)
                midiPlaybackScheduler.setHandRole(HandMode.BOTH, com.ian.pianotrainer.core.audio.PlaybackRole.PRACTICE)
            }
            HandMode.BOTH -> {
                midiPlaybackScheduler.setHandRole(HandMode.RIGHT, com.ian.pianotrainer.core.audio.PlaybackRole.PRACTICE)
                midiPlaybackScheduler.setHandRole(HandMode.LEFT, com.ian.pianotrainer.core.audio.PlaybackRole.PRACTICE)
                midiPlaybackScheduler.setHandRole(HandMode.BOTH, com.ian.pianotrainer.core.audio.PlaybackRole.PRACTICE)
            }
        }
    }

    private fun observeMidiInput() {
        viewModelScope.launch {
            midiInput.noteEvents.collect { event ->
                if (event.isNoteOn && event.velocity > 0) {
                    _activeNotes.value = _activeNotes.value + event.note
                    if (_isAppSoundEnabled.value) {
                        pianoAudioEngine.noteOn(event.note, event.velocity)
                    }
                    practiceEngine.processPlayedNote(event.note, event.velocity)
                } else {
                    _activeNotes.value = _activeNotes.value - event.note
                    if (_isAppSoundEnabled.value) {
                        pianoAudioEngine.noteOff(event.note)
                    }
                }
            }
        }
    }

    fun onVirtualKeyPressed(midiNote: Int) {
        _activeNotes.value = _activeNotes.value + midiNote
        midiInput.onVirtualKeyPressed(midiNote, 80)
        if (_isAppSoundEnabled.value) {
            pianoAudioEngine.noteOn(midiNote, 80)
        }
        practiceEngine.processPlayedNote(midiNote, 80)
    }

    fun onVirtualKeyReleased(midiNote: Int) {
        _activeNotes.value = _activeNotes.value - midiNote
        midiInput.onVirtualKeyReleased(midiNote)
        if (_isAppSoundEnabled.value) {
            pianoAudioEngine.noteOff(midiNote)
        }
    }

    fun toggleAppSound() {
        val nextState = !_isAppSoundEnabled.value
        _isAppSoundEnabled.value = nextState
        if (!nextState) {
            pianoAudioEngine.allNotesOff()
        }
    }

    fun toggleDemoMode() {
        val nextDemo = !_isDemoMode.value
        _isDemoMode.value = nextDemo
        if (nextDemo) {
            practiceEngine.resume()
            midiPlaybackScheduler.setDemoMode(true)
            midiPlaybackScheduler.play(practiceEngine.state.value.currentPositionMs)
        } else {
            midiPlaybackScheduler.setDemoMode(false)
            pianoAudioEngine.allNotesOff()
            updateSchedulerRoles()
        }
    }

    fun setHandMode(hand: HandMode) {
        if (_currentHandMode.value != hand) {
            _currentHandMode.value = hand
            updateSchedulerRoles()
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

    fun setPlaybackSpeed(multiplier: Float) {
        val clamped = multiplier.coerceIn(0.25f, 2.0f)
        _speedMultiplier.value = clamped
        practiceEngine.setPlaybackSpeed(clamped)
        midiPlaybackScheduler.setSpeed(clamped)
        if (_isMetronomeSoundEnabled.value && !practiceEngine.state.value.isPaused) {
            val effectiveBpm = (_currentBpm.value * clamped).toInt().coerceIn(30, 300)
            lastActiveMetronomeBpm = effectiveBpm
            metronomeController.start(effectiveBpm)
        }
    }

    fun seekTo(targetMs: Long) {
        midiPlaybackScheduler.seekTo(targetMs)
        pianoAudioEngine.allNotesOff()
        practiceEngine.seekTo(targetMs)
    }

    fun setLoopPointA() {
        val currentPos = practiceEngine.state.value.currentPositionMs
        _loopPointA.value = currentPos
        val ptB = _loopPointB.value
        if (ptB != null && ptB > currentPos) {
            _isLooping.value = true
            practiceEngine.setLoopRangeMs(currentPos, ptB)
        }
    }

    fun setLoopPointB() {
        val currentPos = practiceEngine.state.value.currentPositionMs
        val ptA = _loopPointA.value ?: 0L
        if (currentPos > ptA) {
            _loopPointB.value = currentPos
            _isLooping.value = true
            practiceEngine.setLoopRangeMs(ptA, currentPos)
        }
    }

    fun clearLoop() {
        _isLooping.value = false
        _loopPointA.value = null
        _loopPointB.value = null
        practiceEngine.clearLoop()
    }

    fun setBpm(newBpm: Int) {
        val clamped = newBpm.coerceIn(30, 240)
        _currentBpm.value = clamped
        if (_isMetronomeSoundEnabled.value && !practiceEngine.state.value.isPaused) {
            val effectiveBpm = (clamped * _speedMultiplier.value).toInt().coerceIn(30, 300)
            lastActiveMetronomeBpm = effectiveBpm
            metronomeController.start(effectiveBpm)
        }
    }

    fun toggleMetronome() {
        val current = _isMetronomeSoundEnabled.value
        val next = !current
        _isMetronomeSoundEnabled.value = next
        if (next && !practiceEngine.state.value.isPaused) {
            val effectiveBpm = (_currentBpm.value * _speedMultiplier.value).toInt().coerceIn(30, 300)
            lastActiveMetronomeBpm = effectiveBpm
            metronomeController.start(effectiveBpm)
        } else {
            lastActiveMetronomeBpm = -1
            metronomeController.stop()
        }
    }

    fun toggleLooping() {
        val nextState = !_isLooping.value
        _isLooping.value = nextState
        practiceEngine.setLooping(nextState)
    }

    fun toggleGradualSpeedUp() {
        _gradualSpeedUpEnabled.value = !_gradualSpeedUpEnabled.value
    }

    fun togglePause() {
        val currentEngine = practiceEngine.state.value
        if (currentEngine.isPaused) {
            practiceEngine.resume()
            if (_isDemoMode.value) {
                midiPlaybackScheduler.play(currentEngine.currentPositionMs)
            }
            if (_isMetronomeSoundEnabled.value) {
                val effectiveBpm = (_currentBpm.value * _speedMultiplier.value).toInt().coerceIn(30, 300)
                lastActiveMetronomeBpm = effectiveBpm
                metronomeController.start(effectiveBpm)
            }
        } else {
            practiceEngine.pause()
            midiPlaybackScheduler.pause()
            pianoAudioEngine.allNotesOff()
            lastActiveMetronomeBpm = -1
            metronomeController.stop()
        }
    }

    fun restart() {
        midiPlaybackScheduler.stop()
        pianoAudioEngine.allNotesOff()
        loadNotesAndStart()
    }

    fun onBackgroundPause() {
        if (!practiceEngine.state.value.isPaused) {
            practiceEngine.pause()
            midiPlaybackScheduler.pause()
            pianoAudioEngine.allNotesOff()
            lastActiveMetronomeBpm = -1
            metronomeController.stop()
        }
    }

    fun saveCurrentAsPreset(presetName: String) {
        if (sourceType != "SONG" || sourceId.isBlank()) return
        val name = presetName.trim().ifBlank { "Preset ${_availablePresets.value.size + 1}" }
        val preset = SongPracticePreset(
            id = UUID.randomUUID().toString(),
            songId = sourceId,
            name = name,
            loopStartMs = _loopPointA.value,
            loopEndMs = _loopPointB.value,
            handMode = _currentHandMode.value,
            practiceMode = _currentPracticeMode.value,
            targetBpm = _currentBpm.value,
            speedMultiplier = _speedMultiplier.value,
            lookAhead = _visualLookAhead.value,
            noteDisplaySize = _noteDisplaySize.value
        )
        viewModelScope.launch {
            songRepository.savePracticePreset(preset)
        }
    }

    fun loadPreset(preset: SongPracticePreset) {
        _currentHandMode.value = preset.handMode
        _currentPracticeMode.value = preset.practiceMode
        _currentBpm.value = preset.targetBpm
        _speedMultiplier.value = preset.speedMultiplier
        _visualLookAhead.value = preset.lookAhead
        _noteDisplaySize.value = preset.noteDisplaySize
        if (preset.loopStartMs != null && preset.loopEndMs != null && preset.loopEndMs > preset.loopStartMs) {
            _loopPointA.value = preset.loopStartMs
            _loopPointB.value = preset.loopEndMs
            _isLooping.value = true
            practiceEngine.setLoopRangeMs(preset.loopStartMs, preset.loopEndMs)
        } else {
            clearLoop()
        }
        loadNotesAndStart()
    }

    fun deletePreset(presetId: String) {
        viewModelScope.launch {
            songRepository.deletePracticePreset(presetId)
        }
    }

    fun finishAndSaveSession() {
        viewModelScope.launch {
            metronomeController.stop()
            lastActiveMetronomeBpm = -1
            tickJob?.cancel()
            countInJob?.cancel()
            midiPlaybackScheduler.stop()
            pianoAudioEngine.allNotesOff()
            val result = practiceEngine.stop()
            val session = result.session
            if (session != null && (session.durationMs >= 2000L || session.correctNotes > 0 || session.wrongNotes > 0)) {
                val enrichedSession = session.copy(
                    sourceTitleSnapshot = title,
                    score = session.correctNotes * 10,
                    maxStreak = session.correctNotes,
                    inputSource = "VIRTUAL_KEYBOARD",
                    effectiveSpeed = _speedMultiplier.value,
                    loopStartMs = _loopPointA.value,
                    loopEndMs = _loopPointB.value
                )
                progressRepository.savePracticeSession(enrichedSession, session.noteResults)
                if (sourceType == "SONG" && sourceId.isNotBlank()) {
                    songRepository.updateLastPracticed(sourceId)
                }
                _navigateToResult.emit(enrichedSession.id)
            } else {
                _navigateToResult.emit("")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        metronomeController.stop()
        lastActiveMetronomeBpm = -1
        tickJob?.cancel()
        countInJob?.cancel()
        midiPlaybackScheduler.stop()
        pianoAudioEngine.allNotesOff()
    }

    class Factory(
        private val title: String,
        private val sourceType: String,
        private val sourceId: String,
        private val initialHand: HandMode,
        private val initialBpm: Int,
        private val initialPracticeMode: PracticeMode = PracticeMode.WAIT_FOR_NOTE,
        private val practiceEngine: PracticeEngine,
        private val midiInput: MidiInput,
        private val metronomeController: MetronomeController,
        private val curriculumRepository: CurriculumRepository,
        private val exerciseRepository: ExerciseRepository,
        private val songRepository: SongRepository,
        private val progressRepository: ProgressRepository,
        private val settingsRepository: SettingsRepository,
        private val pianoAudioEngine: com.ian.pianotrainer.core.audio.PianoAudioEngine,
        private val midiPlaybackScheduler: com.ian.pianotrainer.core.audio.MidiPlaybackScheduler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PracticePlayerViewModel(
                title = title,
                sourceType = sourceType,
                sourceId = sourceId,
                initialHand = initialHand,
                initialBpm = initialBpm,
                initialPracticeMode = initialPracticeMode,
                practiceEngine = practiceEngine,
                midiInput = midiInput,
                metronomeController = metronomeController,
                curriculumRepository = curriculumRepository,
                exerciseRepository = exerciseRepository,
                songRepository = songRepository,
                progressRepository = progressRepository,
                settingsRepository = settingsRepository,
                pianoAudioEngine = pianoAudioEngine,
                midiPlaybackScheduler = midiPlaybackScheduler
            ) as T
        }
    }
}
