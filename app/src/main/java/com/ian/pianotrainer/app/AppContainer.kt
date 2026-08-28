package com.ian.pianotrainer.app

import android.content.Context
import com.ian.pianotrainer.data.assets.AssetCurriculumDataSource
import com.ian.pianotrainer.data.local.database.PianoTrainerDatabase
import com.ian.pianotrainer.data.local.database.SampleDataSeeder
import com.ian.pianotrainer.data.local.preferences.PreferencesManager
import com.ian.pianotrainer.data.mock.MockMetronomeController
import com.ian.pianotrainer.data.mock.MockMidiInput
import com.ian.pianotrainer.data.mock.MockPianoDeviceManager
import com.ian.pianotrainer.data.mock.MockPracticeEngine
import com.ian.pianotrainer.data.repository.CurriculumRepositoryImpl
import com.ian.pianotrainer.data.repository.ProgressRepositoryImpl
import com.ian.pianotrainer.data.repository.SettingsRepositoryImpl
import com.ian.pianotrainer.data.repository.SongRepositoryImpl
import com.ian.pianotrainer.domain.repository.CurriculumRepository
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
    val sampleDataSeeder: SampleDataSeeder
    val midiInput: MidiInput
    val pianoDeviceManager: PianoDeviceManager
    val practiceEngine: PracticeEngine
    val metronomeController: MetronomeController
    val curriculumRepository: CurriculumRepository
    val songRepository: SongRepository
    val progressRepository: ProgressRepository
    val settingsRepository: SettingsRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: PianoTrainerDatabase by lazy {
        PianoTrainerDatabase.getInstance(context)
    }

    override val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(context)
    }

    override val sampleDataSeeder: SampleDataSeeder by lazy {
        SampleDataSeeder(database)
    }

    private val assetCurriculumDataSource: AssetCurriculumDataSource by lazy {
        AssetCurriculumDataSource(context)
    }

    override val midiInput: MidiInput by lazy {
        MockMidiInput()
    }

    override val pianoDeviceManager: PianoDeviceManager by lazy {
        MockPianoDeviceManager()
    }

    override val practiceEngine: PracticeEngine by lazy {
        MockPracticeEngine()
    }

    override val metronomeController: MetronomeController by lazy {
        MockMetronomeController()
    }

    override val curriculumRepository: CurriculumRepository by lazy {
        CurriculumRepositoryImpl(
            assetDataSource = assetCurriculumDataSource,
            lessonProgressDao = database.lessonProgressDao()
        )
    }

    override val songRepository: SongRepository by lazy {
        SongRepositoryImpl(
            importedSongDao = database.importedSongDao()
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
            sampleDataSeeder = sampleDataSeeder
        )
    }
}
