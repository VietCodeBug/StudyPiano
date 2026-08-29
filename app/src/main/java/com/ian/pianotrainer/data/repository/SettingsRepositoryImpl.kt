package com.ian.pianotrainer.data.repository

import com.ian.pianotrainer.data.local.database.DatabaseMaintenance
import com.ian.pianotrainer.data.local.preferences.PreferencesManager
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.KeyboardRangeMode
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.domain.model.UserSettings
import com.ian.pianotrainer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class SettingsRepositoryImpl(
    private val preferencesManager: PreferencesManager,
    private val databaseMaintenance: DatabaseMaintenance
) : SettingsRepository {

    override val userSettings: Flow<UserSettings> = preferencesManager.userSettingsFlow

    override suspend fun setNoteNamingMode(mode: NoteNamingMode) {
        preferencesManager.setNoteNamingMode(mode)
    }

    override suspend fun setDefaultDisplayMode(mode: DisplayMode) {
        preferencesManager.setDefaultDisplayMode(mode)
    }

    override suspend fun setDefaultBpm(bpm: Int) {
        preferencesManager.setDefaultBpm(bpm)
    }

    override suspend fun setVirtualPianoSoundEnabled(enabled: Boolean) {
        preferencesManager.setVirtualPianoSoundEnabled(enabled)
    }

    override suspend fun setMetronomeVolume(volume: Float) {
        preferencesManager.setMetronomeVolume(volume)
    }

    override suspend fun setLastSelectedHandMode(handMode: HandMode) {
        preferencesManager.setLastSelectedHandMode(handMode)
    }

    override suspend fun setLastKnownMidiDeviceName(name: String) {
        preferencesManager.setLastKnownMidiDeviceName(name)
    }

    override suspend fun setDailyGoalMinutes(minutes: Int) {
        preferencesManager.setDailyGoalMinutes(minutes)
    }

    override suspend fun setCountInOption(option: String) {
        preferencesManager.setCountInOption(option)
    }

    override suspend fun setAutoReconnectMidi(enabled: Boolean) {
        preferencesManager.setAutoReconnectMidi(enabled)
    }

    override suspend fun setKeyboardRangeMode(mode: KeyboardRangeMode) {
        preferencesManager.setKeyboardRangeMode(mode)
    }

    override suspend fun setDefaultLookAheadMs(lookAheadMs: Long) {
        preferencesManager.setDefaultLookAheadMs(lookAheadMs)
    }

    override suspend fun resetAllUserData() {
        preferencesManager.resetAllSettings()
        databaseMaintenance.clearAllData()
    }

    override suspend fun resetDemoData() {
        resetAllUserData()
    }
}
