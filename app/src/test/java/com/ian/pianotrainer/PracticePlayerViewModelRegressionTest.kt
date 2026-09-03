package com.ian.pianotrainer

import com.ian.pianotrainer.core.audio.DefaultMidiPlaybackScheduler
import com.ian.pianotrainer.core.audio.PianoAudioEngine
import com.ian.pianotrainer.core.audio.PianoAudioState
import com.ian.pianotrainer.core.music.PracticeClock
import com.ian.pianotrainer.data.local.database.entity.SongNoteEntity
import com.ian.pianotrainer.data.local.database.entity.SongTrackEntity
import com.ian.pianotrainer.data.practice.RealPracticeEngine
import com.ian.pianotrainer.domain.model.Course
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.FingerExercise
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.ImportedSong
import com.ian.pianotrainer.domain.model.KeyboardRangeMode
import com.ian.pianotrainer.domain.model.Lesson
import com.ian.pianotrainer.domain.model.MidiControlEvent
import com.ian.pianotrainer.domain.model.MidiNoteEvent
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.domain.model.PracticeMode
import com.ian.pianotrainer.domain.model.PracticeNoteResult
import com.ian.pianotrainer.domain.model.PracticeSession
import com.ian.pianotrainer.domain.model.ProgressSummary
import com.ian.pianotrainer.domain.model.SongPlaybackData
import com.ian.pianotrainer.domain.model.SongPracticePreset
import com.ian.pianotrainer.domain.model.SongTempoInfo
import com.ian.pianotrainer.domain.model.SongTimeSignature
import com.ian.pianotrainer.domain.model.UserSettings
import com.ian.pianotrainer.domain.repository.CurriculumRepository
import com.ian.pianotrainer.domain.repository.ExerciseRepository
import com.ian.pianotrainer.domain.repository.ProgressRepository
import com.ian.pianotrainer.domain.repository.SettingsRepository
import com.ian.pianotrainer.domain.repository.SongRepository
import com.ian.pianotrainer.domain.service.MetronomeController
import com.ian.pianotrainer.domain.service.MidiInput
import com.ian.pianotrainer.feature.practice.PracticePlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
class PracticePlayerViewModelRegressionTest {

    private class FakeClock(var current: Long = 0L) : PracticeClock {
        override fun elapsedRealtime(): Long = current
        override fun currentTimeMillis(): Long = current
    }

    private class FakeMidiInput : MidiInput {
        private val _noteEvents = MutableSharedFlow<MidiNoteEvent>(extraBufferCapacity = 64)
        override val noteEvents: SharedFlow<MidiNoteEvent> = _noteEvents.asSharedFlow()

        private val _controlEvents = MutableSharedFlow<MidiControlEvent>(extraBufferCapacity = 64)
        override val controlEvents: SharedFlow<MidiControlEvent> = _controlEvents.asSharedFlow()

        override fun onVirtualKeyPressed(midiNote: Int, velocity: Int) {}
        override fun onVirtualKeyReleased(midiNote: Int) {}
    }

    private class FakeMetronome : MetronomeController {
        private val _currentBeat = MutableStateFlow(1)
        override val currentBeat: StateFlow<Int> = _currentBeat.asStateFlow()

        private val _isRunning = MutableStateFlow(false)
        override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _bpm = MutableStateFlow(60)
        override val bpm: StateFlow<Int> = _bpm.asStateFlow()

        override fun start(bpm: Int) { _isRunning.value = true; _bpm.value = bpm }
        override fun stop() { _isRunning.value = false }
        override fun setBpm(bpm: Int) { _bpm.value = bpm }
    }

    private class FakeAudioEngine : PianoAudioEngine {
        private val _state = MutableStateFlow(PianoAudioState(isReady = true))
        override val state: StateFlow<PianoAudioState> = _state.asStateFlow()

        private val _availability = MutableStateFlow<com.ian.pianotrainer.core.audio.PianoAudioAvailability>(
            com.ian.pianotrainer.core.audio.PianoAudioAvailability.Ready(12)
        )
        override val availability: StateFlow<com.ian.pianotrainer.core.audio.PianoAudioAvailability> = _availability.asStateFlow()

        var isSustainDown = false

        override suspend fun prepare() {}
        override fun noteOn(midiNote: Int, velocity: Int, channel: Int) {}
        override fun noteOff(midiNote: Int, channel: Int) {}
        override fun sustainPedal(isDown: Boolean) { isSustainDown = isDown }
        override fun allNotesOff() {}
        override fun setMasterVolume(volume: Float) {}
        override suspend fun release() {}
    }

