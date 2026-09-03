package com.ian.pianotrainer

import com.ian.pianotrainer.core.audio.DefaultMidiPlaybackScheduler
import com.ian.pianotrainer.core.audio.PianoAudioEngine
import com.ian.pianotrainer.core.audio.PianoAudioState
import com.ian.pianotrainer.core.audio.PianoAudioAvailability
import com.ian.pianotrainer.core.audio.PlaybackRole
import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.ImportedSong
import com.ian.pianotrainer.domain.model.SongPlaybackData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MidiPlaybackSchedulerUnitTest {

    private class MockAudioEngine : PianoAudioEngine {
        val playedNotes = mutableListOf<Triple<Int, Int, Int>>() // note, velocity, channel
        val stoppedNotes = mutableListOf<Pair<Int, Int>>() // note, channel
        var isSustain = false
        var allNotesOffCount = 0

        override val state: StateFlow<PianoAudioState> = MutableStateFlow(PianoAudioState(isReady = true))
        override val availability: StateFlow<PianoAudioAvailability> = MutableStateFlow(PianoAudioAvailability.Ready(1))
        override suspend fun prepare() {}
        override fun noteOn(midiNote: Int, velocity: Int, channel: Int) {
            playedNotes.add(Triple(midiNote, velocity, channel))
        }
        override fun noteOff(midiNote: Int, channel: Int) {
            stoppedNotes.add(Pair(midiNote, channel))
        }
        override fun sustainPedal(isDown: Boolean) { isSustain = isDown }
        override fun allNotesOff() {
            allNotesOffCount++
            playedNotes.clear()
        }
        override fun setMasterVolume(volume: Float) {}
        override suspend fun release() {}
    }

    private lateinit var mockEngine: MockAudioEngine
    private lateinit var scheduler: DefaultMidiPlaybackScheduler

    private fun createSamplePlaybackData(): SongPlaybackData {
        val song = ImportedSong(id = "test_song", displayName = "Test Song", originalFileName = "test.mid")
        val notes = listOf(
            ExerciseNote(midiNote = 60, startMs = 0L, durationMs = 500L, hand = HandMode.RIGHT, trackIndex = 0),
            ExerciseNote(midiNote = 48, startMs = 0L, durationMs = 1000L, hand = HandMode.LEFT, trackIndex = 1),
            ExerciseNote(midiNote = 64, startMs = 500L, durationMs = 500L, hand = HandMode.RIGHT, trackIndex = 0),
            ExerciseNote(midiNote = 67, startMs = 1000L, durationMs = 500L, hand = HandMode.RIGHT, trackIndex = 0)
        )
        return SongPlaybackData(
            song = song,
            notes = notes,
            tracks = emptyList(),
            tempos = emptyList(),
            timeSignatures = emptyList()
        )
    }

    @Before
    fun setup() {
        mockEngine = MockAudioEngine()
        scheduler = DefaultMidiPlaybackScheduler(mockEngine)
    }

    @Test
    fun `demo mode plays all notes as timeline advances`() {
        val data = createSamplePlaybackData()
        scheduler.load(data)
        scheduler.setDemoMode(true)
        scheduler.play(0L)

        // At t = 0ms: Notes 60 and 48 should start
        scheduler.tick(0L)
        assertEquals(2, mockEngine.playedNotes.size)
        assertTrue(mockEngine.playedNotes.any { it.first == 60 })
        assertTrue(mockEngine.playedNotes.any { it.first == 48 })

        // At t = 500ms: Note 60 ends, Note 64 starts
        scheduler.tick(500L)
        assertTrue(mockEngine.stoppedNotes.any { it.first == 60 })
        assertTrue(mockEngine.playedNotes.any { it.first == 64 })

        // At t = 1000ms: Note 48 and Note 64 end, Note 67 starts
        scheduler.tick(1000L)
        assertTrue(mockEngine.stoppedNotes.any { it.first == 48 })
        assertTrue(mockEngine.stoppedNotes.any { it.first == 64 })
        assertTrue(mockEngine.playedNotes.any { it.first == 67 })
    }

    @Test
    fun `practice mode plays only accompaniment hand`() {
        val data = createSamplePlaybackData()
        scheduler.load(data)
        scheduler.setDemoMode(false)
        // User is practicing RIGHT hand -> LEFT hand is ACCOMPANIMENT
        scheduler.setHandRole(HandMode.RIGHT, PlaybackRole.PRACTICE)
        scheduler.setHandRole(HandMode.LEFT, PlaybackRole.ACCOMPANIMENT)
        scheduler.play(0L)

        // At t = 0ms: Only Left hand (note 48) should be played by app synth
        scheduler.tick(0L)
        assertEquals(1, mockEngine.playedNotes.size)
        assertEquals(48, mockEngine.playedNotes[0].first)

        // At t = 500ms: Right hand note 64 is PRACTICE -> should NOT be played
        scheduler.tick(500L)
        assertEquals(1, mockEngine.playedNotes.size) // No new note played
    }

    @Test
    fun `seek clears active notes and skips past notes`() {
        val data = createSamplePlaybackData()
        scheduler.load(data)
        scheduler.setDemoMode(true)
        scheduler.play(0L)

        val beforeCount = mockEngine.allNotesOffCount
        // Seek directly to 800ms
        scheduler.seekTo(800L)
        assertTrue(mockEngine.allNotesOffCount > beforeCount)

        // At t = 1000ms: Note 67 starts (earlier notes 60, 48, 64 were skipped)
        scheduler.tick(1000L)
        assertEquals(1, mockEngine.playedNotes.size)
        assertEquals(67, mockEngine.playedNotes[0].first)
    }

    @Test
    fun `pause stops audio output`() {
        val data = createSamplePlaybackData()
        scheduler.load(data)
        scheduler.setDemoMode(true)
        scheduler.play(0L)
        scheduler.tick(0L)
        assertEquals(2, mockEngine.playedNotes.size)

        val beforeCount = mockEngine.allNotesOffCount
        scheduler.pause()
        assertTrue(mockEngine.allNotesOffCount > beforeCount)
        assertEquals(0, mockEngine.playedNotes.size) // allNotesOff cleared active sounds

        // Tick while paused should not play new notes
        scheduler.tick(500L)
        assertEquals(0, mockEngine.playedNotes.size)
    }

    @Test
    fun `loop seek resets index and replays loop start without old notes`() {
        scheduler.load(createSamplePlaybackData())
        scheduler.setDemoMode(true)
        scheduler.play(500L)
        scheduler.tick(500L)
        assertEquals(listOf(64), mockEngine.playedNotes.map { it.first })
        scheduler.play(500L)
        scheduler.tick(500L)
        assertEquals(listOf(64), mockEngine.playedNotes.map { it.first })
    }

    @Test
    fun `repeated same pitch notes each receive note on and note off`() {
        val song = ImportedSong(id="repeat", displayName="Repeat", originalFileName="repeat.mid")
        val notes = listOf(
            ExerciseNote(60,startMs=0,durationMs=300,hand=HandMode.LEFT,trackIndex=1),
            ExerciseNote(60,startMs=100,durationMs=300,hand=HandMode.LEFT,trackIndex=1)
        )
        scheduler.load(SongPlaybackData(song,notes,emptyList(),emptyList(),emptyList()))
        scheduler.setDemoMode(true); scheduler.play(0); scheduler.tick(0); scheduler.tick(100); scheduler.tick(400)
        assertEquals(2, mockEngine.playedNotes.count { it.first == 60 })
        assertEquals(2, mockEngine.stoppedNotes.count { it.first == 60 })
    }}
