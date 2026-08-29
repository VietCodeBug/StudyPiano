package com.ian.pianotrainer.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

class SoundPoolPianoEngine(
    private val context: Context
) : PianoAudioEngine {

    companion object {
        private const val TAG = "SoundPoolPianoEngine"
        private const val MAX_POLYPHONY = 48
        private const val SAMPLE_RATE = 44100 // High-fidelity 44.1kHz audio

        // Anchor pitches across 88 keys: A0, C1, C2, C3, C4, C5, C6, C7, C8
        val ANCHOR_MIDI_NOTES = intArrayOf(21, 24, 36, 48, 60, 72, 84, 96, 108)
    }

    private val _state = MutableStateFlow(PianoAudioState())
    override val state: StateFlow<PianoAudioState> = _state.asStateFlow()

    private var soundPool: SoundPool? = null
    // anchorMidiNote -> soundId
    private val sampleSoundIds = mutableMapOf<Int, Int>()
    // midiNote -> active streamId
    private val activeStreams = mutableMapOf<Int, Int>()
    // Set of streamIds held by sustain pedal
    private val sustainedStreamIds = mutableSetOf<Int>()

    private var masterVolume = 1.0f
    private var isSustainDown = false
    private var isPrepared = false

    override suspend fun prepare() = withContext(Dispatchers.IO) {
        if (isPrepared) return@withContext

        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(MAX_POLYPHONY)
                .setAudioAttributes(audioAttributes)
                .build()

            soundPool = pool

            // Load samples from assets if available, or generate acoustic harmonic piano anchors
            val loadedCount = loadOrGenerateAnchors(pool)

            isPrepared = true
            _state.value = _state.value.copy(
                isReady = true,
                loadedSampleCount = loadedCount
            )
            Log.i(TAG, "PianoAudioEngine prepared with $loadedCount anchor samples.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SoundPoolPianoEngine", e)
            _state.value = _state.value.copy(isReady = false)
        }
    }

    private fun loadOrGenerateAnchors(pool: SoundPool): Int {
        var count = 0
        val sampleDir = File(context.cacheDir, "piano_anchors_v2").apply { mkdirs() }

        for (anchorMidi in ANCHOR_MIDI_NOTES) {
            val assetName = "piano_samples/piano_$anchorMidi.ogg"
            var loaded = false

            // 1. Try loading asset if present
            try {
                val afd = context.assets.openFd(assetName)
                val soundId = pool.load(afd, 1)
                if (soundId > 0) {
                    sampleSoundIds[anchorMidi] = soundId
                    count++
                    loaded = true
                }
            } catch (ignored: Exception) { }

            // 2. Otherwise generate / load cached high-fidelity acoustic wave file
            if (!loaded) {
                val targetFile = File(sampleDir, "piano_anchor_$anchorMidi.wav")
                if (!targetFile.exists() || targetFile.length() < 1000) {
                    generatePianoWaveFile(targetFile, anchorMidi)
                }
                if (targetFile.exists()) {
                    val soundId = pool.load(targetFile.absolutePath, 1)
                    if (soundId > 0) {
                        sampleSoundIds[anchorMidi] = soundId
                        count++
                    }
                }
            }
        }
        return count
    }

    /**
     * Synthesizes a warm acoustic grand piano waveform with string inharmonicity,
     * soft physical mallet attack, multi-partial exponential decay, and smooth envelope.
     */
    private fun generatePianoWaveFile(targetFile: File, midiNote: Int) {
        val f0 = 440.0 * 2.0.pow((midiNote - 69) / 12.0)
        // Duration: low notes ring longer (3.0s), high notes shorter (1.5s)
        val durationSec = when {
            midiNote < 48 -> 3.0
            midiNote < 72 -> 2.2
            else -> 1.5
        }
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ShortArray(totalSamples)

        // Harmonic weights and relative decay rates for acoustic piano string
        val partialWeights = doubleArrayOf(1.0, 0.55, 0.35, 0.20, 0.12, 0.06, 0.03)
        val partialDecayMult = doubleArrayOf(1.0, 1.4, 1.9, 2.6, 3.5, 4.8, 6.5)

        val inharmonicityB = 0.00012 // Natural string stiffness inharmonicity
        val twoPi = 2.0 * PI
        val baseDecay = when {
            midiNote < 48 -> 0.7
            midiNote < 72 -> 1.1
            else -> 1.8
        }

        val attackSamples = (SAMPLE_RATE * 0.006).toInt() // 6ms smooth attack to avoid pop

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var sample = 0.0

            // Additive string partials with inharmonicity
            for (p in partialWeights.indices) {
                val n = p + 1
                val fn = n * f0 * Math.sqrt(1.0 + inharmonicityB * n * n)
                if (fn < SAMPLE_RATE / 2.0) { // Nyquist safeguard
                    val partialDecay = exp(-t * baseDecay * partialDecayMult[p])
                    sample += partialWeights[p] * sin(twoPi * fn * t) * partialDecay
                }
            }

            // Soft hammer strike body warmth (warm low resonance, no harsh white noise)
            if (t < 0.025) {
                val bodyResonance = sin(twoPi * (f0 * 0.5) * t) * (1.0 - t / 0.025) * 0.18
                sample += bodyResonance
            }

            // Smooth attack ramp (first 6ms) to prevent audio clicks
            if (i < attackSamples) {
                val attackEnv = sin(0.5 * PI * (i.toDouble() / attackSamples))
                sample *= attackEnv
            }

            // Scale to 16-bit PCM (warm master gain)
            val clamped = (sample * 20000.0).coerceIn(-32767.0, 32767.0).roundToInt()
            buffer[i] = clamped.toShort()
        }

        // Write standard 16-bit mono WAV header + PCM data
        FileOutputStream(targetFile).use { fos ->
            val header = createWavHeader(totalSamples * 2, SAMPLE_RATE)
            fos.write(header)
            val byteBuffer = ByteBuffer.allocate(buffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in buffer) {
                byteBuffer.putShort(s)
            }
            fos.write(byteBuffer.array())
        }
    }

    private fun createWavHeader(dataSize: Int, sampleRate: Int): ByteArray {
        val totalSize = dataSize + 36
        val byteRate = sampleRate * 2 // 16-bit mono = 2 bytes per sample
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16) // Subchunk1Size for PCM
            putShort(1) // AudioFormat 1 = PCM
            putShort(1) // NumChannels 1 = Mono
            putInt(sampleRate)
            putInt(byteRate)
            putShort(2) // BlockAlign
            putShort(16) // BitsPerSample
            put("data".toByteArray())
            putInt(dataSize)
        }.array()
    }

    override fun noteOn(midiNote: Int, velocity: Int, channel: Int) {
        if (velocity <= 0) {
            noteOff(midiNote, channel)
            return
        }
        val pool = soundPool ?: return
        if (sampleSoundIds.isEmpty()) return

        // 1. Find the closest anchor note
        val anchor = findClosestAnchor(midiNote)
        val soundId = sampleSoundIds[anchor] ?: return

        // 2. Calculate pitch-shift rate: 2.0^((midiNote - anchor) / 12.0)
        val semitoneDiff = midiNote - anchor
        val rate = 2.0.pow(semitoneDiff / 12.0).toFloat().coerceIn(0.5f, 2.0f)

        // 3. Dynamic velocity curve (perceptual acoustic power curve)
        val normalizedVelocity = (velocity / 127.0).coerceIn(0.0, 1.0)
        val volumeGain = (normalizedVelocity.pow(1.35) * masterVolume).toFloat().coerceIn(0.01f, 1.0f)

        // Stop any previously playing instance of the same note to prevent phase clashing
        activeStreams[midiNote]?.let { prevStreamId ->
            try { pool.stop(prevStreamId) } catch (e: Exception) { }
        }

        // 4. Play through SoundPool with high priority
        val streamId = pool.play(soundId, volumeGain, volumeGain, 1, 0, rate)
        if (streamId > 0) {
            activeStreams[midiNote] = streamId
            _state.value = _state.value.copy(activeVoiceCount = activeStreams.size)
        }
    }

    override fun noteOff(midiNote: Int, channel: Int) {
        val pool = soundPool ?: return
        val streamId = activeStreams.remove(midiNote) ?: return

        if (isSustainDown) {
            // Keep sound ringing while sustain pedal is held
            sustainedStreamIds.add(streamId)
        } else {
            // Stop with short smooth decay
            try {
                pool.stop(streamId)
            } catch (e: Exception) { }
        }
        _state.value = _state.value.copy(activeVoiceCount = activeStreams.size)
    }

    override fun sustainPedal(isDown: Boolean) {
        isSustainDown = isDown
        _state.value = _state.value.copy(isSustainPedalDown = isDown)

        if (!isDown && sustainedStreamIds.isNotEmpty()) {
            val pool = soundPool ?: return
            // Stop all notes that were released while pedal was held
            for (streamId in sustainedStreamIds) {
                try {
                    pool.stop(streamId)
                } catch (e: Exception) { }
            }
            sustainedStreamIds.clear()
        }
    }

    override fun allNotesOff() {
        val pool = soundPool ?: return
        for ((_, streamId) in activeStreams) {
            try { pool.stop(streamId) } catch (e: Exception) { }
        }
        activeStreams.clear()

        for (streamId in sustainedStreamIds) {
            try { pool.stop(streamId) } catch (e: Exception) { }
        }
        sustainedStreamIds.clear()

        _state.value = _state.value.copy(activeVoiceCount = 0)
    }

    override fun setMasterVolume(volume: Float) {
        masterVolume = volume.coerceIn(0.0f, 1.0f)
        _state.value = _state.value.copy(masterVolume = masterVolume)
    }

    override suspend fun release() = withContext(Dispatchers.IO) {
        allNotesOff()
        soundPool?.release()
        soundPool = null
        sampleSoundIds.clear()
        isPrepared = false
        _state.value = PianoAudioState()
    }

    private fun findClosestAnchor(midiNote: Int): Int {
        var closest = ANCHOR_MIDI_NOTES[0]
        var minDistance = Int.MAX_VALUE
        for (anchor in ANCHOR_MIDI_NOTES) {
            val dist = kotlin.math.abs(midiNote - anchor)
            if (dist < minDistance) {
                minDistance = dist
                closest = anchor
            }
        }
        return closest
    }
}
