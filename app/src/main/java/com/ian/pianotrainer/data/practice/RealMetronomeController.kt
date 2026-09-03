package com.ian.pianotrainer.data.practice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import com.ian.pianotrainer.domain.service.MetronomeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

class RealMetronomeController(
    private val isAudioEnabled: Boolean = true
) : MetronomeController {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var metronomeJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentBeat = MutableStateFlow(1)
    override val currentBeat: StateFlow<Int> = _currentBeat.asStateFlow()

    private val _bpm = MutableStateFlow(60)
    override val bpm: StateFlow<Int> = _bpm.asStateFlow()

    // Project-generated, non-melodic percussive clicks (16-bit PCM mono 44100Hz)
    private val sampleRate = 44100
    private val highClickPcm: ShortArray by lazy { generatePercussiveClick(isDownbeat = true) }
    private val lowClickPcm: ShortArray by lazy { generatePercussiveClick(isDownbeat = false) }

    private var audioTrack: AudioTrack? = null

    init {
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBufSize, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e("RealMetronomeController", "Could not initialize AudioTrack", e)
        }
    }

    /** Project-generated filtered-noise impulse; deliberately non-pitched. */
    private fun generatePercussiveClick(isDownbeat: Boolean): ShortArray {
        val durationMs = if (isDownbeat) 24 else 16
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val samples = ShortArray(numSamples)
        var randomState = if (isDownbeat) 0x51A7C3 else 0x2C91ED
        var previousNoise = 0.0
        val gain = if (isDownbeat) 12500.0 else 8500.0
        for (i in 0 until numSamples) {
            randomState = randomState xor (randomState shl 13)
            randomState = randomState xor (randomState ushr 17)
            randomState = randomState xor (randomState shl 5)
            val white = ((randomState and 0xFFFF) / 32767.5) - 1.0
            val highPassed = white - previousNoise * 0.72
            previousNoise = white
            val normalized = i.toDouble() / numSamples
            val envelope = (1.0 - normalized) * (1.0 - normalized) * (1.0 - normalized)
            val transient = if (i < 3) (3 - i) * 0.18 else 0.0
            samples[i] = ((highPassed * envelope + transient) * gain)
                .toInt().coerceIn(-32767, 32767).toShort()
        }
        return samples
    }
    override fun start(bpm: Int) {
        setBpm(bpm)
        _isRunning.value = true
        metronomeJob?.cancel()

        metronomeJob = scope.launch {
            var beat = 1
            var nextBeatMonotonicMs = SystemClock.elapsedRealtime()

            while (isActive && _isRunning.value) {
                _currentBeat.value = beat

                if (isAudioEnabled && audioTrack != null) {
                    val pcm = if (beat == 1) highClickPcm else lowClickPcm
                    try {
                        audioTrack?.write(pcm, 0, pcm.size, AudioTrack.WRITE_NON_BLOCKING)
                    } catch (e: Exception) {
                        Log.e("RealMetronomeController", "Error writing metronome click", e)
                    }
                }

                val currentBpm = _bpm.value.coerceIn(30, 240)
                val intervalMs = 60000L / currentBpm
                nextBeatMonotonicMs += intervalMs

                val now = SystemClock.elapsedRealtime()
                val sleepTime = (nextBeatMonotonicMs - now).coerceAtLeast(0L)
                delay(sleepTime)

                beat = if (beat >= 4) 1 else beat + 1
            }
        }
    }

    override fun stop() {
        _isRunning.value = false
        metronomeJob?.cancel()
        metronomeJob = null
        _currentBeat.value = 1
    }

    override fun setBpm(bpm: Int) {
        _bpm.value = bpm.coerceIn(30, 240)
    }

    fun release() {
        stop()
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e("RealMetronomeController", "Error releasing AudioTrack", e)
        }
    }
}
