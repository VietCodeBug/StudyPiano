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
import kotlin.math.pow

class SoundPoolPianoEngine(
    private val context: Context
) : PianoAudioEngine {

    companion object {
        private const val TAG = "SoundPoolPianoEngine"
        private const val MAX_POLYPHONY = 48

        // Anchor pitches across 88 keys: A0, C1, C2, C3, C4, C5, C6, C7, C8
        val ANCHOR_MIDI_NOTES = intArrayOf(21, 24, 36, 48, 60, 72, 84, 96, 108)
    }

    private val _state = MutableStateFlow(PianoAudioState())
    override val state: StateFlow<PianoAudioState> = _state.asStateFlow()
    private val _availability = MutableStateFlow<PianoAudioAvailability>(PianoAudioAvailability.Loading)
    override val availability: StateFlow<PianoAudioAvailability> = _availability.asStateFlow()

    private var soundPool: SoundPool? = null
    // anchorMidiNote -> soundId
    private val sampleSoundIds = mutableMapOf<Int, Int>()
    private data class VoiceKey(val midiNote: Int, val channel: Int)
    // FIFO voices preserve overlapping repeated pitches per channel/track.
    private val activeStreams = mutableMapOf<VoiceKey, ArrayDeque<Int>>()
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
            val pendingLoads = mutableSetOf<Int>()
            var successfulLoads = 0
            pool.setOnLoadCompleteListener { _, soundId, status ->
                if (pendingLoads.remove(soundId) && status == 0) successfulLoads++
                if (pendingLoads.isEmpty() && sampleSoundIds.isNotEmpty()) {
                    val ready = successfulLoads == sampleSoundIds.size
                    _state.value = _state.value.copy(isReady = ready, loadedSampleCount = successfulLoads)
                    _availability.value = if (ready) PianoAudioAvailability.Ready(successfulLoads)
                    else PianoAudioAvailability.Error("Không tải được đầy đủ bộ âm piano")
                }
            }

            // No synthesized fallback: silence is safer than misrepresenting generated tones as piano.
            val loadedCount = loadBundledSamples(pool, pendingLoads)

            isPrepared = true
            _state.value = _state.value.copy(
                isReady = false,
                loadedSampleCount = loadedCount
            )
            _availability.value = if (loadedCount == 0) PianoAudioAvailability.Unavailable(
                "Chưa cài bộ âm piano; chế độ này chỉ hiển thị nốt."
            ) else PianoAudioAvailability.Loading
            Log.i(TAG, "PianoAudioEngine prepared with $loadedCount pending samples.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SoundPoolPianoEngine", e)
            _state.value = _state.value.copy(isReady = false)
            _availability.value = PianoAudioAvailability.Error(e.message ?: "Không thể khởi tạo âm thanh piano")
        }
    }

    private fun loadBundledSamples(pool: SoundPool, pendingLoads: MutableSet<Int>): Int {
        var count = 0
        for (anchorMidi in ANCHOR_MIDI_NOTES) {
            val assetName = "piano_samples/piano_${anchorMidi}_medium.ogg"
            try {
                context.assets.openFd(assetName).use { afd ->
                    val soundId = pool.load(afd, 1)
                    if (soundId > 0) {
                        sampleSoundIds[anchorMidi] = soundId
                        pendingLoads.add(soundId)
                        count++
                    }
                }
            } catch (_: Exception) {
                // Missing licensed samples intentionally produce silence.
            }
        }
        return count
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

        val streamId = pool.play(soundId, volumeGain, volumeGain, 1, 0, rate)
        if (streamId > 0) {
            activeStreams.getOrPut(VoiceKey(midiNote, channel)) { ArrayDeque() }.addLast(streamId)
            _state.value = _state.value.copy(activeVoiceCount = activeStreams.values.sumOf { it.size })
        }
    }

    override fun noteOff(midiNote: Int, channel: Int) {
        val pool = soundPool ?: return
        val key = VoiceKey(midiNote, channel)
        val voices = activeStreams[key] ?: return
        val streamId = voices.removeFirstOrNull() ?: return
        if (voices.isEmpty()) activeStreams.remove(key)
        if (isSustainDown) sustainedStreamIds.add(streamId) else try { pool.stop(streamId) } catch (_: Exception) { }
        _state.value = _state.value.copy(activeVoiceCount = activeStreams.values.sumOf { it.size })
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
        for (voices in activeStreams.values) for (streamId in voices) {
            try { pool.stop(streamId) } catch (_: Exception) { }
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
        _availability.value = PianoAudioAvailability.Loading
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
