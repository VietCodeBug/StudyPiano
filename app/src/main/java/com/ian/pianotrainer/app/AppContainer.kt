package com.ian.pianotrainer.app

import android.content.Context
import com.ian.pianotrainer.core.midi.AndroidMidiDriver
import com.ian.pianotrainer.core.music.SystemPracticeClock
import com.ian.pianotrainer.data.assets.AssetCurriculumDataSource
import com.ian.pianotrainer.data.assets.AssetExerciseDataSource
import com.ian.pianotrainer.data.local.database.DatabaseMaintenance
import com.ian.pianotrainer.data.local.database.PianoTrainerDatabase
import com.ian.pianotrainer.data.local.preferences.PreferencesManager
import com.ian.pianotrainer.data.practice.RealMetronomeController
import com.ian.pianotrainer.data.practice.RealPracticeEngine
import com.ian.pianotrainer.data.repository.CurriculumRepositoryImpl
import com.ian.pianotrainer.data.repository.ExerciseRepositoryImpl
import com.ian.pianotrainer.data.repository.ProgressRepositoryImpl
import com.ian.pianotrainer.data.repository.RealFreePlayRepository
import com.ian.pianotrainer.data.repository.SettingsRepositoryImpl
import com.ian.pianotrainer.data.repository.SongRepositoryImpl
import com.ian.pianotrainer.domain.repository.CurriculumRepository
import com.ian.pianotrainer.domain.repository.ExerciseRepository
import com.ian.pianotrainer.domain.repository.FreePlayRepository
import com.ian.pianotrainer.domain.repository.ProgressRepository
import com.ian.pianotrainer.domain.repository.SettingsRepository
import com.ian.pianotrainer.domain.repository.SongRepository
import com.ian.pianotrainer.domain.service.MetronomeController
import com.ian.pianotrainer.domain.service.MidiInput
import com.ian.pianotrainer.domain.service.PianoDeviceManager
import com.ian.pianotrainer.domain.service.PracticeEngine

interface AppContainer {
    val database: PianoTrainerDatabase
    val preferencesManager: PreferencesManager
    val databaseMaintenance: DatabaseMaintenance
    val midiInput: MidiInput
    val pianoDeviceManager: PianoDeviceManager
    val practiceEngine: PracticeEngine
    val metronomeController: MetronomeController
    val curriculumRepository: CurriculumRepository
    val exerciseRepository: ExerciseRepository
    val songRepository: SongRepository
    val progressRepository: ProgressRepository
    val settingsRepository: SettingsRepository
    val freePlayRepository: FreePlayRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: PianoTrainerDatabase by lazy {
        PianoTrainerDatabase.getInstance(context)
    }

    override val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(context)
    }

    override val databaseMaintenance: DatabaseMaintenance by lazy {
        DatabaseMaintenance(database)
    }

    private val assetCurriculumDataSource: AssetCurriculumDataSource by lazy {
        AssetCurriculumDataSource(context)
    }

    private val assetExerciseDataSource: AssetExerciseDataSource by lazy {
        AssetExerciseDataSource(context)
    }

    private val midiDriver: AndroidMidiDriver by lazy {
        AndroidMidiDriver(context)
    }

    override val midiInput: MidiInput
        get() = midiDriver

    override val pianoDeviceManager: PianoDeviceManager
        get() = midiDriver

    override val practiceEngine: PracticeEngine by lazy {
        RealPracticeEngine(SystemPracticeClock())
    }

    override val metronomeController: MetronomeController by lazy {
        RealMetronomeController()
    }

    override val curriculumRepository: CurriculumRepository by lazy {
        CurriculumRepositoryImpl(
            assetDataSource = assetCurriculumDataSource,
            lessonProgressDao = database.lessonProgressDao()
        )
    }

    override val exerciseRepository: ExerciseRepository by lazy {
        ExerciseRepositoryImpl(
            assetExerciseDataSource = assetExerciseDataSource,
            practiceSessionDao = database.practiceSessionDao()
        )
    }

    override val songRepository: SongRepository by lazy {
        SongRepositoryImpl(
            context = context,
            database = database,
            importedSongDao = database.importedSongDao(),
            songTrackDao = database.songTrackDao(),
            songNoteDao = database.songNoteDao(),
            songTempoDao = database.songTempoDao(),
            songTimeSignatureDao = database.songTimeSignatureDao()
        )
    }

    override val progressRepository: ProgressRepository by lazy {
        ProgressRepositoryImpl(
            sessionDao = database.practiceSessionDao(),
            noteResultDao = database.practiceNoteResultDao(),
            lessonProgressDao = database.lessonProgressDao()
        )
    }

    override val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(
            preferencesManager = preferencesManager,
            databaseMaintenance = databaseMaintenance
        )
    }

    override val freePlayRepository: FreePlayRepository by lazy {
        RealFreePlayRepository(
            dao = database.freePlayRecordingDao()
        )
    }
}
