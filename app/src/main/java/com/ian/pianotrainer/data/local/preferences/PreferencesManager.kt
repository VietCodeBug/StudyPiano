package com.ian.pianotrainer.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.KeyboardRangeMode
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class PreferencesManager(private val context: Context) {

    private object Keys {
        val NOTE_NAMING_MODE = stringPreferencesKey("note_naming_mode")
        val DEFAULT_DISPLAY_MODE = stringPreferencesKey("default_display_mode")
        val DEFAULT_BPM = intPreferencesKey("default_bpm")
        val VIRTUAL_PIANO_SOUND_ENABLED = booleanPreferencesKey("virtual_piano_sound_enabled")
        val METRONOME_VOLUME = floatPreferencesKey("metronome_volume")
        val LAST_SELECTED_HAND_MODE = stringPreferencesKey("last_selected_hand_mode")
        val LAST_KNOWN_MIDI_DEVICE_NAME = stringPreferencesKey("last_known_midi_device_name")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DEMO_DATA_INITIALIZED = booleanPreferencesKey("demo_data_initialized")
        val DAILY_GOAL_MINUTES = intPreferencesKey("daily_goal_minutes")
        val COUNT_IN_OPTION = stringPreferencesKey("count_in_option")
        val AUTO_RECONNECT_MIDI = booleanPreferencesKey("auto_reconnect_midi")
        val KEYBOARD_RANGE_MODE = stringPreferencesKey("keyboard_range_mode")
        val DEFAULT_LOOK_AHEAD_MS = longPreferencesKey("default_look_ahead_ms")
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        val namingModeStr = preferences[Keys.NOTE_NAMING_MODE] ?: NoteNamingMode.CDE.name
        val displayModeStr = preferences[Keys.DEFAULT_DISPLAY_MODE] ?: DisplayMode.FALLING_NOTES.name
        val handModeStr = preferences[Keys.LAST_SELECTED_HAND_MODE] ?: HandMode.RIGHT.name
        val rangeModeStr = preferences[Keys.KEYBOARD_RANGE_MODE] ?: KeyboardRangeMode.FULL_88_KEYS.name

        UserSettings(
            noteNamingMode = runCatching { NoteNamingMode.valueOf(namingModeStr) }.getOrDefault(NoteNamingMode.CDE),
            defaultDisplayMode = runCatching { DisplayMode.valueOf(displayModeStr) }.getOrDefault(DisplayMode.FALLING_NOTES),
            defaultBpm = preferences[Keys.DEFAULT_BPM] ?: 60,
            virtualPianoSoundEnabled = preferences[Keys.VIRTUAL_PIANO_SOUND_ENABLED] ?: false,
            metronomeVolume = preferences[Keys.METRONOME_VOLUME] ?: 0.8f,
            lastSelectedHandMode = runCatching { HandMode.valueOf(handModeStr) }.getOrDefault(HandMode.RIGHT),
            lastKnownMidiDeviceName = preferences[Keys.LAST_KNOWN_MIDI_DEVICE_NAME] ?: "",
            onboardingCompleted = preferences[Keys.ONBOARDING_COMPLETED] ?: true,
            demoDataInitialized = preferences[Keys.DEMO_DATA_INITIALIZED] ?: false,
            dailyGoalMinutes = preferences[Keys.DAILY_GOAL_MINUTES] ?: 20,
            countInOption = preferences[Keys.COUNT_IN_OPTION] ?: "OFF",
            autoReconnectMidi = preferences[Keys.AUTO_RECONNECT_MIDI] ?: true,
            keyboardRangeMode = runCatching { KeyboardRangeMode.valueOf(rangeModeStr) }.getOrDefault(KeyboardRangeMode.FULL_88_KEYS),
            defaultLookAheadMs = preferences[Keys.DEFAULT_LOOK_AHEAD_MS] ?: 4000L
        )
    }

    suspend fun setNoteNamingMode(mode: NoteNamingMode) {
        context.dataStore.edit { preferences ->
            preferences[Keys.NOTE_NAMING_MODE] = mode.name
        }
    }

    suspend fun setDefaultDisplayMode(mode: DisplayMode) {
        context.dataStore.edit { preferences ->
            preferences[Keys.DEFAULT_DISPLAY_MODE] = mode.name
        }
    }

    suspend fun setDefaultBpm(bpm: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.DEFAULT_BPM] = bpm
        }
    }

    suspend fun setVirtualPianoSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.VIRTUAL_PIANO_SOUND_ENABLED] = enabled
        }
    }

    suspend fun setMetronomeVolume(volume: Float) {
        context.dataStore.edit { preferences ->
            preferences[Keys.METRONOME_VOLUME] = volume
        }
    }

    suspend fun setLastSelectedHandMode(handMode: HandMode) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LAST_SELECTED_HAND_MODE] = handMode.name
        }
    }

    suspend fun setLastKnownMidiDeviceName(deviceName: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LAST_KNOWN_MIDI_DEVICE_NAME] = deviceName
        }
    }

    suspend fun setDailyGoalMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.DAILY_GOAL_MINUTES] = minutes.coerceIn(5, 120)
        }
    }

    suspend fun setCountInOption(option: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.COUNT_IN_OPTION] = option
        }
    }

    suspend fun setAutoReconnectMidi(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.AUTO_RECONNECT_MIDI] = enabled
        }
    }

    suspend fun setKeyboardRangeMode(mode: KeyboardRangeMode) {
        context.dataStore.edit { preferences ->
            preferences[Keys.KEYBOARD_RANGE_MODE] = mode.name
        }
    }

    suspend fun setDefaultLookAheadMs(lookAheadMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[Keys.DEFAULT_LOOK_AHEAD_MS] = lookAheadMs.coerceIn(1500L, 8000L)
        }
    }

    suspend fun setDemoDataInitialized(initialized: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.DEMO_DATA_INITIALIZED] = initialized
        }
    }

    suspend fun resetAllSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
