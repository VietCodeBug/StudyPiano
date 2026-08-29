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
import com.ian.pianotrainer.domain.model.PianoDeviceCapability
import com.ian.pianotrainer.domain.model.ScanMode
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

object MidiErrorCodes {
    const val BT_NOT_SUPPORTED = "BT_NOT_SUPPORTED"
    const val BT_PERMISSION_DENIED = "BT_PERMISSION_DENIED"
    const val BT_DISABLED = "BT_DISABLED"
    const val BLE_MIDI_SERVICE_NOT_FOUND = "BLE_MIDI_SERVICE_NOT_FOUND"
    const val MIDI_DEVICE_OPEN_FAILED = "MIDI_DEVICE_OPEN_FAILED"
    const val MIDI_OUTPUT_PORT_NOT_FOUND = "MIDI_OUTPUT_PORT_NOT_FOUND"
    const val MIDI_PORT_OPEN_FAILED = "MIDI_PORT_OPEN_FAILED"
}

data class MidiRawLogEntry(
    val timestampMs: Long,
    val relativeTimeMs: Long,
    val description: String,
    val rawHexBytes: String,
    val channel: Int,
    val noteOrCc: Int,
    val valueOrVelocity: Int
)

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

    private val _rawLogRingBuffer = MutableStateFlow<List<MidiRawLogEntry>>(emptyList())
    val rawLogRingBuffer: StateFlow<List<MidiRawLogEntry>> = _rawLogRingBuffer.asStateFlow()

    private val _lastErrorCode = MutableStateFlow<String?>(null)
    val lastErrorCode: StateFlow<String?> = _lastErrorCode.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private var sessionStartMonotonicMs: Long = SystemClock.elapsedRealtime()

    // Microphone pitch detector integration
    val micPitchDetector = MicrophonePitchDetector(context, scope)

    private val midiManager: MidiManager? by lazy {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
        } else {
            null
        }
    }

    val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bm?.adapter
    }

    private var activeMidiDevice: MidiDevice? = null
    private var activeOutputPort: MidiOutputPort? = null
    private var activeDeviceInfo: MidiDeviceInfo? = null
    private var scanTimeoutJob: Job? = null

    // Scanned BLE devices cache: address -> BluetoothDevice
    private val discoveredBluetoothDevices = mutableMapOf<String, BluetoothDevice>()
    private val discoveredDeviceCapabilities = mutableMapOf<String, PianoDeviceCapability>()

    private val parser = MidiStreamParser(MidiInputSource.BLUETOOTH_LE, null)

    companion object {
        private const val TAG = "AndroidMidiDriver"
        val BLE_MIDI_SERVICE_UUID: UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
        val BLE_MIDI_CHARACTERISTIC_UUID: UUID = UUID.fromString("7772E5DB-3868-4112-A1A9-F2669D106BF3")
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
            val name = result.scanRecord?.deviceName ?: device.name ?: "Thiết bị không tên"
            val rssi = result.rssi
            val serviceUuids = result.scanRecord?.serviceUuids
            val hasMidiUuid = serviceUuids?.any { it.uuid == BLE_MIDI_SERVICE_UUID } ?: false

            discoveredBluetoothDevices[address] = device
            val capability = if (hasMidiUuid) {
                PianoDeviceCapability.BLE_MIDI_VERIFIED
            } else {
                // If it looks like audio or has no MIDI UUID
                PianoDeviceCapability.BLUETOOTH_AUDIO_ONLY
            }
            discoveredDeviceCapabilities[address] = capability

            val currentList = _discoveredDevices.value.toMutableList()
            val existingIndex = currentList.indexOfFirst { it.bluetoothAddress == address || it.id == "ble_$address" }

            val pianoDev = PianoDevice(
                id = "ble_$address",
                name = name,
                type = if (hasMidiUuid) DeviceType.BLUETOOTH_MIDI else DeviceType.BLUETOOTH_MIDI,
                capability = capability,
                isSimulated = false,
                isConnected = (_connectedDevice.value?.bluetoothAddress == address && _connectionState.value == DeviceConnectionState.CONNECTED),
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
            _lastErrorCode.value = "BLE_SCAN_FAILED"
            _lastErrorMessage.value = "Quét BLE thất bại (Mã lỗi $errorCode). Hãy kiểm tra lại Bluetooth."
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

            val capability = if (info.type == MidiDeviceInfo.TYPE_BLUETOOTH) {
                PianoDeviceCapability.BLE_MIDI_VERIFIED
            } else {
                PianoDeviceCapability.USB_MIDI
            }

            val isThisConnected = (info.id == activeDeviceInfo?.id && _connectionState.value == DeviceConnectionState.CONNECTED)
            val portCount = info.inputPortCount + info.outputPortCount

            deviceList.add(
                PianoDevice(
                    id = "midi_dev_${info.id}",
                    name = name,
                    type = type,
                    capability = capability,
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
                val capability = discoveredDeviceCapabilities[address] ?: PianoDeviceCapability.UNKNOWN_BLUETOOTH
                val hasMidiService = (capability == PianoDeviceCapability.BLE_MIDI_VERIFIED)
                val devName = try {
                    dev.name ?: "Thiết bị không tên"
                } catch (e: SecurityException) {
                    "Thiết bị BLE (${address.takeLast(5)})"
                }

                deviceList.add(
                    PianoDevice(
                        id = "ble_$address",
                        name = devName,
                        type = DeviceType.BLUETOOTH_MIDI,
                        capability = capability,
                        isSimulated = false,
                        isConnected = (_connectedDevice.value?.bluetoothAddress == address && _connectionState.value == DeviceConnectionState.CONNECTED),
                        bluetoothAddress = address,
                        hasBleMidiService = hasMidiService
                    )
                )
            }
        }

        _discoveredDevices.value = deviceList
    }

    @SuppressLint("MissingPermission")
    override suspend fun startScan() {
        startScanWithMode(ScanMode.MIDI_ONLY)
    }

    @SuppressLint("MissingPermission")
    fun startScanWithMode(mode: ScanMode) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _lastErrorCode.value = MidiErrorCodes.BT_NOT_SUPPORTED
            _lastErrorMessage.value = "Thiết bị này không hỗ trợ phần cứng Bluetooth."
            _connectionState.value = DeviceConnectionState.DISCONNECTED
            return
        }

        if (!adapter.isEnabled) {
            _lastErrorCode.value = MidiErrorCodes.BT_DISABLED
            _lastErrorMessage.value = "Bluetooth đang tắt. Hãy bật Bluetooth trong Cài đặt để kết nối đàn."
            _connectionState.value = DeviceConnectionState.DISCONNECTED
            return
        }

        val scanner: BluetoothLeScanner? = try {
            adapter.bluetoothLeScanner
        } catch (e: SecurityException) {
            _lastErrorCode.value = MidiErrorCodes.BT_PERMISSION_DENIED
            _lastErrorMessage.value = "Chưa được cấp quyền quét Bluetooth."
            return
        }

        if (scanner == null) {
            _lastErrorCode.value = MidiErrorCodes.BT_DISABLED
            _lastErrorMessage.value = "Không thể khởi tạo bộ quét Bluetooth LE."
            return
        }

        _isBleScanning.value = true
        _connectionState.value = DeviceConnectionState.SCANNING
        _lastErrorCode.value = null
        _lastErrorMessage.value = null
        refreshDevices()

        val filters = mutableListOf<ScanFilter>()
        if (mode == ScanMode.MIDI_ONLY) {
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
        } catch (e: SecurityException) {
            _lastErrorCode.value = MidiErrorCodes.BT_PERMISSION_DENIED
            _lastErrorMessage.value = "Thiếu quyền Bluetooth để quét thiết bị."
            _isBleScanning.value = false
            return
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting BLE scan", e)
            _isBleScanning.value = false
            _lastErrorCode.value = "BLE_SCAN_ERROR"
            _lastErrorMessage.value = "Lỗi khi quét: ${e.localizedMessage}"
            return
        }

        // Auto-stop scan after 12 seconds
        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(12_000L)
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
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException stopping scan", e)
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
        stopScan()

        if (!device.hasBleMidiService && device.capability == PianoDeviceCapability.BLUETOOTH_AUDIO_ONLY) {
            _lastErrorCode.value = MidiErrorCodes.BLE_MIDI_SERVICE_NOT_FOUND
            _lastErrorMessage.value = "Thiết bị này có thể là kênh âm thanh Bluetooth (Audio). MIDI điều khiển nốt có thể xuất hiện bằng một thiết bị khác hoặc qua USB MIDI."
            _connectionState.value = DeviceConnectionState.ERROR
            return
        }

        val manager = midiManager ?: run {
            _lastErrorCode.value = "MIDI_SERVICE_UNAVAILABLE"
            _lastErrorMessage.value = "Hệ thống Android MIDI không khả dụng trên thiết bị này."
            _connectionState.value = DeviceConnectionState.ERROR
            return
        }

        disconnectDevice()

        _connectionState.value = DeviceConnectionState.CONNECTING
        _lastErrorCode.value = null
        _lastErrorMessage.value = null

        val btDevice = device.bluetoothAddress?.let { discoveredBluetoothDevices[it] }

        if (btDevice != null && device.type == DeviceType.BLUETOOTH_MIDI) {
            try {
                manager.openBluetoothDevice(btDevice, { openedDevice ->
                    if (openedDevice == null) {
                        Log.e(TAG, "MidiManager.openBluetoothDevice returned null for ${device.name}")
                        _lastErrorCode.value = MidiErrorCodes.MIDI_DEVICE_OPEN_FAILED
                        _lastErrorMessage.value = "Không thể mở thiết bị Bluetooth MIDI. Vui lòng thử lại."
                        _connectionState.value = DeviceConnectionState.ERROR
                        return@openBluetoothDevice
                    }
                    attachToMidiDevice(openedDevice, device)
                }, mainHandler)
            } catch (e: SecurityException) {
                _lastErrorCode.value = MidiErrorCodes.BT_PERMISSION_DENIED
                _lastErrorMessage.value = "Thiếu quyền kết nối Bluetooth (BLUETOOTH_CONNECT)."
                _connectionState.value = DeviceConnectionState.ERROR
            } catch (e: Exception) {
                Log.e(TAG, "Error calling openBluetoothDevice", e)
                _lastErrorCode.value = MidiErrorCodes.MIDI_DEVICE_OPEN_FAILED
                _lastErrorMessage.value = "Lỗi kết nối Bluetooth MIDI: ${e.localizedMessage}"
                _connectionState.value = DeviceConnectionState.ERROR
            }
        } else {
            val targetInfo = manager.devices.firstOrNull {
                "midi_dev_${it.id}" == device.id || (device.bluetoothAddress != null && it.properties.getString("bluetooth_address") == device.bluetoothAddress)
            }

            if (targetInfo != null) {
                try {
                    manager.openDevice(targetInfo, { openedDevice ->
                        if (openedDevice == null) {
                            Log.e(TAG, "MidiManager.openDevice returned null for ${device.name}")
                            _lastErrorCode.value = MidiErrorCodes.MIDI_DEVICE_OPEN_FAILED
                            _lastErrorMessage.value = "Không thể mở thiết bị MIDI hệ thống/USB."
                            _connectionState.value = DeviceConnectionState.ERROR
                            return@openDevice
                        }
                        attachToMidiDevice(openedDevice, device)
                    }, mainHandler)
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening MIDI device", e)
                    _lastErrorCode.value = MidiErrorCodes.MIDI_DEVICE_OPEN_FAILED
                    _lastErrorMessage.value = "Lỗi khi mở thiết bị MIDI: ${e.localizedMessage}"
                    _connectionState.value = DeviceConnectionState.ERROR
                }
            } else {
                Log.e(TAG, "Device target not found for id: ${device.id}")
                _lastErrorCode.value = MidiErrorCodes.MIDI_DEVICE_OPEN_FAILED
                _lastErrorMessage.value = "Không tìm thấy thiết bị MIDI trong danh sách hệ thống."
                _connectionState.value = DeviceConnectionState.ERROR
            }
        }
    }

    private fun attachToMidiDevice(openedDevice: MidiDevice, targetDevice: PianoDevice) {
        val portInfoList = openedDevice.info.ports
        // Rule: Only select ports with TYPE_OUTPUT because piano sends MIDI out into app
        val outputPorts = portInfoList.filter { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }

        if (outputPorts.isEmpty()) {
            Log.e(TAG, "No TYPE_OUTPUT ports found on device ${targetDevice.name}")
            try {
                openedDevice.close()
            } catch (e: Exception) { }
            _lastErrorCode.value = MidiErrorCodes.MIDI_OUTPUT_PORT_NOT_FOUND
            _lastErrorMessage.value = "Đàn không có cổng xuất tín hiệu MIDI (TYPE_OUTPUT)."
            _connectionState.value = DeviceConnectionState.ERROR
            return
        }

        var successfullyOpenedPort: MidiOutputPort? = null
        var openedPortNumber = -1

        // Sequentially try opening each output port until one succeeds
        for (portInfo in outputPorts) {
            try {
                val port = openedDevice.openOutputPort(portInfo.portNumber)
                if (port != null) {
                    successfullyOpenedPort = port
                    openedPortNumber = portInfo.portNumber
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open output port ${portInfo.portNumber}, trying next", e)
            }
        }

        if (successfullyOpenedPort != null) {
            activeMidiDevice = openedDevice
            activeDeviceInfo = openedDevice.info
            activeOutputPort = successfullyOpenedPort

            parser.reset()
            val inputSource = if (targetDevice.type == DeviceType.BLUETOOTH_MIDI) {
                MidiInputSource.BLUETOOTH_LE
            } else {
                MidiInputSource.USB
            }
            parser.updateContext(inputSource, targetDevice.id)

            sessionStartMonotonicMs = SystemClock.elapsedRealtime()
            successfullyOpenedPort.connect(midiReceiver)

            val connectedDev = targetDevice.copy(
                isConnected = true,
                isConnecting = false,
                activePortIndex = openedPortNumber,
                portCount = portInfoList.size
            )
            _connectedDevice.value = connectedDev
            _connectionState.value = DeviceConnectionState.CONNECTED
            _lastErrorCode.value = null
            _lastErrorMessage.value = null
            refreshDevices()
            Log.i(TAG, "Successfully connected to MIDI device ${targetDevice.name} on output port $openedPortNumber")
        } else {
            try {
                openedDevice.close()
            } catch (e: Exception) { }
            Log.e(TAG, "Failed to open any output ports on device ${targetDevice.name}")
            _lastErrorCode.value = MidiErrorCodes.MIDI_PORT_OPEN_FAILED
            _lastErrorMessage.value = "Không thể mở cổng nhận tín hiệu MIDI từ đàn. Hãy rút cáp/tắt bật lại Bluetooth rồi thử lại."
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
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            val nowMs = SystemClock.elapsedRealtime()
            val relativeMs = nowMs - sessionStartMonotonicMs
            val parsedEvents = parser.parse(msg, offset, count, nowMs)

            val rawSub = msg.copyOfRange(offset.coerceIn(0, msg.size), (offset + count).coerceIn(0, msg.size))
            val hexString = rawSub.joinToString(" ") { "%02X".format(it) }

            for (event in parsedEvents) {
                when (event) {
                    is ParsedMidiEvent.Note -> {
                        val ne = event.noteEvent
                        scope.launch {
                            _noteEvents.emit(ne)
                            _lastDiagnosticEvent.value = ne
                        }

                        val desc = if (ne.isNoteOn) "Note On [${ne.note}] vel=${ne.velocity}" else "Note Off [${ne.note}]"
                        addRawLog(
                            MidiRawLogEntry(
                                timestampMs = System.currentTimeMillis(),
                                relativeTimeMs = relativeMs,
                                description = desc,
                                rawHexBytes = hexString,
                                channel = ne.channel,
                                noteOrCc = ne.note,
                                valueOrVelocity = ne.velocity
                            )
                        )
                    }
                    is ParsedMidiEvent.Control -> {
                        val ce = event.controlEvent
                        scope.launch {
                            _controlEvents.emit(ce)
                        }

                        val desc = if (ce.controllerNumber == 64) {
                            if (ce.value >= 64) "Sustain Pedal DOWN (CC64=${ce.value})" else "Sustain Pedal UP (CC64=${ce.value})"
                        } else {
                            "Control Change (CC${ce.controllerNumber}=${ce.value})"
                        }

                        addRawLog(
                            MidiRawLogEntry(
                                timestampMs = System.currentTimeMillis(),
                                relativeTimeMs = relativeMs,
                                description = desc,
                                rawHexBytes = hexString,
                                channel = ce.channel,
                                noteOrCc = ce.controllerNumber,
                                valueOrVelocity = ce.value
                            )
                        )
                    }
                }
            }
        }
    }

    private fun addRawLog(entry: MidiRawLogEntry) {
        val current = _rawLogRingBuffer.value
        val updated = (listOf(entry) + current).take(300)
        _rawLogRingBuffer.value = updated
    }

    fun clearRawLogs() {
        _rawLogRingBuffer.value = emptyList()
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
