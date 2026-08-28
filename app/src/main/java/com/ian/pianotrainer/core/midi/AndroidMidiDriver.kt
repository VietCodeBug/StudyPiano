package com.ian.pianotrainer.core.midi

import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ian.pianotrainer.domain.model.DeviceConnectionState
import com.ian.pianotrainer.domain.model.DeviceType
import com.ian.pianotrainer.domain.model.MidiControlEvent
import com.ian.pianotrainer.domain.model.MidiNoteEvent
import com.ian.pianotrainer.domain.model.PianoDevice
import com.ian.pianotrainer.domain.service.MidiInput
import com.ian.pianotrainer.domain.service.PianoDeviceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AndroidMidiDriver(
    private val context: Context
) : MidiInput, PianoDeviceManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _noteEvents = MutableSharedFlow<MidiNoteEvent>(extraBufferCapacity = 64)
    override val noteEvents: SharedFlow<MidiNoteEvent> = _noteEvents.asSharedFlow()

    private val _controlEvents = MutableSharedFlow<MidiControlEvent>(extraBufferCapacity = 64)
    override val controlEvents: SharedFlow<MidiControlEvent> = _controlEvents.asSharedFlow()

    private val _connectionState = MutableStateFlow(DeviceConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<DeviceConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<PianoDevice?>(null)
    override val connectedDevice: StateFlow<PianoDevice?> = _connectedDevice.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<PianoDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<PianoDevice>> = _discoveredDevices.asStateFlow()

    private val midiManager: MidiManager? by lazy {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
        } else {
            null
        }
    }

    private var activeMidiDevice: MidiDevice? = null
    private var activeOutputPort: MidiOutputPort? = null
    private var activeDeviceInfo: MidiDeviceInfo? = null

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            refreshDevices()
        }

        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            if (activeDeviceInfo?.id == device.id) {
                scope.launch { disconnectDevice() }
            }
            refreshDevices()
        }

        override fun onDeviceStatusChanged(status: android.media.midi.MidiDeviceStatus) {
            // Status changed
        }
    }

    init {
        try {
            midiManager?.registerDeviceCallback(deviceCallback, mainHandler)
            refreshDevices()
        } catch (e: Exception) {
            Log.e("AndroidMidiDriver", "Failed to register MidiManager callback", e)
        }
    }

    private fun refreshDevices() {
        val manager = midiManager ?: return
        val deviceList = mutableListOf<PianoDevice>()

        for (info in manager.devices) {
            val properties = info.properties
            val name = properties.getString(MidiDeviceInfo.PROPERTY_NAME)
                ?: properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
                ?: "Thiết bị MIDI ${info.id}"

            val type = if (info.type == MidiDeviceInfo.TYPE_BLUETOOTH) {
                DeviceType.BLUETOOTH_MIDI
            } else {
                DeviceType.USB_MIDI
            }

            deviceList.add(
                PianoDevice(
                    id = "midi_dev_${info.id}",
                    name = name,
                    type = type,
                    isSimulated = false,
                    isConnected = (info.id == activeDeviceInfo?.id)
                )
            )
        }

        _discoveredDevices.value = deviceList
    }

    override suspend fun startScan() {
        _connectionState.value = DeviceConnectionState.SCANNING
        refreshDevices()
        if (_connectedDevice.value != null) {
            _connectionState.value = DeviceConnectionState.CONNECTED
        } else {
            _connectionState.value = DeviceConnectionState.DISCONNECTED
        }
    }

    override suspend fun stopScan() {
        if (_connectionState.value == DeviceConnectionState.SCANNING) {
            _connectionState.value = if (_connectedDevice.value != null) {
                DeviceConnectionState.CONNECTED
            } else {
                DeviceConnectionState.DISCONNECTED
            }
        }
    }

    override suspend fun connectDevice(device: PianoDevice) {
        val manager = midiManager ?: run {
            _connectionState.value = DeviceConnectionState.ERROR
            return
        }

        val targetInfo = manager.devices.firstOrNull { "midi_dev_${it.id}" == device.id }
        if (targetInfo == null) {
            _connectionState.value = DeviceConnectionState.ERROR
            return
        }

        _connectionState.value = DeviceConnectionState.CONNECTING

        manager.openDevice(targetInfo, { openedDevice ->
            if (openedDevice == null) {
                _connectionState.value = DeviceConnectionState.ERROR
                return@openDevice
            }

            activeMidiDevice = openedDevice
            activeDeviceInfo = targetInfo

            try {
                val outputPort = openedDevice.openOutputPort(0)
                if (outputPort != null) {
                    activeOutputPort = outputPort
                    outputPort.connect(midiReceiver)
                    _connectedDevice.value = device.copy(isConnected = true)
                    _connectionState.value = DeviceConnectionState.CONNECTED
                    refreshDevices()
                } else {
                    _connectionState.value = DeviceConnectionState.ERROR
                }
            } catch (e: Exception) {
                Log.e("AndroidMidiDriver", "Error opening MIDI output port", e)
                _connectionState.value = DeviceConnectionState.ERROR
            }
        }, mainHandler)
    }

    override suspend fun disconnectDevice() {
        try {
            activeOutputPort?.onDisconnect(midiReceiver)
            activeOutputPort?.close()
            activeMidiDevice?.close()
        } catch (e: Exception) {
            Log.e("AndroidMidiDriver", "Error disconnecting MIDI device", e)
        } finally {
            activeOutputPort = null
            activeMidiDevice = null
            activeDeviceInfo = null
            _connectedDevice.value = null
            _connectionState.value = DeviceConnectionState.DISCONNECTED
            refreshDevices()
        }
    }

    private val midiReceiver = object : MidiReceiver() {
        private var runningStatus = 0
        private val packetBuffer = IntArray(3)
        private var packetIndex = 0
        private var expectedBytes = 0

        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            for (i in 0 until count) {
                val b = msg[offset + i].toInt() and 0xFF

                if (b >= 0x80) {
                    if (b in 0xF8..0xFF) {
                        continue
                    }
                    if (b in 0x80..0xEF) {
                        runningStatus = b
                        packetIndex = 0
                        val type = b and 0xF0
                        expectedBytes = if (type == 0xC0 || type == 0xD0) 1 else 2
                    }
                } else if (runningStatus != 0) {
                    packetBuffer[packetIndex++] = b
                    if (packetIndex >= expectedBytes) {
                        dispatchMidiMessage(runningStatus, packetBuffer[0], packetBuffer.getOrElse(1) { 0 }, timestamp)
                        packetIndex = 0
                    }
                }
            }
        }
    }

    private fun dispatchMidiMessage(status: Int, byte1: Int, byte2: Int, timestamp: Long) {
        val type = status and 0xF0
        val channel = status and 0x0F

        when (type) {
            0x80 -> { // Note Off
                scope.launch {
                    _noteEvents.emit(
                        MidiNoteEvent(
                            channel = channel,
                            note = byte1,
                            velocity = byte2,
                            isNoteOn = false,
                            timestampMs = System.currentTimeMillis()
                        )
                    )
                }
            }
            0x90 -> { // Note On
                val isNoteOn = byte2 > 0
                scope.launch {
                    _noteEvents.emit(
                        MidiNoteEvent(
                            channel = channel,
                            note = byte1,
                            velocity = byte2,
                            isNoteOn = isNoteOn,
                            timestampMs = System.currentTimeMillis()
                        )
                    )
                }
            }
            0xB0 -> { // Control Change
                scope.launch {
                    _controlEvents.emit(
                        MidiControlEvent(
                            channel = channel,
                            controllerNumber = byte1,
                            value = byte2,
                            timestampMs = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    override fun onVirtualKeyPressed(midiNote: Int, velocity: Int) {
        scope.launch {
            _noteEvents.emit(
                MidiNoteEvent(
                    note = midiNote,
                    velocity = velocity,
                    isNoteOn = true,
                    timestampMs = System.currentTimeMillis()
                )
            )
        }
    }

    override fun onVirtualKeyReleased(midiNote: Int) {
        scope.launch {
            _noteEvents.emit(
                MidiNoteEvent(
                    note = midiNote,
                    velocity = 0,
                    isNoteOn = false,
                    timestampMs = System.currentTimeMillis()
                )
            )
        }
    }
}
