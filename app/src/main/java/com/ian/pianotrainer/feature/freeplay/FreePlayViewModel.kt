package com.ian.pianotrainer.feature.freeplay

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.core.midi.AudioInputCoordinator
import com.ian.pianotrainer.core.midi.AudioRecordOwner
import com.ian.pianotrainer.core.music.PracticeClock
import com.ian.pianotrainer.core.music.SystemPracticeClock
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.FreePlayRecording
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.KeyboardRangeMode
import com.ian.pianotrainer.domain.model.PracticeMode
import com.ian.pianotrainer.domain.model.PracticeSession
import com.ian.pianotrainer.domain.model.RecordedMidiEvent
import com.ian.pianotrainer.domain.model.UserSettings
import com.ian.pianotrainer.domain.repository.FreePlayRepository
import com.ian.pianotrainer.domain.repository.ProgressRepository
import com.ian.pianotrainer.domain.repository.SettingsRepository
import com.ian.pianotrainer.domain.service.MetronomeController
import com.ian.pianotrainer.domain.service.MidiInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class FreePlayUiState(
    val activePressedNotes: Set<Int> = emptySet(),
    val lastPressedNote: Int? = null,
    val trails: List<RisingTrail> = emptyList(),
    val currentClockMs: Long = 0L,
    val startOctave: Int = 3,
    val rangeMode: KeyboardRangeMode = KeyboardRangeMode.TWO_OCTAVES,
    val isMetronomeRunning: Boolean = false,
    val bpm: Int = 80,
    val currentBeat: Int = 1,
    val isRecording: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val recordedEventCount: Int = 0,
    val isAudioRecordingEnabled: Boolean = false,
    val savedRecordings: List<FreePlayRecording> = emptyList(),
    val playingRecordingId: String? = null,
    val isPlayingRecording: Boolean = false,
    val playbackProgressFraction: Float = 0f,
    val playbackCurrentMs: Long = 0L,
    val playbackDurationMs: Long = 0L,
    val showSaveDialog: Boolean = false,
    val audioPermissionNeeded: Boolean = false,
    val audioOccupiedWarning: Boolean = false,
    val userSettings: UserSettings = UserSettings()
)

