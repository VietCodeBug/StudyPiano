package com.ian.pianotrainer.feature.freeplay

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.FreePlayRecording
import com.ian.pianotrainer.domain.model.KeyboardRangeMode
import com.ian.pianotrainer.domain.model.RecordedMidiEvent
import com.ian.pianotrainer.domain.model.UserSettings
import com.ian.pianotrainer.domain.repository.FreePlayRepository
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
    val showSaveDialog: Boolean = false,
    val userSettings: UserSettings = UserSettings()
)

class FreePlayViewModel(
    private val context: Context,
    private val midiInput: MidiInput,
    private val metronomeController: MetronomeController,
    private val settingsRepository: SettingsRepository,
    private val freePlayRepository: FreePlayRepository
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
    private var recordingStartTimeMs: Long = 0L
    private var currentAudioFilePath: String? = null
    private var mediaRecorder: MediaRecorder? = null

    // Playback State
    private val _playingRecordingId = MutableStateFlow<String?>(null)
    private val _isPlayingRecording = MutableStateFlow(false)
    private var playbackJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    private var animationClockJob: Job? = null
    private var nextTrailId = 1L
    private val activeTrailsMap = mutableMapOf<Int, Long>() // midiNote to TrailId

    val savedRecordings: StateFlow<List<FreePlayRecording>> = freePlayRepository.getAllRecordings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<FreePlayUiState> = combine(
        _activeNotes,
        _lastNote,
        _trails,
        _currentClockMs,
        _startOctave,
        _rangeMode,
        metronomeController.isRunning,
        _bpm,
        metronomeController.currentBeat,
        _isRecording,
        _recordingDurationMs,
        _recordedEventCount,
        _isAudioRecordingEnabled,
        _playingRecordingId,
        _isPlayingRecording,
        _showSaveDialog,
        settingsRepository.userSettings,
        savedRecordings
    ) { args ->
        FreePlayUiState(
            activePressedNotes = args[0] as Set<Int>,
            lastPressedNote = args[1] as Int?,
            trails = args[2] as List<RisingTrail>,
            currentClockMs = args[3] as Long,
            startOctave = args[4] as Int,
            rangeMode = args[5] as KeyboardRangeMode,
            isMetronomeRunning = args[6] as Boolean,
            bpm = args[7] as Int,
            currentBeat = args[8] as Int,
            isRecording = args[9] as Boolean,
            recordingDurationMs = args[10] as Long,
            recordedEventCount = args[11] as Int,
            isAudioRecordingEnabled = args[12] as Boolean,
            playingRecordingId = args[13] as String?,
            isPlayingRecording = args[14] as Boolean,
            showSaveDialog = args[15] as Boolean,
            userSettings = args[16] as UserSettings,
            savedRecordings = args[17] as List<FreePlayRecording>
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FreePlayUiState()
    )

    init {
        startAnimationClock()
        observeMidi()
    }

    private fun startAnimationClock() {
        animationClockJob = viewModelScope.launch {
            val startUptime = SystemClock.uptimeMillis()
            while (true) {
                val now = SystemClock.uptimeMillis() - startUptime
                _currentClockMs.value = now

                if (_isRecording.value) {
                    _recordingDurationMs.value = SystemClock.uptimeMillis() - recordingStartTimeMs
                }

                // Cleanup trails older than 6 seconds
                val currentTrails = _trails.value
                if (currentTrails.isNotEmpty()) {
                    val pruned = currentTrails.filter { trail ->
                        val tailAge = if (trail.endMs != null) now - trail.endMs else 0L
                        tailAge < 5000L
                    }
                    if (pruned.size != currentTrails.size) {
                        _trails.value = pruned
                    }
                }

                delay(16) // ~60fps
            }
        }
    }

    private fun observeMidi() {
        viewModelScope.launch {
            midiInput.noteEvents.collect { event ->
                handleNoteEvent(event.note, event.isNoteOn && event.velocity > 0, event.velocity)
            }
        }
    }

    private fun handleNoteEvent(midiNote: Int, isNoteOn: Boolean, velocity: Int) {
        val now = _currentClockMs.value

        if (isNoteOn) {
            _activeNotes.value = _activeNotes.value + midiNote
            _lastNote.value = midiNote

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
            val elapsed = SystemClock.uptimeMillis() - recordingStartTimeMs
            _recordedEvents.add(
                RecordedMidiEvent(
                    timestampMs = elapsed,
                    isNoteOn = isNoteOn,
                    note = midiNote,
                    velocity = velocity
                )
            )
            _recordedEventCount.value = _recordedEvents.size
        }
    }

    private fun getNoteTrailColor(midiNote: Int): Color {
        val octave = (midiNote / 12) - 1
        return when (octave % 6) {
            0 -> Color(0xFF00E5FF) // Neon Cyan
            1 -> Color(0xFF3B82F6) // Electric Blue
            2 -> Color(0xFFA855F7) // Purple
            3 -> Color(0xFFEC4899) // Hot Pink
            4 -> Color(0xFFF97316) // Vibrant Orange
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
        _isAudioRecordingEnabled.value = enabled
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
        // Stop any active playback
        stopPlayback()

        _recordedEvents.clear()
        _recordedEventCount.value = 0
        _recordingDurationMs.value = 0L
        recordingStartTimeMs = SystemClock.uptimeMillis()
        _isRecording.value = true

        // Audio mic recording if enabled
        if (_isAudioRecordingEnabled.value) {
            try {
                val audioFile = File(context.cacheDir, "freeplay_${System.currentTimeMillis()}.m4a")
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
                // Audio recording initialization failed, continue with MIDI only
                mediaRecorder = null
                currentAudioFilePath = null
            }
        }
    }

    fun stopRecording(showDialog: Boolean = true) {
        if (!_isRecording.value) return

        _isRecording.value = false
        val finalDuration = SystemClock.uptimeMillis() - recordingStartTimeMs
        _recordingDurationMs.value = finalDuration

        // Stop media recorder safely
        mediaRecorder?.let { recorder ->
            try {
                recorder.stop()
                recorder.release()
            } catch (e: Exception) {
                // Ignore stop error
            }
            mediaRecorder = null
        }

        if (showDialog && _recordedEvents.isNotEmpty()) {
            _showSaveDialog.value = true
        }
    }

    fun saveRecording(title: String) {
        val finalTitle = title.ifBlank {
            "Bản thu " + SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
        }
        val recordingId = UUID.randomUUID().toString()
        val duration = _recordingDurationMs.value
        val noteCount = _recordedEvents.count { it.isNoteOn }
        val hasAudio = currentAudioFilePath != null && File(currentAudioFilePath!!).exists()

        val recording = FreePlayRecording(
            id = recordingId,
            title = finalTitle,
            createdAt = System.currentTimeMillis(),
            durationMs = duration,
            noteCount = noteCount,
            hasAudio = hasAudio,
            audioFilePath = currentAudioFilePath,
            midiFilePath = null
        )

        val eventsCopy = _recordedEvents.toList()

        viewModelScope.launch {
            freePlayRepository.saveRecording(recording, eventsCopy)
            _showSaveDialog.value = false
            _recordedEvents.clear()
            _recordedEventCount.value = 0
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
                    // Audio playback fallback
                }
            }

            // Playback MIDI events in coroutine
            val events = recording.events.sortedBy { it.timestampMs }
            val startTime = SystemClock.uptimeMillis()

            playbackJob = viewModelScope.launch {
                for (event in events) {
                    val targetTime = startTime + event.timestampMs
                    val now = SystemClock.uptimeMillis()
                    val waitMs = targetTime - now
                    if (waitMs > 0) {
                        delay(waitMs)
                    }

                    if (event.isNoteOn) {
                        onVirtualKeyPressed(event.note)
                    } else {
                        onVirtualKeyReleased(event.note)
                    }
                }

                // Wait remaining duration
                val remaining = (recording.durationMs - (SystemClock.uptimeMillis() - startTime)).coerceAtLeast(0)
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
            } catch (e: Exception) {
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
    }

    fun deleteRecording(recordingId: String) {
        viewModelScope.launch {
            if (_playingRecordingId.value == recordingId) {
                stopPlayback()
            }
            val rec = freePlayRepository.getRecordingById(recordingId)
            rec?.audioFilePath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }
            freePlayRepository.deleteRecording(recordingId)
        }
    }

    fun renameRecording(recordingId: String, newTitle: String) {
        viewModelScope.launch {
            freePlayRepository.renameRecording(recordingId, newTitle)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
        metronomeController.stop()
        animationClockJob?.cancel()
        mediaRecorder?.release()
    }

    class Factory(
        private val context: Context,
        private val midiInput: MidiInput,
        private val metronomeController: MetronomeController,
        private val settingsRepository: SettingsRepository,
        private val freePlayRepository: FreePlayRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FreePlayViewModel(
                context = context,
                midiInput = midiInput,
                metronomeController = metronomeController,
                settingsRepository = settingsRepository,
                freePlayRepository = freePlayRepository
            ) as T
        }
    }
}
