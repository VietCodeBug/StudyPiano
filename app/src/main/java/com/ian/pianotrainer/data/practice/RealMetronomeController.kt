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

    // Pre-synthesized acoustic wooden metronome clicks (16-bit PCM mono 44100Hz)
    private val sampleRate = 44100
    private val highClickPcm: ShortArray by lazy { generateWoodMetronomeClick(fundamentalHz = 520.0, isDownbeat = true) }
    private val lowClickPcm: ShortArray by lazy { generateWoodMetronomeClick(fundamentalHz = 380.0, isDownbeat = false) }

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

    /**
     * Synthesizes an authentic acoustic wooden mechanical metronome (Wittner Maelzel) sound
     * with warm wood body resonance, hollow chamber acoustic damping, and no harsh electronic beep.
     */
    private fun generateWoodMetronomeClick(fundamentalHz: Double, isDownbeat: Boolean): ShortArray {
        val durationMs = if (isDownbeat) 28 else 20
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val samples = ShortArray(numSamples)
        val attackSamples = (sampleRate * 0.0015).toInt() // 1.5ms soft transient

        val twoPi = 2.0 * Math.PI
        val decayRate = if (isDownbeat) 120.0 else 160.0
        val gain = if (isDownbeat) 11000.0 else 8500.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate

            // Natural wooden box resonances: fundamental + hollow sub + wooden shell overtone
            val s1 = sin(twoPi * fundamentalHz * t)
            val s2 = 0.35 * sin(twoPi * (fundamentalHz * 2.1) * t)
            val s3 = 0.20 * sin(twoPi * (fundamentalHz * 0.52) * t)

            val woodBody = s1 + s2 + s3
            val decay = kotlin.math.exp(-decayRate * t)

            // Attack envelope
            val attackEnv = if (i < attackSamples) (i.toDouble() / attackSamples) else 1.0

            val sampleVal = (woodBody * decay * attackEnv * gain).toInt().coerceIn(-32767, 32767)
            samples[i] = sampleVal.toShort()
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