class FreePlayViewModel(
    private val context: Context,
    private val midiInput: MidiInput,
    private val metronomeController: MetronomeController,
    private val settingsRepository: SettingsRepository,
    private val freePlayRepository: FreePlayRepository,
    private val progressRepository: ProgressRepository? = null,
    private val clock: PracticeClock = SystemPracticeClock()
) : ViewModel() {

    private val _activeNotes = MutableStateFlow<Set<Int>>(emptySet())
    private val _lastNote = MutableStateFlow<Int?>(null)
    private val _trails = MutableStateFlow<List<RisingTrail>>(emptyList())
    private val _currentClockMs = MutableStateFlow(0L)
    private val _startOctave = MutableStateFlow(3)
    private val _rangeMode = MutableStateFlow(KeyboardRangeMode.TWO_OCTAVES)
    private val _bpm = MutableStateFlow(80)

    // Recording State
    private val _isRecording = MutableStateFlow(false)
    private val _recordingDurationMs = MutableStateFlow(0L)
    private val _recordedEvents = mutableListOf<RecordedMidiEvent>()
    private val _recordedEventCount = MutableStateFlow(0)
    private val _isAudioRecordingEnabled = MutableStateFlow(false)
    private val _showSaveDialog = MutableStateFlow(false)
    private val _audioPermissionNeeded = MutableStateFlow(false)
    private val _audioOccupiedWarning = MutableStateFlow(false)
    private var currentRecordingId: String = ""
    private var recordingStartTimeMs: Long = 0L
    private var currentAudioFilePath: String? = null
    private var mediaRecorder: MediaRecorder? = null

    // Playback State
    private val _playingRecordingId = MutableStateFlow<String?>(null)
    private val _isPlayingRecording = MutableStateFlow(false)
    private val _playbackProgressFraction = MutableStateFlow(0f)
    private val _playbackCurrentMs = MutableStateFlow(0L)
    private val _playbackDurationMs = MutableStateFlow(0L)
    private var playbackJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    // Active Practice Session Tracking (Gate E1)
    private var activePracticeAccumulatedMs: Long = 0L
    private var lastNoteOnMonotonicMs: Long = 0L
    private var sessionStartTimeMs: Long = 0L
    private var totalNotesPlayed: Int = 0
    private val IDLE_TIMEOUT_MS = 120_000L // 2 minutes idle threshold

    private var animationClockJob: Job? = null
    private var nextTrailId = 1L
    private val activeTrailsMap = mutableMapOf<Int, Long>()

    val savedRecordings: StateFlow<List<FreePlayRecording>> = freePlayRepository.getAllRecordings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Group 1: Visual and Keyboard
    private data class VisualStateGroup(
        val activeNotes: Set<Int>,
        val lastNote: Int?,
        val trails: List<RisingTrail>,
        val clockMs: Long,
        val octave: Int,
        val range: KeyboardRangeMode
    )

    // Group 2: Metronome and Recording
    private data class RecordingStateGroup(
        val isMetronome: Boolean,
        val bpm: Int,
        val beat: Int,
        val isRecording: Boolean,
        val durationMs: Long,
        val eventCount: Int,
        val audioEnabled: Boolean,
        val saveDialog: Boolean,
        val permissionNeeded: Boolean,
        val audioOccupied: Boolean
    )

    // Group 3: Playback
    private data class PlaybackStateGroup(
        val playingId: String?,
        val isPlaying: Boolean,
        val progress: Float,
        val currentMs: Long,
        val durationMs: Long
    )

    private data class NotesStateGroup(
        val activeNotes: Set<Int>,
        val lastNote: Int?,
        val trails: List<RisingTrail>,
        val clockMs: Long
    )
    private data class KeyboardStateGroup(
        val octave: Int,
        val range: KeyboardRangeMode
    )
    private val visualGroupFlow = combine(
        combine(_activeNotes, _lastNote, _trails, _currentClockMs) { active, last, trails, clock ->
            NotesStateGroup(active, last, trails, clock)
        },
        combine(_startOctave, _rangeMode) { octave, range ->
            KeyboardStateGroup(octave, range)
        }
    ) { notes, kb ->
        VisualStateGroup(notes.activeNotes, notes.lastNote, notes.trails, notes.clockMs, kb.octave, kb.range)
    }

    private data class MetroStateGroup(
        val isMetronome: Boolean,
        val bpm: Int,
        val beat: Int
    )
    private data class RecStateGroup(
        val isRecording: Boolean,
        val durationMs: Long,
        val eventCount: Int,
        val audioEnabled: Boolean
    )
    private data class DialogStateGroup(
        val saveDialog: Boolean,
        val permissionNeeded: Boolean,
        val audioOccupied: Boolean
    )

    private val recordingGroupFlow = combine(
        combine(metronomeController.isRunning, _bpm, metronomeController.currentBeat) { isMetro, bpm, beat ->
            MetroStateGroup(isMetro, bpm, beat)
        },
        combine(_isRecording, _recordingDurationMs, _recordedEventCount, _isAudioRecordingEnabled) { isRec, dur, cnt, audio ->
            RecStateGroup(isRec, dur, cnt, audio)
        },
        combine(_showSaveDialog, _audioPermissionNeeded, _audioOccupiedWarning) { saveDlg, perm, occ ->
            DialogStateGroup(saveDlg, perm, occ)
        }
    ) { metro, rec, dlg ->
        RecordingStateGroup(
            isMetronome = metro.isMetronome,
            bpm = metro.bpm,
            beat = metro.beat,
            isRecording = rec.isRecording,
            durationMs = rec.durationMs,
            eventCount = rec.eventCount,
            audioEnabled = rec.audioEnabled,
            saveDialog = dlg.saveDialog,
            permissionNeeded = dlg.permissionNeeded,
            audioOccupied = dlg.audioOccupied
        )
    }

    private val playbackGroupFlow = combine(
        _playingRecordingId,
        _isPlayingRecording,
        _playbackProgressFraction,
        _playbackCurrentMs,
        _playbackDurationMs
    ) { playingId, isPlaying, progress, curMs, durMs ->
        PlaybackStateGroup(playingId, isPlaying, progress, curMs, durMs)
    }

    val uiState: StateFlow<FreePlayUiState> = combine(
        visualGroupFlow,
        recordingGroupFlow,
        playbackGroupFlow,
        settingsRepository.userSettings,
        savedRecordings
    ) { visual, rec, play, settings, recordings ->
        FreePlayUiState(
            activePressedNotes = visual.activeNotes,
            lastPressedNote = visual.lastNote,
            trails = visual.trails,
            currentClockMs = visual.clockMs,
            startOctave = visual.octave,
            rangeMode = visual.range,
            isMetronomeRunning = rec.isMetronome,
            bpm = rec.bpm,
            currentBeat = rec.beat,
            isRecording = rec.isRecording,
            recordingDurationMs = rec.durationMs,
            recordedEventCount = rec.eventCount,
            isAudioRecordingEnabled = rec.audioEnabled,
            showSaveDialog = rec.saveDialog,
            audioPermissionNeeded = rec.permissionNeeded,
            audioOccupiedWarning = rec.audioOccupied,
            playingRecordingId = play.playingId,
            isPlayingRecording = play.isPlaying,
            playbackProgressFraction = play.progress,
            playbackCurrentMs = play.currentMs,
            playbackDurationMs = play.durationMs,
            userSettings = settings,
            savedRecordings = recordings
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FreePlayUiState()
    )

    init {
        cleanupOldPendingRecordings()
        startAnimationClock()
        observeMidi()
    }

    private fun cleanupOldPendingRecordings() {
        try {
            val pendingDir = File(context.filesDir, "pending_recordings")
            if (pendingDir.exists()) {
                pendingDir.deleteRecursively()
            }
        } catch (_: Exception) {
            // Ignore startup cleanup error
        }
    }

    private fun startAnimationClock() {
        animationClockJob = viewModelScope.launch {
            val startUptime = clock.elapsedRealtime()
            while (true) {
                val now = clock.elapsedRealtime() - startUptime
                _currentClockMs.value = now

                if (_isRecording.value) {
                    _recordingDurationMs.value = clock.elapsedRealtime() - recordingStartTimeMs
                }

                // Cleanup trails older than 6 seconds
                val currentTrails = _trails.value
                if (currentTrails.isNotEmpty()) {
                    val pruned = currentTrails.filter { trail ->
                        val end = trail.endMs ?: now
                        (now - end) < 6000L
                    }
                    if (pruned.size != currentTrails.size) {
                        _trails.value = pruned
                    }
                }

                delay(16L) // ~60 FPS
            }
        }
    }

    private fun observeMidi() {
        viewModelScope.launch {
            midiInput.noteEvents.collect { event ->
                handleNoteEvent(event.note, event.isNoteOn, event.velocity, event.channel)
            }
        }

        viewModelScope.launch {
            midiInput.controlEvents.collect { event ->
                if (_isRecording.value) {
                    val now = clock.elapsedRealtime()
                    val relMs = now - recordingStartTimeMs
                    _recordedEvents.add(
                        RecordedMidiEvent(
                            timestampMs = relMs,
                            isNoteOn = false,
                            note = 0,
                            velocity = 0,
                            channel = event.channel,
                            isControlChange = true,
                            controlNumber = event.controllerNumber,
                            controlValue = event.value
                        )
                    )
                    _recordedEventCount.value = _recordedEvents.size
                }
            }
        }
    }

    private fun handleNoteEvent(midiNote: Int, isNoteOn: Boolean, velocity: Int, channel: Int) {
        val now = _currentClockMs.value
        val nowMonotonic = clock.elapsedRealtime()

        if (isNoteOn) {
            _activeNotes.value = _activeNotes.value + midiNote
            _lastNote.value = midiNote
            totalNotesPlayed++

            // Track active practice session
            if (sessionStartTimeMs == 0L) {
                sessionStartTimeMs = clock.currentTimeMillis()
                lastNoteOnMonotonicMs = nowMonotonic
            } else {
                val delta = nowMonotonic - lastNoteOnMonotonicMs
                if (delta <= IDLE_TIMEOUT_MS) {
                    activePracticeAccumulatedMs += delta
                }
                lastNoteOnMonotonicMs = nowMonotonic
            }

            // Start rising trail
            val trailId = nextTrailId++
            activeTrailsMap[midiNote] = trailId

            val color = getNoteTrailColor(midiNote)
            val newTrail = RisingTrail(
                id = trailId,
                midiNote = midiNote,
                startMs = now,
                endMs = null,
                color = color
            )
            _trails.value = _trails.value + newTrail
        } else {
            _activeNotes.value = _activeNotes.value - midiNote

            // Cap trail end
            val trailId = activeTrailsMap.remove(midiNote)
            if (trailId != null) {
                _trails.value = _trails.value.map { trail ->
                    if (trail.id == trailId) trail.copy(endMs = now) else trail
                }
            }
        }

        // Buffer event if recording
        if (_isRecording.value) {
            val relMs = nowMonotonic - recordingStartTimeMs
            _recordedEvents.add(
                RecordedMidiEvent(
                    timestampMs = relMs,
                    isNoteOn = isNoteOn,
                    note = midiNote,
                    velocity = velocity,
                    channel = channel
                )
            )
            _recordedEventCount.value = _recordedEvents.size
        }
    }

    private fun getNoteTrailColor(midiNote: Int): Color {
        val octave = (midiNote / 12) - 1
        return when (octave % 6) {
            0 -> Color(0xFF00E5FF) // Neon Cyan
            1 -> Color(0xFF38BDF8) // Sky Blue
            2 -> Color(0xFF3B82F6) // Electric Blue
            3 -> Color(0xFF06B6D4) // Radiant Teal
            4 -> Color(0xFFF97316) // Vibrant Warm Orange
            else -> Color(0xFF10B981) // Emerald Green
        }
    }

    fun onVirtualKeyPressed(midiNote: Int) {
        midiInput.onVirtualKeyPressed(midiNote, 95)
    }

    fun onVirtualKeyReleased(midiNote: Int) {
        midiInput.onVirtualKeyReleased(midiNote)
    }

    fun setOctave(octave: Int) {
        _startOctave.value = octave.coerceIn(1, 6)
    }

    fun setRangeMode(mode: KeyboardRangeMode) {
        _rangeMode.value = mode
    }

    fun toggleMetronome() {
        if (metronomeController.isRunning.value) {
            metronomeController.stop()
        } else {
            metronomeController.start(_bpm.value)
        }
    }

    fun setBpm(newBpm: Int) {
        _bpm.value = newBpm.coerceIn(40, 240)
        if (metronomeController.isRunning.value) {
            metronomeController.setBpm(newBpm)
        }
    }

    fun setAudioRecordingEnabled(enabled: Boolean) {
        if (enabled) {
            if (!AudioInputCoordinator.isAvailable(AudioRecordOwner.FREE_PLAY_RECORDER)) {
                _audioOccupiedWarning.value = true
                return
            }
        }
        _isAudioRecordingEnabled.value = enabled
    }

    fun dismissAudioOccupiedWarning() {
        _audioOccupiedWarning.value = false
    }

    fun dismissPermissionNeeded() {
        _audioPermissionNeeded.value = false
    }

    // --- Recording Management ---

    fun toggleRecording() {
        if (_isRecording.value) {
            stopRecording(showDialog = true)
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        stopPlayback()

        currentRecordingId = UUID.randomUUID().toString()
        _recordedEvents.clear()
        _recordedEventCount.value = 0
        _recordingDurationMs.value = 0L
        recordingStartTimeMs = clock.elapsedRealtime()
        currentAudioFilePath = null
        _isRecording.value = true

        // Audio mic recording if enabled
        if (_isAudioRecordingEnabled.value) {
            if (!AudioInputCoordinator.requestAccess(AudioRecordOwner.FREE_PLAY_RECORDER)) {
                Log.w("FreePlay", "Audio recorder could not acquire microphone access")
                _audioOccupiedWarning.value = true
                return
            }

            try {
                val pendingDir = File(context.filesDir, "pending_recordings/$currentRecordingId")
                pendingDir.mkdirs()
                val audioFile = File(pendingDir, "audio.m4a")
                currentAudioFilePath = audioFile.absolutePath

                val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                recorder.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128000)
                    setAudioSamplingRate(44100)
                    setOutputFile(currentAudioFilePath)
                    prepare()
                    start()
                }
                mediaRecorder = recorder
            } catch (e: Exception) {
                Log.e("FreePlay", "MediaRecorder initialization failed", e)
                releaseMediaRecorder()
                currentAudioFilePath = null
            }
        }
    }

    private fun releaseMediaRecorder() {
        try {
            mediaRecorder?.let { recorder ->
                try {
                    recorder.stop()
                } catch (_: Exception) {
                    // Ignore stop error if record was too short
                }
                recorder.release()
            }
        } catch (_: Exception) {
            // Ignore release errors
        } finally {
            mediaRecorder = null
            AudioInputCoordinator.releaseAccess(AudioRecordOwner.FREE_PLAY_RECORDER)
        }
    }

    fun stopRecording(showDialog: Boolean = true) {
        if (!_isRecording.value) return

        _isRecording.value = false
        val finalDuration = clock.elapsedRealtime() - recordingStartTimeMs
        _recordingDurationMs.value = finalDuration

        releaseMediaRecorder()

        if (showDialog && _recordedEvents.isNotEmpty()) {
            _showSaveDialog.value = true
        }
    }

    fun saveRecording(title: String) {
        val finalTitle = title.ifBlank {
            "Bản thu " + SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
        }
        val recordingId = currentRecordingId.ifBlank { UUID.randomUUID().toString() }
        val duration = _recordingDurationMs.value
        val noteCount = _recordedEvents.count { it.isNoteOn }
        val hasAudio = currentAudioFilePath != null && File(currentAudioFilePath!!).exists() && File(currentAudioFilePath!!).length() > 1024

        val recording = FreePlayRecording(
            id = recordingId,
            title = finalTitle,
            createdAt = clock.currentTimeMillis(),
            durationMs = duration,
            noteCount = noteCount,
            hasAudio = hasAudio,
            audioFilePath = currentAudioFilePath,
            midiFilePath = null,
            inputSource = "VIRTUAL_KEYBOARD",
            bpm = _bpm.value,
            fileStatus = "READY"
        )

        val eventsCopy = _recordedEvents.toList()

        viewModelScope.launch {
            try {
                freePlayRepository.saveRecording(recording, eventsCopy)
            } catch (e: Exception) {
                Log.e("FreePlay", "Failed to save recording", e)
            } finally {
                _showSaveDialog.value = false
                _recordedEvents.clear()
                _recordedEventCount.value = 0
                currentAudioFilePath = null
            }
        }
    }

    fun discardRecording() {
        _showSaveDialog.value = false
        _recordedEvents.clear()
        _recordedEventCount.value = 0
        currentAudioFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) file.delete()
        }
        currentAudioFilePath = null
    }

    // --- Playback Management ---

    fun playRecording(recordingId: String) {
        if (_isPlayingRecording.value && _playingRecordingId.value == recordingId) {
            stopPlayback()
            return
        }

        stopPlayback()

        viewModelScope.launch {
            val recording = freePlayRepository.getRecordingById(recordingId) ?: return@launch
            _playingRecordingId.value = recordingId
            _isPlayingRecording.value = true
            _playbackDurationMs.value = recording.durationMs
            _playbackCurrentMs.value = 0L
            _playbackProgressFraction.value = 0f

            // Play audio if present
            if (recording.hasAudio && recording.audioFilePath != null) {
                try {
                    val file = File(recording.audioFilePath)
                    if (file.exists()) {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(file.absolutePath)
                            prepare()
                            start()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FreePlay", "Audio playback failed", e)
                }
            }

            // Playback MIDI events in coroutine
            val events = recording.events.sortedBy { it.timestampMs }
            val startTime = clock.elapsedRealtime()

            playbackJob = viewModelScope.launch {
                for (event in events) {
                    val targetTime = startTime + event.timestampMs
                    val now = clock.elapsedRealtime()
                    val waitMs = targetTime - now
                    if (waitMs > 0) {
                        delay(waitMs)
                    }

                    _playbackCurrentMs.value = event.timestampMs
                    _playbackProgressFraction.value = if (recording.durationMs > 0) {
                        (event.timestampMs.toFloat() / recording.durationMs).coerceIn(0f, 1f)
                    } else 0f

                    if (event.isNoteOn) {
                        onVirtualKeyPressed(event.note)
                    } else {
                        onVirtualKeyReleased(event.note)
                    }
                }

                // Wait remaining duration
                val remaining = (recording.durationMs - (clock.elapsedRealtime() - startTime)).coerceAtLeast(0)
                delay(remaining + 300L)
                stopPlayback()
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null

        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
                player.release()
            } catch (_: Exception) {
                // Ignore release errors
            }
            mediaPlayer = null
        }

        // Release all pressed keys
        _activeNotes.value.forEach { midiNote ->
            midiInput.onVirtualKeyReleased(midiNote)
        }
        _activeNotes.value = emptySet()

        _playingRecordingId.value = null
        _isPlayingRecording.value = false
        _playbackProgressFraction.value = 0f
        _playbackCurrentMs.value = 0L
    }

    fun deleteRecording(recordingId: String) {
        viewModelScope.launch {
            if (_playingRecordingId.value == recordingId) {
                stopPlayback()
            }
            freePlayRepository.deleteRecording(recordingId)
        }
    }

    fun renameRecording(recordingId: String, newTitle: String) {
        viewModelScope.launch {
            freePlayRepository.renameRecording(recordingId, newTitle)
        }
    }

    private fun persistPracticeSessionIfQualified() {
        if (activePracticeAccumulatedMs >= 10_000L && progressRepository != null) {
            val session = PracticeSession(
                id = UUID.randomUUID().toString(),
                sourceType = "FREE_PLAY",
                sourceId = null,
                practiceMode = PracticeMode.WAIT_FOR_NOTE,
                handMode = HandMode.BOTH,
                displayMode = DisplayMode.FALLING_NOTES,
                bpm = _bpm.value,
                startedAt = sessionStartTimeMs,
                durationMs = activePracticeAccumulatedMs,
                totalExpectedNotes = totalNotesPlayed,
                correctNotes = totalNotesPlayed,
                wrongNotes = 0,
                missedNotes = 0,
                earlyNotes = 0,
                lateNotes = 0,
                accuracy = 1.0f,
                sourceTitleSnapshot = "Chơi tự do",
                score = totalNotesPlayed * 10,
                maxStreak = totalNotesPlayed,
                inputSource = "VIRTUAL_KEYBOARD"
            )
            viewModelScope.launch {
                try {
                    progressRepository.savePracticeSession(session)
                } catch (e: Exception) {
                    Log.e("FreePlay", "Failed to save Free Play practice session", e)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
        metronomeController.stop()
        animationClockJob?.cancel()
        releaseMediaRecorder()
        persistPracticeSessionIfQualified()
    }

    class Factory(
        private val context: Context,
        private val midiInput: MidiInput,
        private val metronomeController: MetronomeController,
        private val settingsRepository: SettingsRepository,
        private val freePlayRepository: FreePlayRepository,
        private val progressRepository: ProgressRepository? = null,
        private val clock: PracticeClock = SystemPracticeClock()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FreePlayViewModel(
                context = context,
                midiInput = midiInput,
                metronomeController = metronomeController,
                settingsRepository = settingsRepository,
                freePlayRepository = freePlayRepository,
                progressRepository = progressRepository,
                clock = clock
            ) as T
        }
    }
}
