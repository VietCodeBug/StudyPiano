package com.ian.pianotrainer.feature.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.core.midi.AndroidMidiDriver
import com.ian.pianotrainer.domain.model.DeviceConnectionState
import com.ian.pianotrainer.domain.model.MidiControlEvent
import com.ian.pianotrainer.domain.model.MidiNoteEvent
import com.ian.pianotrainer.domain.model.PianoDevice
import com.ian.pianotrainer.domain.model.UserSettings
import com.ian.pianotrainer.domain.repository.SettingsRepository
import com.ian.pianotrainer.domain.service.MidiInput
import com.ian.pianotrainer.domain.service.PianoDeviceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MidiDiagnosticUiState(
    val lastEvent: MidiNoteEvent? = null,
    val eventLogs: List<MidiNoteEvent> = emptyList(),
    val activePressedNotes: Set<Int> = emptySet(),
    val userSettings: UserSettings = UserSettings(),
    val connectionState: DeviceConnectionState = DeviceConnectionState.DISCONNECTED,
    val connectedDevice: PianoDevice? = null,
    val noteOnCount: Long = 0L,
    val noteOffCount: Long = 0L,
    val isSustainPedalDown: Boolean = false,
    val eventsPerSecond: Int = 0,
    val isRawHexMode: Boolean = false,
    val isBleScanning: Boolean = false
)

class MidiDiagnosticViewModel(
    private val midiInput: MidiInput,
    private val deviceManager: PianoDeviceManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _eventLogs = MutableStateFlow<List<MidiNoteEvent>>(emptyList())
    private val _lastEvent = MutableStateFlow<MidiNoteEvent?>(null)
    private val _activeNotes = MutableStateFlow<Set<Int>>(emptySet())
    private val _noteOnCount = MutableStateFlow(0L)
    private val _noteOffCount = MutableStateFlow(0L)
    private val _isSustainPedalDown = MutableStateFlow(false)
    private val _eventsPerSecond = MutableStateFlow(0)
    private val _isRawHexMode = MutableStateFlow(false)

    private val midiDriver = midiInput as? AndroidMidiDriver
    private var windowEventCount = 0

    val uiState: StateFlow<MidiDiagnosticUiState> = combine(
        combine(_lastEvent, _eventLogs, _activeNotes, settingsRepository.userSettings) { a, b, c, d -> Quad(a, b, c, d) },
        combine(deviceManager.connectionState, deviceManager.connectedDevice, _noteOnCount, _noteOffCount) { a, b, c, d -> Quad(a, b, c, d) },
        combine(_isSustainPedalDown, _eventsPerSecond, _isRawHexMode, midiDriver?.isBleScanning ?: MutableStateFlow(false)) { a, b, c, d -> Quad(a, b, c, d) }
    ) { q1, q2, q3 ->
        MidiDiagnosticUiState(
            lastEvent = q1.first,
            eventLogs = q1.second,
            activePressedNotes = q1.third,
            userSettings = q1.fourth,
            connectionState = q2.first,
            connectedDevice = q2.second,
            noteOnCount = q2.third,
            noteOffCount = q2.fourth,
            isSustainPedalDown = q3.first,
            eventsPerSecond = q3.second,
            isRawHexMode = q3.third,
            isBleScanning = q3.fourth
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MidiDiagnosticUiState()
    )

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    init {
        observeMidi()
        startRateCalculator()
    }

    private fun observeMidi() {
        viewModelScope.launch {
            midiInput.noteEvents.collect { event ->
                windowEventCount++
                _lastEvent.value = event
                _eventLogs.value = (listOf(event) + _eventLogs.value).take(100)

                if (event.isNoteOn && event.velocity > 0) {
                    _noteOnCount.value++
                    _activeNotes.value = _activeNotes.value + event.note
                } else {
                    _noteOffCount.value++
                    _activeNotes.value = _activeNotes.value - event.note
                }
            }
        }

        viewModelScope.launch {
            midiInput.controlEvents.collect { event ->
                windowEventCount++
                if (event.controllerNumber == 64) { // Damper/Sustain Pedal
                    _isSustainPedalDown.value = (event.value >= 64)
                }
            }
        }
    }

    private fun startRateCalculator() {
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                _eventsPerSecond.value = windowEventCount
                windowEventCount = 0
            }
        }
    }

    fun toggleRawHexMode() {
        _isRawHexMode.value = !_isRawHexMode.value
    }

    fun onVirtualKeyPressed(midiNote: Int) {
        midiInput.onVirtualKeyPressed(midiNote, 85)
    }

    fun onVirtualKeyReleased(midiNote: Int) {
        midiInput.onVirtualKeyReleased(midiNote)
    }

    fun clearLogs() {
        _eventLogs.value = emptyList()
        _lastEvent.value = null
        _noteOnCount.value = 0L
        _noteOffCount.value = 0L
    }

    fun copyDiagnosticReport(context: Context) {
        val state = uiState.value
        val report = buildString {
            appendLine("=== PIANO TRAINER MIDI DIAGNOSTIC REPORT ===")
            appendLine("Connection: ${state.connectionState}")
            appendLine("Device: ${state.connectedDevice?.name ?: "None"} (${state.connectedDevice?.type?.displayName ?: "N/A"})")
            appendLine("Address: ${state.connectedDevice?.bluetoothAddress ?: "N/A"}")
            appendLine("Port: ${state.connectedDevice?.activePortIndex ?: 0}")
            appendLine("Note On count: ${state.noteOnCount}")
            appendLine("Note Off count: ${state.noteOffCount}")
            appendLine("Sustain Pedal (CC64): ${if (state.isSustainPedalDown) "DOWN" else "UP"}")
            appendLine("Events/sec: ${state.eventsPerSecond}")
            appendLine("\n--- Last Events ---")
            state.eventLogs.take(20).forEach { ev ->
                appendLine("[${ev.timestampMs}] ${ev.inputSource.name} - Note ${ev.note} (Vel ${ev.velocity}) - ${if (ev.isNoteOn) "ON" else "OFF"}")
            }
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("PianoTrainer_Midi_Report", report)
        clipboard?.setPrimaryClip(clip)
    }

    class Factory(
        private val midiInput: MidiInput,
        private val deviceManager: PianoDeviceManager,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MidiDiagnosticViewModel(midiInput, deviceManager, settingsRepository) as T
        }
    }
}