    private class FakeSongRepository(val playbackData: SongPlaybackData) : SongRepository {
        override fun getAllSongs(): Flow<List<ImportedSong>> = flowOf(listOf(playbackData.song))
        override suspend fun getAllSongsList(): List<ImportedSong> = listOf(playbackData.song)
        override fun getFavoriteSongs(): Flow<List<ImportedSong>> = flowOf(emptyList())
        override suspend fun getSongById(id: String): ImportedSong? = if (id == playbackData.song.id) playbackData.song else null
        override suspend fun getSongPlaybackData(id: String): SongPlaybackData? = if (id == playbackData.song.id) playbackData else null
        override suspend fun getSongTracks(songId: String): List<SongTrackEntity> = emptyList()
        override suspend fun getSongNotes(songId: String): List<SongNoteEntity> = emptyList()
        override suspend fun getSongTimeSignatures(songId: String): List<SongTimeSignature> = emptyList()
        override suspend fun importMidiFile(inputStream: InputStream, originalFileName: String, fileSize: Long, customTitle: String?): Result<ImportedSong> = Result.success(playbackData.song)
        override suspend fun updateTrackConfigurations(songId: String, tracks: List<SongTrackEntity>) {}
        override suspend fun renameSong(id: String, newName: String) {}
        override suspend fun toggleFavorite(id: String) {}
        override suspend fun deleteSong(id: String) {}
        override suspend fun updateLastPracticed(id: String) {}
        override suspend fun seedCurriculumRepertoire(): Int = 0
        override fun getPracticePresets(songId: String): Flow<List<SongPracticePreset>> = flowOf(emptyList())
        override suspend fun getAllPresetsList(): List<SongPracticePreset> = emptyList()
        override suspend fun savePracticePreset(preset: SongPracticePreset) {}
        override suspend fun deletePracticePreset(id: String) {}
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val _settings = MutableStateFlow(UserSettings())
        override val userSettings: Flow<UserSettings> = _settings.asStateFlow()
        override suspend fun setNoteNamingMode(mode: NoteNamingMode) {}
        override suspend fun setDefaultDisplayMode(mode: DisplayMode) {}
        override suspend fun setDefaultBpm(bpm: Int) {}
        override suspend fun setVirtualPianoSoundEnabled(enabled: Boolean) {}
        override suspend fun setMetronomeVolume(volume: Float) {}
        override suspend fun setLastSelectedHandMode(handMode: HandMode) {}
        override suspend fun setLastKnownMidiDeviceName(name: String) {}
        override suspend fun setDailyGoalMinutes(minutes: Int) {}
        override suspend fun setCountInOption(option: String) {}
        override suspend fun setAutoReconnectMidi(enabled: Boolean) {}
        override suspend fun setKeyboardRangeMode(mode: KeyboardRangeMode) {}
        override suspend fun setDefaultLookAheadMs(lookAheadMs: Long) {}
        override suspend fun resetAllUserData() {}
        override suspend fun resetDemoData() {}
    }

    private class FakeCurriculumRepository : CurriculumRepository {
        override fun getCourses(): Flow<List<Course>> = flowOf(emptyList())
        override suspend fun getCourseById(courseId: String): Course? = null
        override suspend fun getLessonById(lessonId: String): Lesson? = null
        override suspend fun updateLessonProgress(lessonId: String, isCompleted: Boolean, accuracy: Float, bpm: Int) {}
    }

    private class FakeExerciseRepository : ExerciseRepository {
        override fun getFingerExercises(): Flow<List<FingerExercise>> = flowOf(emptyList())
        override suspend fun getExerciseById(id: String): FingerExercise? = null
    }

    private class FakeProgressRepository : ProgressRepository {
        override fun getProgressSummary(daysFilter: Int?): Flow<ProgressSummary> = flowOf(ProgressSummary())
        override fun getRecentSessions(limit: Int): Flow<List<PracticeSession>> = flowOf(emptyList())
        override fun getSessionNoteResults(sessionId: String): Flow<List<PracticeNoteResult>> = flowOf(emptyList())
        override suspend fun getAllSessionsList(): List<PracticeSession> = emptyList()
        override suspend fun getAllNoteResultsList(): List<PracticeNoteResult> = emptyList()
        override suspend fun getSessionById(sessionId: String): PracticeSession? = null
        override suspend fun savePracticeSession(session: PracticeSession, noteResults: List<PracticeNoteResult>) {}
        override suspend fun deletePracticeSession(sessionId: String) {}
        override suspend fun clearAllProgress() {}
    }

