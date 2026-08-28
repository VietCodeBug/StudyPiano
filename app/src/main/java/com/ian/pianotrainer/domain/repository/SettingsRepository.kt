package com.ian.pianotrainer.domain.repository

import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val userSettings: Flow<UserSettings>

    suspend fun setNoteNamingMode(mode: NoteNamingMode)
    suspend fun setDefaultDisplayMode(mode: DisplayMode)
    suspend fun setDefaultBpm(bpm: Int)
    suspend fun setVirtualPianoSoundEnabled(enabled: Boolean)
    suspend fun setMetronomeVolume(volume: Float)
    suspend fun setLastSelectedHandMode(handMode: HandMode)
    suspend fun setLastKnownMidiDeviceName(name: String)
    suspend fun resetDemoData()
}
