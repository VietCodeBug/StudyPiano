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

            // No synthesized fallback: silence is safer than misrepresenting generated tones as piano.
            val loadedCount = loadBundledSamples(pool)

            isPrepared = true
            _state.value = _state.value.copy(
                isReady = loadedCount > 0,
                loadedSampleCount = loadedCount
            )
            Log.i(TAG, "PianoAudioEngine prepared with $loadedCount anchor samples.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SoundPoolPianoEngine", e)
            _state.value = _state.value.copy(isReady = false)
        }
    }

    private fun loadBundledSamples(pool: SoundPool): Int {
        var count = 0
        for (anchorMidi in ANCHOR_MIDI_NOTES) {
            val assetName = "piano_samples/piano_${anchorMidi}_medium.ogg"
            try {
                context.assets.openFd(assetName).use { afd ->
                    val soundId = pool.load(afd, 1)
                    if (soundId > 0) {
                        sampleSoundIds[anchorMidi] = soundId
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
