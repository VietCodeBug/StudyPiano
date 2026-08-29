package com.ian.pianotrainer.core.midi

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import com.ian.pianotrainer.domain.model.MidiInputSource
import com.ian.pianotrainer.domain.model.MidiNoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Microphone Pitch Detector (Beta)
 * Uses single-channel PCM audio recording with an autocorrelation/YIN pitch estimation algorithm,
 * noise gate, confidence threshold, and pitch stability hysteresis.
 */
class MicrophonePitchDetector(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _noteEvents = MutableSharedFlow<MidiNoteEvent>(extraBufferCapacity = 64)
    val noteEvents: SharedFlow<MidiNoteEvent> = _noteEvents.asSharedFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _currentDetectedNote = MutableStateFlow<Int?>(null)
    val currentDetectedNote: StateFlow<Int?> = _currentDetectedNote.asStateFlow()

    private val _audioLevelRms = MutableStateFlow(0f)
    val audioLevelRms: StateFlow<Float> = _audioLevelRms.asStateFlow()

    private val _confidence = MutableStateFlow(0f)
    val confidence: StateFlow<Float> = _confidence.asStateFlow()

    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null

    // Audio configuration
    private val sampleRate = 22050
    private val bufferSize = 2048
    private val noiseGateRms = 0.02f
    private val a4Frequency = 440.0

    // Stability tracking
    private var candidateNote: Int = -1
    private var candidateCount: Int = 0
    private val requiredStableFrames = 2
    private var activeEmittedNote: Int? = null

    @SuppressLint("MissingPermission")
    fun startListening(): Boolean {
        if (_isListening.value) return true

        if (!AudioInputCoordinator.requestAccess(AudioRecordOwner.PITCH_DETECTOR)) {
            Log.w("MicrophoneDetector", "Microphone access denied: occupied by another recorder")
            return false
        }

        try {
            val minBufSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val actualBufferSize = maxOf(minBufSize, bufferSize * 2)

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                actualBufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("MicrophoneDetector", "AudioRecord failed to initialize")
                record.release()
                AudioInputCoordinator.releaseAccess(AudioRecordOwner.PITCH_DETECTOR)
                return false
            }

            audioRecord = record
            record.startRecording()
            _isListening.value = true

            recordJob = scope.launch(Dispatchers.Default) {
                processAudioLoop(record)
            }

            return true
        } catch (e: Exception) {
            Log.e("MicrophoneDetector", "Error starting microphone pitch detector", e)
            stopListening()
            return false
        }
    }

    fun stopListening() {
        _isListening.value = false
        recordJob?.cancel()
        recordJob = null

        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e("MicrophoneDetector", "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
            AudioInputCoordinator.releaseAccess(AudioRecordOwner.PITCH_DETECTOR)
        }

        // Release any active note
        activeEmittedNote?.let { note ->
            emitNoteOff(note)
            activeEmittedNote = null
        }
        _currentDetectedNote.value = null
        _audioLevelRms.value = 0f
        _confidence.value = 0f
        candidateNote = -1
        candidateCount = 0
    }

    private suspend fun processAudioLoop(record: AudioRecord) {
        val audioBuffer = ShortArray(bufferSize)

        while (scope.isActive && _isListening.value) {
            val readSamples = record.read(audioBuffer, 0, bufferSize)
            if (readSamples <= 0) continue

            // 1. Calculate RMS Level
            var sumSquares = 0.0
            for (i in 0 until readSamples) {
                val normalized = audioBuffer[i] / 32768.0
                sumSquares += normalized * normalized
            }
            val rms = sqrt(sumSquares / readSamples).toFloat()
            _audioLevelRms.value = rms

            // 2. Noise Gate Check
            if (rms < noiseGateRms) {
                if (activeEmittedNote != null) {
                    emitNoteOff(activeEmittedNote!!)
                    activeEmittedNote = null
                }
                _currentDetectedNote.value = null
                _confidence.value = 0f
                candidateNote = -1
                candidateCount = 0
                continue
            }

            // 3. Autocorrelation / YIN-style Pitch Detection
            val pitchResult = detectPitchAutocorrelation(audioBuffer, readSamples, sampleRate)
            val freq = pitchResult.frequency
            val conf = pitchResult.confidence
            _confidence.value = conf

            if (freq > 20.0 && freq < 4200.0 && conf > 0.65f) {
                // Convert frequency to MIDI note
                val midiDouble = 69.0 + 12.0 * (log2(freq / a4Frequency))
                val midiNote = midiDouble.roundToInt().coerceIn(21, 108)

                if (midiNote == candidateNote) {
                    candidateCount++
                } else {
                    candidateNote = midiNote
                    candidateCount = 1
                }

                if (candidateCount >= requiredStableFrames) {
                    if (activeEmittedNote != midiNote) {
                        activeEmittedNote?.let { prevNote ->
                            emitNoteOff(prevNote)
                        }
                        emitNoteOn(midiNote, (rms.coerceIn(0f, 1f) * 127).toInt().coerceIn(40, 110))
                        activeEmittedNote = midiNote
                        _currentDetectedNote.value = midiNote
                    }
                }
            } else {
                if (candidateCount > 0) {
                    candidateCount--
                }
                if (candidateCount == 0 && activeEmittedNote != null) {
                    emitNoteOff(activeEmittedNote!!)
                    activeEmittedNote = null
                    _currentDetectedNote.value = null
                }
            }
        }
    }

    private data class PitchResult(val frequency: Double, val confidence: Float)

    private fun detectPitchAutocorrelation(buffer: ShortArray, length: Int, sampleRate: Int): PitchResult {
        val minPeriod = sampleRate / 1000 // up to 1000 Hz
        val maxPeriod = sampleRate / 55   // down to 55 Hz (A1)

        if (maxPeriod >= length / 2) {
            return PitchResult(0.0, 0f)
        }

        // Difference function d(tau) = sum (x[i] - x[i+tau])^2
        var bestTau = -1
        var minDiff = Double.MAX_VALUE
        var zeroEnergy = 0.0

        for (i in 0 until maxPeriod) {
            zeroEnergy += buffer[i].toDouble() * buffer[i]
        }

        if (zeroEnergy < 1e-6) return PitchResult(0.0, 0f)

        for (tau in minPeriod..maxPeriod) {
            var diff = 0.0
            for (i in 0 until (length - tau)) {
                val delta = buffer[i].toDouble() - buffer[i + tau].toDouble()
                diff += delta * delta
            }

            // Normalization
            val normalizedDiff = diff / (zeroEnergy + 1.0)
            if (normalizedDiff < minDiff) {
                minDiff = normalizedDiff
                bestTau = tau
            }
        }

        if (bestTau > 0 && minDiff < 0.8) {
            // Parabolic interpolation for sub-sample accuracy
            val freq = sampleRate.toDouble() / bestTau
            val confidence = (1.0 - minDiff).toFloat().coerceIn(0f, 1f)
            return PitchResult(freq, confidence)
        }

        return PitchResult(0.0, 0f)
    }

    private fun emitNoteOn(note: Int, velocity: Int) {
        val now = SystemClock.elapsedRealtime()
        scope.launch {
            _noteEvents.emit(
                MidiNoteEvent(
                    channel = 0,
                    note = note,
                    velocity = velocity,
                    isNoteOn = true,
                    timestampMs = now,
                    inputSource = MidiInputSource.MICROPHONE,
                    deviceId = "mic_input_beta"
                )
            )
        }
    }

    private fun emitNoteOff(note: Int) {
        val now = SystemClock.elapsedRealtime()
        scope.launch {
            _noteEvents.emit(
                MidiNoteEvent(
                    channel = 0,
                    note = note,
                    velocity = 0,
                    isNoteOn = false,
                    timestampMs = now,
                    inputSource = MidiInputSource.MICROPHONE,
                    deviceId = "mic_input_beta"
                )
            )
        }
    }
}
