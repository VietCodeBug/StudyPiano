package com.ian.pianotrainer.core.midi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import com.ian.pianotrainer.domain.model.DeviceConnectionState
import com.ian.pianotrainer.domain.model.DeviceType
import com.ian.pianotrainer.domain.model.MidiControlEvent
import com.ian.pianotrainer.domain.model.MidiInputSource
import com.ian.pianotrainer.domain.model.MidiNoteEvent
import com.ian.pianotrainer.domain.model.PianoDevice
import com.ian.pianotrainer.domain.service.MidiInput
import com.ian.pianotrainer.domain.service.PianoDeviceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class AndroidMidiDriver(
    private val context: Context
) : MidiInput, PianoDeviceManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _noteEvents = MutableSharedFlow<MidiNoteEvent>(extraBufferCapacity = 128)
    override val noteEvents: SharedFlow<MidiNoteEvent> = _noteEvents.asSharedFlow()

    private val _controlEvents = MutableSharedFlow<MidiControlEvent>(extraBufferCapacity = 128)
    override val controlEvents: SharedFlow<MidiControlEvent> = _controlEvents.asSharedFlow()

    private val _connectionState = MutableStateFlow(DeviceConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<DeviceConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<PianoDevice?>(null)
    override val connectedDevice: StateFlow<PianoDevice?> = _connectedDevice.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<PianoDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<PianoDevice>> = _discoveredDevices.asStateFlow()

    private val _isBleScanning = MutableStateFlow(false)
    val isBleScanning: StateFlow<Boolean> = _isBleScanning.asStateFlow()

    private val _lastDiagnosticEvent = MutableStateFlow<MidiNoteEvent?>(null)
    val lastDiagnosticEvent: StateFlow<MidiNoteEvent?> = _lastDiagnosticEvent.asStateFlow()

    // Microphone pitch detector integration
    val micPitchDetector = MicrophonePitchDetector(context, scope)

    private val midiManager: MidiManager? by lazy {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
        } else {
            null
        }
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bm?.adapter
    }

    private var activeMidiDevice: MidiDevice? = null
    private var activeOutputPort: MidiOutputPort? = null
    private var activeDeviceInfo: MidiDeviceInfo? = null
    private var scanTimeoutJob: Job? = null

    // Scanned BLE devices cache: address -> BluetoothDevice
    private val discoveredBluetoothDevices = mutableMapOf<String, BluetoothDevice>()

    companion object {
        private const val TAG = "AndroidMidiDriver"
        val BLE_MIDI_SERVICE_UUID: UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
        val BLE_MIDI_PARCEL_UUID: ParcelUuid = ParcelUuid(BLE_MIDI_SERVICE_UUID)
    }

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

    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device = result.device ?: return
            val address = device.address ?: return
            val name = result.scanRecord?.deviceName ?: device.name ?: "Thiết bị BLE (${address.takeLast(5)})"
            val rssi = result.rssi
            val serviceUuids = result.scanRecord?.serviceUuids
            val hasMidiUuid = serviceUuids?.any { it.uuid == BLE_MIDI_SERVICE_UUID } ?: false

            discoveredBluetoothDevices[address] = device

            val currentList = _discoveredDevices.value.toMutableList()
            val existingIndex = currentList.indexOfFirst { it.bluetoothAddress == address || it.id == "ble_$address" }

            val pianoDev = PianoDevice(
                id = "ble_$address",
                name = name,
                type = DeviceType.BLUETOOTH_MIDI,
                isSimulated = false,
                isConnected = (_connectedDevice.value?.bluetoothAddress == address),
                isConnecting = (_connectedDevice.value?.bluetoothAddress == address && _connectionState.value == DeviceConnectionState.CONNECTING),
                signalStrength = rssi,
                bluetoothAddress = address,
                hasBleMidiService = hasMidiUuid
            )

            if (existingIndex >= 0) {
                currentList[existingIndex] = pianoDev
            } else {
                currentList.add(pianoDev)
            }

            _discoveredDevices.value = currentList
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with errorCode: $errorCode")
            _isBleScanning.value = false
        }
    }

    init {
        try {
            midiManager?.registerDeviceCallback(deviceCallback, mainHandler)
            refreshDevices()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register MidiManager callback", e)
        }

        // Forward microphone events into main noteEvents flow
        scope.launch {
            micPitchDetector.noteEvents.collect { event ->
                _noteEvents.emit(event)
                _lastDiagnosticEvent.value = event
            }
        }
    }

    fun refreshDevices() {
        val manager = midiManager ?: return
        val deviceList = mutableListOf<PianoDevice>()

        // 1. Devices already registered with Android MIDI service (USB or paired MIDI)
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

            val isThisConnected = (info.id == activeDeviceInfo?.id)
            val portCount = info.inputPortCount + info.outputPortCount

            deviceList.add(
                PianoDevice(
                    id = "midi_dev_${info.id}",
                    name = name,
                    type = type,
                    isSimulated = false,
                    isConnected = isThisConnected,
                    hasBleMidiService = true,
                    portCount = maxOf(1, portCount)
                )
            )
        }

        // 2. Add previously discovered BLE devices
        for ((address, dev) in discoveredBluetoothDevices) {
            if (deviceList.none { it.bluetoothAddress == address || it.id == "ble_$address" }) {
                deviceList.add(
                    PianoDevice(
                        id = "ble_$address",
                        name = dev.name ?: "Thiết bị BLE (${address.takeLast(5)})",
                        type = DeviceType.BLUETOOTH_MIDI,
                        isSimulated = false,
                        isConnected = (_connectedDevice.value?.bluetoothAddress == address),
                        bluetoothAddress = address,
                        hasBleMidiService = true
                    )
                )
            }
        }

        _discoveredDevices.value = deviceList
    }

    @SuppressLint("MissingPermission")
    override suspend fun startScan() {
        startScanWithFilter(useMidiFilter = true)
    }

    @SuppressLint("MissingPermission")
    fun startScanWithFilter(useMidiFilter: Boolean) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth is not enabled or not supported")
            _connectionState.value = DeviceConnectionState.DISCONNECTED
            refreshDevices()
            return
        }

        val scanner: BluetoothLeScanner? = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BluetoothLeScanner is not available")
            refreshDevices()
            return
        }

        _isBleScanning.value = true
        _connectionState.value = DeviceConnectionState.SCANNING
        refreshDevices()

        val filters = mutableListOf<ScanFilter>()
        if (useMidiFilter) {
            filters.add(
                ScanFilter.Builder()
                    .setServiceUuid(BLE_MIDI_PARCEL_UUID)
                    .build()
            )
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.stopScan(bleScanCallback)
            scanner.startScan(filters, settings, bleScanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting BLE scan", e)
            _isBleScanning.value = false
            return
        }

        // Auto-stop scan after 10 seconds
        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(10_000L)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun stopScan() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null

        if (_isBleScanning.value) {
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping BLE scan", e)
            }
            _isBleScanning.value = false
        }

        if (_connectionState.value == DeviceConnectionState.SCANNING) {
            _connectionState.value = if (_connectedDevice.value != null) {
                DeviceConnectionState.CONNECTED
            } else {
                DeviceConnectionState.DISCONNECTED
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connectDevice(device: PianoDevice) {
        val manager = midiManager ?: run {
            _connectionState.value = DeviceConnectionState.ERROR
            return
        }

        // 1. Disconnect any existing device
        disconnectDevice()

        _connectionState.value = DeviceConnectionState.CONNECTING

        // Check if device is a scanned BluetoothDevice that needs openBluetoothDevice
        val btDevice = device.bluetoothAddress?.let { discoveredBluetoothDevices[it] }

        if (btDevice != null && device.type == DeviceType.BLUETOOTH_MIDI) {
            // BLE Connection via MidiManager.openBluetoothDevice
            try {
                manager.openBluetoothDevice(btDevice, { openedDevice ->
                    if (openedDevice == null) {
                        Log.e(TAG, "MidiManager.openBluetoothDevice returned null for ${btDevice.name}")
                        _connectionState.value = DeviceConnectionState.ERROR
                        return@openBluetoothDevice
                    }
                    attachToMidiDevice(openedDevice, device)
                }, mainHandler)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling openBluetoothDevice", e)
                _connectionState.value = DeviceConnectionState.ERROR
            }
        } else {
            // USB or already enumerated system MIDI device
            val targetInfo = manager.devices.firstOrNull {
                "midi_dev_${it.id}" == device.id || (device.bluetoothAddress != null && it.properties.getString("bluetooth_address") == device.bluetoothAddress)
            }

            if (targetInfo != null) {
                manager.openDevice(targetInfo, { openedDevice ->
                    if (openedDevice == null) {
                        Log.e(TAG, "MidiManager.openDevice returned null for ${device.name}")
                        _connectionState.value = DeviceConnectionState.ERROR
                        return@openDevice
                    }
                    attachToMidiDevice(openedDevice, device)
                }, mainHandler)
            } else {
                Log.e(TAG, "Device target not found for id: ${device.id}")
                _connectionState.value = DeviceConnectionState.ERROR
            }
        }
    }

    private fun attachToMidiDevice(openedDevice: MidiDevice, targetDevice: PianoDevice) {
        activeMidiDevice = openedDevice
        activeDeviceInfo = openedDevice.info

        val portInfoList = openedDevice.info.ports
        // Find port with TYPE_OUTPUT because piano sends MIDI output into the receiver
        val outputPortInfo = portInfoList.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
            ?: portInfoList.firstOrNull()

        val portNumber = outputPortInfo?.portNumber ?: 0

        try {
            val outputPort = openedDevice.openOutputPort(portNumber)
            if (outputPort != null) {
                activeOutputPort = outputPort
                outputPort.connect(midiReceiver)

                val connectedDev = targetDevice.copy(
                    isConnected = true,
                    isConnecting = false,
                    activePortIndex = portNumber,
                    portCount = portInfoList.size
                )
                _connectedDevice.value = connectedDev
                _connectionState.value = DeviceConnectionState.CONNECTED
                refreshDevices()
                Log.i(TAG, "Successfully connected to MIDI device ${targetDevice.name} on output port $portNumber")
            } else {
                Log.e(TAG, "Failed to open output port $portNumber on device ${targetDevice.name}")
                _connectionState.value = DeviceConnectionState.ERROR
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening MIDI output port", e)
            _connectionState.value = DeviceConnectionState.ERROR
        }
    }

    override suspend fun disconnectDevice() {
        try {
            activeOutputPort?.onDisconnect(midiReceiver)
            activeOutputPort?.close()
            activeMidiDevice?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting MIDI device", e)
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

                // Realtime bytes (0xF8 - 0xFF) can appear anywhere and should not interrupt running status
                if (b in 0xF8..0xFF) {
                    continue
                }

                if (b >= 0x80) {
                    if (b in 0x80..0xEF) {
                        runningStatus = b
                        packetIndex = 0
                        val type = b and 0xF0
                        expectedBytes = if (type == 0xC0 || type == 0xD0) 1 else 2
                    } else {
                        // System common or Sysex (0xF0-0xF7): reset running status
                        runningStatus = 0
                        packetIndex = 0
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
        val nowMs = SystemClock.elapsedRealtime()
        val devId = _connectedDevice.value?.id ?: "midi_device"
        val inputSource = if (_connectedDevice.value?.type == DeviceType.BLUETOOTH_MIDI) {
            MidiInputSource.BLUETOOTH_LE
        } else {
            MidiInputSource.USB
        }

        when (type) {
            0x80 -> { // Note Off
                val event = MidiNoteEvent(
                    channel = channel,
                    note = byte1,
                    velocity = byte2,
                    isNoteOn = false,
                    timestampMs = nowMs,
                    inputSource = inputSource,
                    deviceId = devId
                )
                scope.launch {
                    _noteEvents.emit(event)
                    _lastDiagnosticEvent.value = event
                }
            }
            0x90 -> { // Note On (velocity 0 is Note Off)
                val isNoteOn = byte2 > 0
                val event = MidiNoteEvent(
                    channel = channel,
                    note = byte1,
                    velocity = byte2,
                    isNoteOn = isNoteOn,
                    timestampMs = nowMs,
                    inputSource = inputSource,
                    deviceId = devId
                )
                scope.launch {
                    _noteEvents.emit(event)
                    _lastDiagnosticEvent.value = event
                }
            }
            0xB0 -> { // Control Change (e.g. Sustain CC64)
                val event = MidiControlEvent(
                    channel = channel,
                    controllerNumber = byte1,
                    value = byte2,
                    timestampMs = nowMs,
                    inputSource = inputSource,
                    deviceId = devId
                )
                scope.launch {
                    _controlEvents.emit(event)
                }
            }
        }
    }

    override fun onVirtualKeyPressed(midiNote: Int, velocity: Int) {
        val nowMs = SystemClock.elapsedRealtime()
        val event = MidiNoteEvent(
            note = midiNote,
            velocity = velocity,
            isNoteOn = true,
            timestampMs = nowMs,
            inputSource = MidiInputSource.VIRTUAL_KEYBOARD,
            deviceId = "virtual_keyboard"
        )
        scope.launch {
            _noteEvents.emit(event)
            _lastDiagnosticEvent.value = event
        }
    }

    override fun onVirtualKeyReleased(midiNote: Int) {
        val nowMs = SystemClock.elapsedRealtime()
        val event = MidiNoteEvent(
            note = midiNote,
            velocity = 0,
            isNoteOn = false,
            timestampMs = nowMs,
            inputSource = MidiInputSource.VIRTUAL_KEYBOARD,
            deviceId = "virtual_keyboard"
        )
        scope.launch {
            _noteEvents.emit(event)
            _lastDiagnosticEvent.value = event
        }
    }
}
