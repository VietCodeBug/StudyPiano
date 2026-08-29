package com.ian.pianotrainer.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.core.music.SectionSlicer
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.KeyboardRangeMode
import com.ian.pianotrainer.domain.model.LearningSection
import com.ian.pianotrainer.domain.model.NoteDisplaySize
import com.ian.pianotrainer.domain.model.PlayerTransportMode
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
    val transportMode: PlayerTransportMode = PlayerTransportMode.PRACTICE_RHYTHM,
    val practiceMode: PracticeMode = PracticeMode.RHYTHM,
    val handMode: HandMode = HandMode.RIGHT,
    val displayMode: DisplayMode = DisplayMode.FALLING_NOTES,
    val rangeMode: KeyboardRangeMode = KeyboardRangeMode.FULL_88_KEYS,
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
    val isAppSoundEnabled: Boolean = false,
    val sections: List<LearningSection> = emptyList(),
    val selectedSection: LearningSection? = null,
    val showNoteNames: Boolean = false,
    val enableVirtualKeyInteraction: Boolean = false,
    val demoPositionMs: Long = 0L
)

class PracticePlayerViewModel(
    private val title: String,
    private val sourceType: String,
    private val sourceId: String,
    private val initialHand: HandMode,
    private val initialBpm: Int,
    private val initialPracticeMode: PracticeMode = PracticeMode.RHYTHM,
    private val initialSectionId: String? = null,
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
    private val _allSourceNotes = MutableStateFlow<List<ExerciseNote>>(emptyList())
    private val _exerciseNotes = MutableStateFlow<List<ExerciseNote>>(emptyList())
    private val _currentHandMode = MutableStateFlow(initialHand)
    private val _currentDisplayMode = MutableStateFlow(DisplayMode.FALLING_NOTES)
    private val _currentPracticeMode = MutableStateFlow(initialPracticeMode)
    private val _transportMode = MutableStateFlow(
        if (initialPracticeMode == PracticeMode.WAIT_FOR_NOTE) PlayerTransportMode.PRACTICE_WAIT else PlayerTransportMode.PRACTICE_RHYTHM
    )
    private val _rangeMode = MutableStateFlow(KeyboardRangeMode.FULL_88_KEYS)
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
    private val _isAppSoundEnabled = MutableStateFlow(false)
    private val _sections = MutableStateFlow<List<LearningSection>>(emptyList())
    private val _selectedSection = MutableStateFlow<LearningSection?>(null)
    private val _showNoteNames = MutableStateFlow(false)
    private val _enableVirtualKeyInteraction = MutableStateFlow(false)
    private val _demoPositionMs = MutableStateFlow(0L)

    private var tickJob: Job? = null
    private var countInJob: Job? = null
    private var demoJob: Job? = null
    private var toolbarAutoHideJob: Job? = null
    private var lastActiveMetronomeBpm: Int = -1

    private val _navigateToResult = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToResult: SharedFlow<String> = _navigateToResult.asSharedFlow()

    // Strongly Typed Flow Groups without casting
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

    private data class PlaybackAudioParams(
        val bpm: Int,
        val speed: Float,
        val soundEnabled: Boolean,
        val transportMode: PlayerTransportMode,
        val demoPos: Long
    )

    private val playbackAudioFlow = combine(
        _currentBpm,
        _speedMultiplier,
        _isAppSoundEnabled,
        _transportMode,
        _demoPositionMs
    ) { bpm, speed, sound, transport, demoPos ->
        PlaybackAudioParams(bpm, speed, sound, transport, demoPos)
    }

    private data class ModeSettingsGroup(
        val hand: HandMode,
        val display: DisplayMode,
        val practiceMode: PracticeMode,
        val showNoteNames: Boolean,
        val enableInteraction: Boolean
    )

    private val modeSettingsFlow = combine(
        _currentHandMode,
        _currentDisplayMode,
        _currentPracticeMode,
        _showNoteNames,
        _enableVirtualKeyInteraction
    ) { hand, display, practiceMode, showNames, enableInteraction ->
        ModeSettingsGroup(hand, display, practiceMode, showNames, enableInteraction)
    }

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

    private data class SectionLoopGroup(
        val sections: List<LearningSection>,
        val selectedSection: LearningSection?,
        val isLoop: Boolean,
        val ptA: Long?,
        val ptB: Long?,
        val targetSec: Int,
        val countInActive: Boolean
    )

    private val sectionLoopFlow = combine(
        combine(_sections, _selectedSection) { secs, sel -> Pair(secs, sel) },
        combine(_isLooping, _loopPointA, _loopPointB) { isLoop, ptA, ptB -> Triple(isLoop, ptA, ptB) },
        combine(_targetDurationSeconds, _isCountInActive) { targetSec, countInActive -> Pair(targetSec, countInActive) }
    ) { p1, p2, p3 ->
        SectionLoopGroup(
            sections = p1.first,
            selectedSection = p1.second,
            isLoop = p2.first,
            ptA = p2.second,
            ptB = p2.third,
            targetSec = p3.first,
            countInActive = p3.second
        )
    }

    private data class MetronomeContextGroup(
        val currentBeat: Int,
        val isMetro: Boolean,
        val metroSound: Boolean,
        val countInBeats: Int,
        val gradualSpeedUp: Boolean,
        val settings: UserSettings,
        val loading: Boolean,
        val playbackData: SongPlaybackData?,
        val presets: List<SongPracticePreset>
    )

    private val metronomeContextFlow = combine(
        combine(metronomeController.currentBeat, metronomeController.isRunning, _isMetronomeSoundEnabled) { beat, isMetro, metroSound ->
            Triple(beat, isMetro, metroSound)
        },
        combine(_countInBeatsRemaining, _gradualSpeedUpEnabled) { countIn, gradual ->
            Pair(countIn, gradual)
        },
        combine(settingsRepository.userSettings, _isLoading, _songPlaybackData, _availablePresets) { settings, loading, playbackData, presets ->
            listOf(settings, loading, playbackData, presets)
        }
    ) { p1, p2, p3 ->
        MetronomeContextGroup(
            currentBeat = p1.first,
            isMetro = p1.second,
            metroSound = p1.third,
            countInBeats = p2.first,
            gradualSpeedUp = p2.second,
            settings = p3[0] as UserSettings,
            loading = p3[1] as Boolean,
            playbackData = p3[2] as? SongPlaybackData,
            presets = (p3[3] as? List<*>)?.filterIsInstance<SongPracticePreset>() ?: emptyList()
        )
    }

    val uiState: StateFlow<PracticePlayerUiState> = combine(
        engineNotesFlow,
        playbackAudioFlow,
        modeSettingsFlow,
        layoutConfigFlow,
        combine(sectionLoopFlow, metronomeContextFlow) { sl, mc -> Pair(sl, mc) }
    ) { engineNotes, audio, modes, layout, extraPair ->
        val sl = extraPair.first
        val mc = extraPair.second

        PracticePlayerUiState(
            title = title,
            sourceType = sourceType,
            sourceId = sourceId,
            transportMode = audio.transportMode,
            practiceMode = modes.practiceMode,
            handMode = modes.hand,
            displayMode = modes.display,
            rangeMode = layout.range,
            noteDisplaySize = layout.noteSize,
            visualLookAhead = layout.lookAhead,
            isToolbarVisible = layout.toolbarVisible,
            bpm = audio.bpm,
            speedMultiplier = audio.speed,
            startOctave = layout.octave,
            exerciseNotes = engineNotes.notes,
            engineState = engineNotes.engineState,
            activePressedNotes = engineNotes.activeNotes,
            currentBeat = mc.currentBeat,
            isMetronomeRunning = mc.isMetro,
            isMetronomeSoundEnabled = mc.metroSound,
            userSettings = mc.settings,
            isFinished = engineNotes.engineState.isFinished,
            isLooping = sl.isLoop,
            loopPointA = sl.ptA,
            loopPointB = sl.ptB,
            targetDurationSeconds = sl.targetSec,
            isLoadingNotes = mc.loading,
            songPlaybackData = mc.playbackData,
            isCountInActive = sl.countInActive,
            countInBeatsRemaining = mc.countInBeats,
            availablePresets = mc.presets,
            gradualSpeedUpEnabled = mc.gradualSpeedUp,
            isAppSoundEnabled = audio.soundEnabled,
            sections = sl.sections,
            selectedSection = sl.selectedSection,
            showNoteNames = modes.showNoteNames,
            enableVirtualKeyInteraction = modes.enableInteraction,
            demoPositionMs = audio.demoPos
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
                if (_transportMode.value != PlayerTransportMode.DEMO) {
                    val state = practiceEngine.state.value
                    if (!state.isPaused && !state.isFinished && !_isCountInActive.value) {
                        practiceEngine.tickTimer()
                        if (_currentPracticeMode.value == PracticeMode.RHYTHM) {
                            midiPlaybackScheduler.tick(state.currentPositionMs)
                        }
                    }
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
            _allSourceNotes.value = rawNotes
            val baseBpm = playbackData?.song?.defaultBpm ?: _currentBpm.value
            _currentBpm.value = baseBpm

            // Compute Sections with SectionSlicer
            val slicedSections = SectionSlicer.sliceSong(
                songId = sourceId,
                notes = rawNotes,
                tempos = playbackData?.tempos ?: emptyList(),
                timeSignatures = playbackData?.timeSignatures ?: emptyList(),
                defaultBpm = baseBpm
            )
            _sections.value = slicedSections

            val initialSec = if (initialSectionId != null) {
                slicedSections.firstOrNull { it.id == initialSectionId }
            } else null
            _selectedSection.value = initialSec

            val effectiveNotes = if (initialSec != null) {
                rawNotes.filter { it.startMs in initialSec.startMs..initialSec.endMs }
            } else {
                rawNotes
            }

            _exerciseNotes.value = effectiveNotes
            _isLoading.value = false

            if (effectiveNotes.isNotEmpty()) {
                val targetDuration = ((effectiveNotes.maxOf { it.startMs + it.durationMs }) / 1000).toInt() + 1
                _targetDurationSeconds.value = targetDuration

                val config = PracticeConfiguration(
                    title = title,
                    sourceId = sourceId,
                    sourceType = sourceType,
                    practiceMode = _currentPracticeMode.value,
                    handMode = _currentHandMode.value,
                    displayMode = _currentDisplayMode.value,
                    bpm = _currentBpm.value,
                    notes = effectiveNotes
                )
                practiceEngine.startPractice(
                    configuration = config,
                    isLoopingEnabled = _isLooping.value,
                    targetDurationSeconds = _targetDurationSeconds.value
                )

                if (initialSec != null) {
                    _loopPointA.value = initialSec.startMs
                    _loopPointB.value = initialSec.endMs
                    practiceEngine.setLoopRangeMs(initialSec.startMs, initialSec.endMs)
                    practiceEngine.seekTo(initialSec.startMs)
                }

                // If in rhythm mode, prepare accompaniment scheduler
                if (_currentPracticeMode.value == PracticeMode.RHYTHM) {
                    val startPos = initialSec?.startMs ?: 0L
                    midiPlaybackScheduler.play(startPos)
                }

                // Check count-in
                val countInOpt = settingsRepository.userSettings.stateIn(viewModelScope).value.countInOption
                if (_currentPracticeMode.value != PracticeMode.WAIT_FOR_NOTE && countInOpt != "OFF") {
                    startCountIn(countInOpt)
                } else {
                    startMetronomeIfNeeded()
                }
            }
        }
    }

    fun selectSection(section: LearningSection?) {
        _selectedSection.value = section
        val raw = _allSourceNotes.value
        val filtered = if (section != null) {
            raw.filter { it.startMs in section.startMs..section.endMs }
        } else {
            raw
        }
        _exerciseNotes.value = filtered
        if (filtered.isNotEmpty()) {
            val startMs = section?.startMs ?: 0L
            val endMs = section?.endMs ?: filtered.maxOf { it.startMs + it.durationMs }
            _loopPointA.value = startMs
            _loopPointB.value = endMs
            practiceEngine.setLoopRangeMs(startMs, endMs)
            seekTo(startMs)
        }
    }

    private fun updateSchedulerRoles() {
        if (_transportMode.value == PlayerTransportMode.DEMO) {
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

    fun toggleDemoMode() {
        if (_transportMode.value == PlayerTransportMode.DEMO) {
            stopDemo()
        } else {
            startDemo()
        }
    }

    fun startDemo() {
        practiceEngine.pause()
        metronomeController.stop()
        _transportMode.value = PlayerTransportMode.DEMO
        midiPlaybackScheduler.setDemoMode(true)

        val startPos = _selectedSection.value?.startMs ?: practiceEngine.state.value.currentPositionMs
        val endPos = _selectedSection.value?.endMs ?: (_exerciseNotes.value.maxOfOrNull { it.startMs + it.durationMs } ?: 60000L)
        _demoPositionMs.value = startPos
        midiPlaybackScheduler.play(startPos)

        demoJob?.cancel()
        demoJob = viewModelScope.launch {
            var currentPos = startPos
            val startTimeMonotonic = System.currentTimeMillis()

            while (_transportMode.value == PlayerTransportMode.DEMO) {
                val elapsedReal = System.currentTimeMillis() - startTimeMonotonic
                val elapsedScaled = (elapsedReal * _speedMultiplier.value).toLong()
                currentPos = startPos + elapsedScaled
                _demoPositionMs.value = currentPos
                midiPlaybackScheduler.tick(currentPos)

                if (currentPos >= endPos) {
                    if (_isLooping.value) {
                        currentPos = startPos
                        midiPlaybackScheduler.seekTo(startPos)
                    } else {
                        break
                    }
                }
                delay(16)
            }
            if (_transportMode.value == PlayerTransportMode.DEMO) {
                stopDemo()
            }
        }
    }

    fun stopDemo() {
        demoJob?.cancel()
        midiPlaybackScheduler.stop()
        pianoAudioEngine.allNotesOff()
        _transportMode.value = if (_currentPracticeMode.value == PracticeMode.WAIT_FOR_NOTE) PlayerTransportMode.PRACTICE_WAIT else PlayerTransportMode.PRACTICE_RHYTHM
        updateSchedulerRoles()

        val restorePos = _selectedSection.value?.startMs ?: 0L
        practiceEngine.seekTo(restorePos)
        if (_currentPracticeMode.value == PracticeMode.RHYTHM) {
            midiPlaybackScheduler.play(restorePos)
        }
        practiceEngine.resume()
    }

    private fun observeMidiInput() {
        viewModelScope.launch {
            midiInput.noteEvents.collect { event ->
                if (event.isNoteOn && event.velocity > 0) {
                    _activeNotes.value = _activeNotes.value + event.note
                    if (_isAppSoundEnabled.value) {
                        pianoAudioEngine.noteOn(event.note, event.velocity)
                    }
                    if (_transportMode.value != PlayerTransportMode.DEMO) {
                        practiceEngine.processPlayedNote(event.note, event.velocity)
                    }
                } else {
                    _activeNotes.value = _activeNotes.value - event.note
                    if (_isAppSoundEnabled.value) {
                        pianoAudioEngine.noteOff(event.note)
                    }
                }
            }
        }
    }

    fun onVirtualKeyPressed(midiNote: Int, velocity: Int = 80) {
        _activeNotes.value = _activeNotes.value + midiNote
        midiInput.onVirtualKeyPressed(midiNote, velocity)
        if (_isAppSoundEnabled.value) {
            pianoAudioEngine.noteOn(midiNote, velocity)
        }
        if (_transportMode.value != PlayerTransportMode.DEMO) {
            practiceEngine.processPlayedNote(midiNote, velocity)
        }
    }

    fun onVirtualKeyReleased(midiNote: Int) {
        _activeNotes.value = _activeNotes.value - midiNote
        midiInput.onVirtualKeyReleased(midiNote)
        if (_isAppSoundEnabled.value) {
            pianoAudioEngine.noteOff(midiNote)
        }
    }

    fun toggleAppSound() {
        val next = !_isAppSoundEnabled.value
        _isAppSoundEnabled.value = next
        if (!next) {
            pianoAudioEngine.allNotesOff()
        }
    }

    fun setHandMode(hand: HandMode) {
        _currentHandMode.value = hand
        updateSchedulerRoles()
        applyPracticeSettings()
    }

    fun setPracticeMode(mode: PracticeMode) {
        _currentPracticeMode.value = mode
        _transportMode.value = if (mode == PracticeMode.WAIT_FOR_NOTE) PlayerTransportMode.PRACTICE_WAIT else PlayerTransportMode.PRACTICE_RHYTHM
        applyPracticeSettings()
    }

    /** Reconfigures playback without reloading the song or discarding the active section. */
    private fun applyPracticeSettings() {
        val notes = _exerciseNotes.value
        if (notes.isEmpty()) return
        pianoAudioEngine.allNotesOff()
        midiPlaybackScheduler.stop()
        val section = _selectedSection.value
        val startMs = section?.startMs ?: 0L
        val endMs = section?.endMs ?: notes.maxOf { it.startMs + it.durationMs }
        practiceEngine.startPractice(
            PracticeConfiguration(
                title = title, sourceId = sourceId, sourceType = sourceType,
                practiceMode = _currentPracticeMode.value, handMode = _currentHandMode.value,
                displayMode = _currentDisplayMode.value, bpm = _currentBpm.value, notes = notes
            ),
            _isLooping.value,
            ((endMs - startMs).coerceAtLeast(0L) / 1000L).toInt()
        )
        if (section != null) practiceEngine.setLoopRangeMs(startMs, endMs)
        practiceEngine.seekTo(startMs)
        if (_currentPracticeMode.value == PracticeMode.RHYTHM) midiPlaybackScheduler.play(startMs)
        scheduleToolbarAutoHide()
    }
    fun setDisplayMode(mode: DisplayMode) {
        _currentDisplayMode.value = mode
    }

    fun setRangeMode(range: KeyboardRangeMode) {
        _rangeMode.value = range
    }

    fun setNoteDisplaySize(size: NoteDisplaySize) {
        _noteDisplaySize.value = size
    }

    fun setVisualLookAhead(lookAhead: VisualLookAhead) {
        _visualLookAhead.value = lookAhead
    }

    fun setShowNoteNames(show: Boolean) {
        _showNoteNames.value = show
    }

    fun setEnableVirtualKeyInteraction(enable: Boolean) {
        _enableVirtualKeyInteraction.value = enable
    }

    fun toggleToolbar() {
        _isToolbarVisible.value = !_isToolbarVisible.value
        if (_isToolbarVisible.value) {
            scheduleToolbarAutoHide()
        }
    }

    fun showToolbarTemporarily() {
        _isToolbarVisible.value = true
        scheduleToolbarAutoHide()
    }

    private fun scheduleToolbarAutoHide() {
        toolbarAutoHideJob?.cancel()
        if (!practiceEngine.state.value.isPaused && !_isCountInActive.value) {
            toolbarAutoHideJob = viewModelScope.launch {
                delay(3000L)
                _isToolbarVisible.value = false
            }
        }
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
        } else if (!next) {
            metronomeController.stop()
            lastActiveMetronomeBpm = -1
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
            if (_currentPracticeMode.value == PracticeMode.RHYTHM) {
                midiPlaybackScheduler.play(currentEngine.currentPositionMs)
            }
            if (_isMetronomeSoundEnabled.value) {
                val effectiveBpm = (_currentBpm.value * _speedMultiplier.value).toInt().coerceIn(30, 300)
                lastActiveMetronomeBpm = effectiveBpm
                metronomeController.start(effectiveBpm)
            }
            scheduleToolbarAutoHide()
        } else {
            toolbarAutoHideJob?.cancel()
            _isToolbarVisible.value = true
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
        val startPos = _selectedSection.value?.startMs ?: 0L
        seekTo(startPos)
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
            demoJob?.cancel()
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
                if (state.isFinished && _transportMode.value != PlayerTransportMode.DEMO) {
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
            else -> emptyList()
        }

        return when (filteredHand) {
            HandMode.RIGHT -> loadedNotes.filter { it.hand == HandMode.RIGHT || it.hand == HandMode.BOTH }
            HandMode.LEFT -> loadedNotes.filter { it.hand == HandMode.LEFT || it.hand == HandMode.BOTH }
            HandMode.BOTH -> loadedNotes
        }
    }

    override fun onCleared() {
        super.onCleared()
        metronomeController.stop()
        lastActiveMetronomeBpm = -1
        tickJob?.cancel()
        countInJob?.cancel()
        demoJob?.cancel()
        toolbarAutoHideJob?.cancel()
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
        private val initialSectionId: String? = null,
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
                initialSectionId = initialSectionId,
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