    private val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSectionSelectionPreservedAcrossSettingsAndRestartReturnsToSectionStart() = runTest(testDispatcher) {
        val clock = FakeClock(1000L)
        val practiceEngine = RealPracticeEngine(clock)
        val midiInput = FakeMidiInput()
        val metronome = FakeMetronome()
        val audioEngine = FakeAudioEngine()
        val scheduler = DefaultMidiPlaybackScheduler(audioEngine)

        val notes = listOf(
            // Section 1 (0..4000ms)
            ExerciseNote(midiNote = 60, noteName = "C4", startMs = 0L, durationMs = 500L, hand = HandMode.RIGHT),
            ExerciseNote(midiNote = 64, noteName = "E4", startMs = 2000L, durationMs = 500L, hand = HandMode.RIGHT),
            // Section 2 (4000..8000ms)
            ExerciseNote(midiNote = 67, noteName = "G4", startMs = 4000L, durationMs = 500L, hand = HandMode.RIGHT),
            ExerciseNote(midiNote = 72, noteName = "C5", startMs = 6000L, durationMs = 500L, hand = HandMode.LEFT),
            // Section 3 (8000..12000ms)
            ExerciseNote(midiNote = 76, noteName = "E5", startMs = 8000L, durationMs = 500L, hand = HandMode.RIGHT)
        )

        val playbackData = SongPlaybackData(
            song = ImportedSong(
                id = "song_reg_1",
                displayName = "Regression Test Song",
                originalFileName = "test.mid",
                defaultBpm = 60,
                notes = notes
            ),
            notes = notes,
            tracks = emptyList(),
            tempos = listOf(SongTempoInfo(0L, 0L, 1_000_000L, 60)),
            timeSignatures = listOf(SongTimeSignature(0L, 0L, 4, 4))
        )

        val songRepo = FakeSongRepository(playbackData)
        val curriculumRepo = FakeCurriculumRepository()
        val exerciseRepo = FakeExerciseRepository()
        val progressRepo = FakeProgressRepository()
        val settingsRepo = FakeSettingsRepository()

        val viewModel = PracticePlayerViewModel(
            title = "Regression Song",
            sourceType = "SONG",
            sourceId = "song_reg_1",
            initialHand = HandMode.RIGHT,
            initialBpm = 60,
            initialPracticeMode = PracticeMode.WAIT_FOR_NOTE,
            initialSectionId = null,
            practiceEngine = practiceEngine,
            midiInput = midiInput,
            metronomeController = metronome,
            curriculumRepository = curriculumRepo,
            exerciseRepository = exerciseRepo,
            songRepository = songRepo,
            progressRepository = progressRepo,
            settingsRepository = settingsRepo,
            pianoAudioEngine = audioEngine,
            midiPlaybackScheduler = scheduler,
            clock = clock
        )

        try {
            val state1 = viewModel.uiState.value
            assertTrue(state1.sections.isNotEmpty())

            // 1. Select Section 2
            val sec2 = state1.sections.getOrNull(1) ?: state1.sections[0]
            viewModel.selectSection(sec2)

            assertEquals(sec2.id, viewModel.uiState.value.selectedSection?.id)
            assertEquals(sec2.startMs, practiceEngine.state.value.currentPositionMs)

            // 2. Change HandMode to BOTH
            viewModel.setHandMode(HandMode.BOTH)

            // 3. Change PracticeMode to RHYTHM
            viewModel.setPracticeMode(PracticeMode.RHYTHM)

            // 4. Change Speed to 0.75x
            viewModel.setPlaybackSpeed(0.75f)

            // 5. Pause -> Resume -> Restart
            viewModel.togglePause()
            assertTrue(practiceEngine.state.value.isPaused)

            viewModel.togglePause()
            viewModel.restart()

            // Verify: Section 2 is still selected, restart position is sec2.startMs (not 0L)
            assertEquals(sec2.id, viewModel.uiState.value.selectedSection?.id)
            assertEquals(sec2.startMs, practiceEngine.state.value.currentPositionMs)
        } finally {
            viewModel.cleanup()
        }
    }
}
