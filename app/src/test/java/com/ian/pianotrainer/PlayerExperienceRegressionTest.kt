package com.ian.pianotrainer

import com.ian.pianotrainer.core.audio.DefaultMidiPlaybackScheduler
import com.ian.pianotrainer.core.audio.PianoAudioEngine
import com.ian.pianotrainer.core.audio.PianoAudioState
import com.ian.pianotrainer.core.audio.PlaybackRole
import com.ian.pianotrainer.core.music.PracticeClock
import com.ian.pianotrainer.core.music.SectionSlicer
import com.ian.pianotrainer.data.practice.RealPracticeEngine
import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.ImportedSong
import com.ian.pianotrainer.domain.model.NoteResultType
import com.ian.pianotrainer.domain.model.PracticeConfiguration
import com.ian.pianotrainer.domain.model.PracticeMode
import com.ian.pianotrainer.domain.model.SongPlaybackData
import com.ian.pianotrainer.domain.model.SongTempoInfo
import com.ian.pianotrainer.domain.model.SongTimeSignature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerExperienceRegressionTest {

    private class TestAudioEngine : PianoAudioEngine {
        val activeNotes = mutableSetOf<Int>()
        val playedNotes = mutableListOf<Int>()
        var allNotesOffCalled = false

        private val _state = MutableStateFlow(PianoAudioState(isReady = true))
        override val state: StateFlow<PianoAudioState> = _state

        override suspend fun prepare() {}
        override fun noteOn(midiNote: Int, velocity: Int, channel: Int) {
            activeNotes.add(midiNote)
            playedNotes.add(midiNote)
        }
        override fun noteOff(midiNote: Int, channel: Int) {
            activeNotes.remove(midiNote)
        }
        override fun sustainPedal(isDown: Boolean) {}
        override fun setMasterVolume(volume: Float) {}
        override fun allNotesOff() {
            activeNotes.clear()
            allNotesOffCalled = true
        }
        override suspend fun release() {
            allNotesOff()
        }
    }

    private class TestClock(var timeMs: Long = 0L) : PracticeClock {
        override fun elapsedRealtime(): Long = timeMs
        override fun currentTimeMillis(): Long = timeMs
    }

    @Test
    fun testAccompanimentPlaysOtherHandInRhythmMode() {
        val audioEngine = TestAudioEngine()
        val scheduler = DefaultMidiPlaybackScheduler(audioEngine)

        val notes = listOf(
            // Right Hand Note at 1000ms
            ExerciseNote(midiNote = 64, noteName = "E4", startMs = 1000L, durationMs = 500L, hand = HandMode.RIGHT),
            // Left Hand Accompaniment Note at 1000ms
            ExerciseNote(midiNote = 48, noteName = "C3", startMs = 1000L, durationMs = 1000L, hand = HandMode.LEFT)
        )

        val playbackData = SongPlaybackData(
            song = ImportedSong(
                id = "song_1",
                displayName = "Test Song",
                originalFileName = "test.mid",
                defaultBpm = 60,
                notes = notes
            ),
            notes = notes,
            tracks = emptyList(),
            tempos = emptyList(),
            timeSignatures = emptyList()
        )

        scheduler.load(playbackData)

        // User is practicing RIGHT hand -> LEFT hand must be ACCOMPANIMENT
        scheduler.setHandRole(HandMode.RIGHT, PlaybackRole.PRACTICE)
        scheduler.setHandRole(HandMode.LEFT, PlaybackRole.ACCOMPANIMENT)
        scheduler.play(0L)

        // Tick past 1000ms
        scheduler.tick(1050L)

        // Left hand note (48) should have played, Right hand note (64) must NOT have played
        assertTrue(audioEngine.playedNotes.contains(48))
        assertFalse(audioEngine.playedNotes.contains(64))
    }

    @Test
    fun testVirtualKeySendsNoteOnAndNoteOff() {
        val audioEngine = TestAudioEngine()

        // Press Note 60
        audioEngine.noteOn(60, 80, 0)
        assertTrue(audioEngine.activeNotes.contains(60))

        // Release Note 60
        audioEngine.noteOff(60, 0)
        assertFalse(audioEngine.activeNotes.contains(60))
    }

    @Test
    fun testPauseSendsAllNotesOff() {
        val audioEngine = TestAudioEngine()
        val scheduler = DefaultMidiPlaybackScheduler(audioEngine)

        audioEngine.noteOn(60, 80, 0)
        audioEngine.noteOn(64, 80, 0)
        assertEquals(2, audioEngine.activeNotes.size)

        scheduler.pause()
        assertTrue(audioEngine.allNotesOffCalled)
        assertEquals(0, audioEngine.activeNotes.size)
    }

    @Test
    fun testSeekDoesNotPlayPastNotes() {
        val audioEngine = TestAudioEngine()
        val scheduler = DefaultMidiPlaybackScheduler(audioEngine)

        val notes = listOf(
            ExerciseNote(midiNote = 60, noteName = "C4", startMs = 500L, durationMs = 500L, hand = HandMode.LEFT),
            ExerciseNote(midiNote = 67, noteName = "G4", startMs = 2000L, durationMs = 500L, hand = HandMode.LEFT)
        )

        val playbackData = SongPlaybackData(
            song = ImportedSong(
                id = "song_2",
                displayName = "Seek Test",
                originalFileName = "seek.mid",
                defaultBpm = 60,
                notes = notes
            ),
            notes = notes,
            tracks = emptyList(),
            tempos = emptyList(),
            timeSignatures = emptyList()
        )

        scheduler.load(playbackData)
        scheduler.setHandRole(HandMode.LEFT, PlaybackRole.ACCOMPANIMENT)

        // Seek directly to 1500ms
        scheduler.seekTo(1500L)
        scheduler.play(1500L)
        scheduler.tick(1600L)

        // Note at 500ms should NOT be played
        assertFalse(audioEngine.playedNotes.contains(60))

        // Tick past 2000ms
        scheduler.tick(2100L)
        assertTrue(audioEngine.playedNotes.contains(67))
    }

    @Test
    fun testVisualFeedbackAppliesOnlyToTargetNoteEvent() {
        val clock = TestClock(1000L)
        val engine = RealPracticeEngine(clock)

        val notes = listOf(
            ExerciseNote(midiNote = 60, noteName = "C4", startMs = 1000L, durationMs = 500L, hand = HandMode.RIGHT),
            ExerciseNote(midiNote = 60, noteName = "C4", startMs = 5000L, durationMs = 500L, hand = HandMode.RIGHT)
        )

        val config = PracticeConfiguration(
            title = "Feedback Test",
            sourceId = "fb_1",
            sourceType = "SONG",
            notes = notes,
            practiceMode = PracticeMode.WAIT_FOR_NOTE,
            handMode = HandMode.RIGHT,
            bpm = 60
        )

        engine.startPractice(config)

        // Play first C4 at 1000ms
        engine.processPlayedNote(60, 80)
        val state = engine.state.value

        assertNotNull(state.activeFeedback)
        assertEquals(60, state.activeFeedback?.midiNote)
        assertEquals(1000L, state.activeFeedback?.startMs)
        assertEquals(NoteResultType.CORRECT, state.activeFeedback?.result)

        // Feedback target matches first note at 1000ms, but NOT second note at 5000ms
        val note1 = notes[0]
        val note2 = notes[1]

        val feedback = state.activeFeedback!!
        val isNote1Target = (feedback.midiNote == note1.midiNote && feedback.startMs == note1.startMs)
        val isNote2Target = (feedback.midiNote == note2.midiNote && feedback.startMs == note2.startMs)

        assertTrue(isNote1Target)
        assertFalse(isNote2Target)
    }

    @Test
    fun testSectionSlicerPreservesMeasuresAndChords() {
        val notes = listOf(
            ExerciseNote(midiNote = 60, noteName = "C4", startMs = 0L, durationMs = 1000L),
            ExerciseNote(midiNote = 64, noteName = "E4", startMs = 0L, durationMs = 1000L),
            ExerciseNote(midiNote = 67, noteName = "G4", startMs = 4000L, durationMs = 1000L),
            ExerciseNote(midiNote = 72, noteName = "C5", startMs = 8000L, durationMs = 1000L),
            ExerciseNote(midiNote = 76, noteName = "E5", startMs = 12000L, durationMs = 1000L)
        )

        val tempos = listOf(SongTempoInfo(0L, 0L, 1_000_000L, 60))
        val timeSignatures = listOf(SongTimeSignature(0L, 0L, 4, 4))

        val sections = SectionSlicer.sliceSong(
            songId = "song_slice_test",
            notes = notes,
            tempos = tempos,
            timeSignatures = timeSignatures,
            defaultBpm = 60
        )

        assertTrue(sections.isNotEmpty())
        sections.forEach { sec ->
            assertTrue(sec.endMs > sec.startMs)
            assertTrue((sec.startMeasure ?: 0) <= (sec.endMeasure ?: 0))
        }
    }
}
