package com.ian.pianotrainer.data.practice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.SoundPool
import android.media.AudioTrack
import android.media.MediaMetadataRetriever
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.os.SystemClock
import android.util.Log
import com.ian.pianotrainer.domain.service.MetronomeController
import com.ian.pianotrainer.domain.service.MetronomeSound
import java.io.File
import java.io.InputStream
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
    private val context: Context? = null,
    private val isAudioEnabled: Boolean = true
) : MetronomeController {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var metronomeJob: Job? = null
    private var previewJob: Job? = null
    private var timelineMode = false

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentBeat = MutableStateFlow(1)
    override val currentBeat: StateFlow<Int> = _currentBeat.asStateFlow()

    private val preferences by lazy { context?.getSharedPreferences("metronome_audio", Context.MODE_PRIVATE) }
    private val _bpm = MutableStateFlow(preferences?.getInt("bpm", 60)?.coerceIn(30, 240) ?: 60)
    override val bpm: StateFlow<Int> = _bpm.asStateFlow()
    private var standaloneBpm = _bpm.value

    private val _beatsPerBar = MutableStateFlow(preferences?.getInt("beats_per_bar", 4)?.takeIf { it in setOf(2, 3, 4, 6) } ?: 4)
    override val beatsPerBar: StateFlow<Int> = _beatsPerBar.asStateFlow()
    private val _accentEnabled = MutableStateFlow(preferences?.getBoolean("accent", true) ?: true)
    override val accentEnabled: StateFlow<Boolean> = _accentEnabled.asStateFlow()
    private val _volume = MutableStateFlow(preferences?.getFloat("volume", 0.8f)?.coerceIn(0f, 1f) ?: 0.8f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private val sampleRate = 44100
    private var selectedSound = runCatching {
        MetronomeSound.valueOf(preferences?.getString("sound", null) ?: MetronomeSound.MECHANICAL.name)
    }.getOrDefault(MetronomeSound.MECHANICAL)
    private val presetPcm: Map<MetronomeSound, Pair<ShortArray, ShortArray>> by lazy {
        MetronomeSound.builtIns.associateWith { sound ->
            generateClick(sound, true) to generateClick(sound, false)
        }
    }

    private var audioTrack: AudioTrack? = null
    private var customSoundId = 0
    private var previewCustomWhenLoaded = false
    private var customSoundName: String? = preferences?.getString("custom_name", null)
    private val soundPool: SoundPool? by lazy {
        if (context == null) null else SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
            .also { pool ->
                pool.setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0 && sampleId == customSoundId && previewCustomWhenLoaded) {
                        previewCustomWhenLoaded = false
                        scope.launch { playBeat(isDownbeat = true) }
                    }
                }
            }
    }
    private val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val focusRequest by lazy {
        if (android.os.Build.VERSION.SDK_INT >= 26) AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { change -> if (change < 0) stop() }
            .build() else null
    }

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
            presetPcm.size
            loadSavedCustomSound()
        } catch (e: Exception) {
            Log.e("RealMetronomeController", "Could not initialize AudioTrack", e)
        }
    }

    private fun generateClick(sound: MetronomeSound, isDownbeat: Boolean): ShortArray {
        val durationMs = when (sound) {
            MetronomeSound.WOOD -> if (isDownbeat) 42 else 32
            MetronomeSound.MECHANICAL -> if (isDownbeat) 28 else 20
            MetronomeSound.SOFT -> if (isDownbeat) 36 else 26
            MetronomeSound.DIGITAL -> if (isDownbeat) 22 else 16
            MetronomeSound.CUSTOM -> 20
        }
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val samples = ShortArray(numSamples)
        var randomState = if (isDownbeat) 0x51A7C3 else 0x2C91ED
        var previousNoise = 0.0
        for (i in 0 until numSamples) {
            randomState = randomState xor (randomState shl 13)
            randomState = randomState xor (randomState ushr 17)
            randomState = randomState xor (randomState shl 5)
            val white = ((randomState and 0xFFFF) / 32767.5) - 1.0
            val highPassed = white - previousNoise * 0.68
            previousNoise = white
            val normalized = i.toDouble() / numSamples
            val envelope = (1.0 - normalized) * (1.0 - normalized)
            val seconds = i.toDouble() / sampleRate
            val value = when (sound) {
                MetronomeSound.WOOD -> {
                    val frequency = if (isDownbeat) 720.0 else 540.0
                    (sin(2.0 * Math.PI * frequency * seconds) * 0.72 + highPassed * 0.18) * envelope
                }
                MetronomeSound.MECHANICAL -> (highPassed * 0.7 + if (i < 5) 0.3 else 0.0) * envelope
                MetronomeSound.SOFT -> highPassed * envelope * 0.32
                MetronomeSound.DIGITAL -> {
                    val frequency = if (isDownbeat) 1760.0 else 1320.0
                    sin(2.0 * Math.PI * frequency * seconds) * envelope * 0.48
                }
                MetronomeSound.CUSTOM -> 0.0
            }
            val gain = if (isDownbeat) 15500.0 else 12000.0
            samples[i] = (value * gain)
                .toInt().coerceIn(-32767, 32767).toShort()
        }
        if (samples.isNotEmpty()) { samples[0] = 0; samples[samples.lastIndex] = 0 }
        return samples
    }
    override fun start(bpm: Int) {
        val wasTimeline = timelineMode
        timelineMode = false
        if (wasTimeline) _isRunning.value = false
        setBpm(bpm)
        if (_isRunning.value) return
        val focusGranted = if (android.os.Build.VERSION.SDK_INT >= 26) {
            focusRequest == null || audioManager?.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else true
        if (!focusGranted) return
        _isRunning.value = true
        metronomeJob?.cancel()

        metronomeJob = scope.launch {
            var beat = 1
            var nextBeatMonotonicMs = SystemClock.elapsedRealtime()

            while (isActive && _isRunning.value) {
                _currentBeat.value = beat

                if (isAudioEnabled) playBeat(isDownbeat = beat == 1 && _accentEnabled.value)

                val now = SystemClock.elapsedRealtime()
                val deadline = MetronomeTiming.nextDeadline(nextBeatMonotonicMs, now, _bpm.value)
                nextBeatMonotonicMs = deadline.nextDeadlineMs
                repeat(deadline.skippedBeats) { beat = if (beat >= _beatsPerBar.value) 1 else beat + 1 }
                val sleepTime = nextBeatMonotonicMs - now
                delay(sleepTime)
                beat = if (beat >= _beatsPerBar.value) 1 else beat + 1
            }
        }
    }

    override fun startTimeline() {
        metronomeJob?.cancel()
        metronomeJob = null
        _isRunning.value = false
        timelineMode = true
        if (!requestAudioFocus()) return
        _isRunning.value = true
    }

    override fun resetTimeline(beat: Int, bpm: Int) {
        if (!timelineMode) return
        _currentBeat.value = beat.coerceIn(1, _beatsPerBar.value)
        _bpm.value = bpm.coerceIn(30, 240)
    }

    override fun playTimelineBeat(beat: Int, bpm: Int, isMeasureStart: Boolean) {
        if (!timelineMode || !_isRunning.value) return
        _currentBeat.value = beat.coerceIn(1, _beatsPerBar.value)
        _bpm.value = bpm.coerceIn(30, 240)
        if (isAudioEnabled) playBeat(isDownbeat = isMeasureStart && _accentEnabled.value)
    }

    override fun stop() {
        val wasTimeline = timelineMode
        _isRunning.value = false
        timelineMode = false
        metronomeJob?.cancel()
        metronomeJob = null
        _currentBeat.value = 1
        if (wasTimeline) _bpm.value = standaloneBpm
        if (android.os.Build.VERSION.SDK_INT >= 26) focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
    }

    override fun setBpm(bpm: Int) {
        _bpm.value = bpm.coerceIn(30, 240)
        standaloneBpm = _bpm.value
        preferences?.edit()?.putInt("bpm", _bpm.value)?.apply()
    }

    override fun setBeatsPerBar(beats: Int) {
        if (beats !in setOf(2, 3, 4, 6)) return
        _beatsPerBar.value = beats
        _currentBeat.value = _currentBeat.value.coerceAtMost(beats)
        preferences?.edit()?.putInt("beats_per_bar", beats)?.apply()
    }

    override fun setAccentEnabled(enabled: Boolean) {
        _accentEnabled.value = enabled
        preferences?.edit()?.putBoolean("accent", enabled)?.apply()
    }

    override fun getSound(): MetronomeSound = selectedSound

    override fun getCustomSoundName(): String? = customSoundName

    override fun setSound(sound: MetronomeSound) {
        selectedSound = if (sound == MetronomeSound.CUSTOM && customSoundId == 0) {
            MetronomeSound.MECHANICAL
        } else sound
        preferences?.edit()?.putString("sound", selectedSound.name)?.apply()
    }

    override fun setVolume(volume: Float) {
        _volume.value = volume.coerceIn(0f, 1f)
        audioTrack?.setVolume(_volume.value)
        preferences?.edit()?.putFloat("volume", _volume.value)?.apply()
    }

    override fun preview() {
        if (!isAudioEnabled) return
        previewJob?.cancel()
        previewJob = scope.launch { if (!_isRunning.value) playBeat(isDownbeat = true) }
    }

    override suspend fun importCustomSound(input: InputStream, fileName: String): Result<Unit> = runCatching {
        val appContext = context ?: error("Không thể lưu âm thanh trên thiết bị này")
        val extension = fileName.substringAfterLast('.', "wav").lowercase()
        require(extension in setOf("wav", "mp3", "ogg")) { "Chỉ hỗ trợ WAV, MP3 hoặc OGG" }
        val directory = File(appContext.filesDir, "metronome").apply { mkdirs() }
        val target = File(directory, "incoming_click.$extension")
        target.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= 2L * 1024L * 1024L) { "Âm metronome phải nhỏ hơn 2 MB" }
                output.write(buffer, 0, count)
            }
            require(total > 0L) { "Tệp âm thanh bị rỗng" }
        }
        val durationMs = MediaMetadataRetriever().let { retriever ->
            try {
                retriever.setDataSource(target.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally {
                retriever.release()
            }
        }
        require(durationMs in 1L..2000L) { "Hãy chọn tiếng click ngắn hơn 2 giây" }
        directory.listFiles()?.filter { it.name.startsWith("custom_click.") }?.forEach { it.delete() }
        val finalTarget = File(directory, "custom_click.$extension")
        require(target.renameTo(finalTarget)) { "Không thể lưu tệp âm thanh" }
        customSoundId.takeIf { it != 0 }?.let { soundPool?.unload(it) }
        previewCustomWhenLoaded = true
        customSoundId = soundPool?.load(finalTarget.absolutePath, 1) ?: 0
        require(customSoundId != 0) { "Thiết bị không đọc được tệp âm thanh này" }
        customSoundName = fileName
        selectedSound = MetronomeSound.CUSTOM
        preferences?.edit()
            ?.putString("custom_path", finalTarget.absolutePath)
            ?.putString("custom_name", fileName)
            ?.putString("sound", selectedSound.name)
            ?.apply()
    }

    @Synchronized
    private fun playBeat(isDownbeat: Boolean) {
        try {
            if (selectedSound == MetronomeSound.CUSTOM && customSoundId != 0) {
                val beatVolume = if (isDownbeat) _volume.value else _volume.value * 0.72f
                soundPool?.play(customSoundId, beatVolume, beatVolume, 1, 0, 1f)
            } else {
                val pair = presetPcm[selectedSound] ?: presetPcm.getValue(MetronomeSound.WOOD)
                val pcm = if (isDownbeat) pair.first else pair.second
                audioTrack?.setVolume(_volume.value)
                var offset = 0
                while (offset < pcm.size) {
                    val written = audioTrack?.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
                        ?: AudioTrack.ERROR_INVALID_OPERATION
                    if (written <= 0) {
                        Log.e("RealMetronomeController", "AudioTrack.write failed: " + written)
                        break
                    }
                    offset += written
                }
            }
        } catch (error: Exception) {
            Log.e("RealMetronomeController", "Error playing metronome click", error)
        }
    }

    private fun requestAudioFocus(): Boolean = if (android.os.Build.VERSION.SDK_INT >= 26) {
        focusRequest == null || audioManager?.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    } else true

    private fun loadSavedCustomSound() {
        val path = preferences?.getString("custom_path", null) ?: return
        val file = File(path)
        if (file.isFile) customSoundId = soundPool?.load(file.absolutePath, 1) ?: 0
    }

    fun release() {
        stop()
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            soundPool?.release()
        } catch (e: Exception) {
            Log.e("RealMetronomeController", "Error releasing AudioTrack", e)
        }
    }
}
