package com.ian.pianotrainer.feature.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.domain.model.UserSettings
import com.ian.pianotrainer.domain.repository.BackupManifest
import com.ian.pianotrainer.domain.repository.BackupRepository
import com.ian.pianotrainer.domain.repository.SettingsRepository
import com.ian.pianotrainer.domain.service.MetronomeController
import com.ian.pianotrainer.domain.service.MetronomeSound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object InProgress : BackupUiState
    data class Success(val message: String, val manifest: BackupManifest) : BackupUiState
    data class Error(val message: String) : BackupUiState
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository? = null,
    private val metronomeController: MetronomeController? = null
) : ViewModel() {

    val userSettings: StateFlow<UserSettings> = settingsRepository.userSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    private val _metronomeSound = MutableStateFlow(metronomeController?.getSound() ?: MetronomeSound.WOOD)
    val metronomeSound: StateFlow<MetronomeSound> = _metronomeSound.asStateFlow()

    private val _customSoundName = MutableStateFlow(metronomeController?.getCustomSoundName())
    val customSoundName: StateFlow<String?> = _customSoundName.asStateFlow()

    private val _metronomeFeedback = MutableStateFlow<String?>(null)
    val metronomeFeedback: StateFlow<String?> = _metronomeFeedback.asStateFlow()

    init {
        viewModelScope.launch {
            userSettings.collect { settings ->
                metronomeController?.setVolume(settings.metronomeVolume)
            }
        }
    }

    fun setNoteNamingMode(mode: NoteNamingMode) {
        viewModelScope.launch {
            settingsRepository.setNoteNamingMode(mode)
        }
    }

    fun setDefaultDisplayMode(mode: DisplayMode) {
        viewModelScope.launch {
            // Force FALLING_NOTES if SHEET_MUSIC is requested while not ready
            val targetMode = if (mode == DisplayMode.SHEET_MUSIC) DisplayMode.FALLING_NOTES else mode
            settingsRepository.setDefaultDisplayMode(targetMode)
        }
    }

    fun setDefaultBpm(bpm: Int) {
        viewModelScope.launch {
            settingsRepository.setDefaultBpm(bpm)
        }
    }

    fun setMetronomeVolume(volume: Float) {
        metronomeController?.setVolume(volume)
        viewModelScope.launch {
            settingsRepository.setMetronomeVolume(volume)
        }
    }

    fun setMetronomeSound(sound: MetronomeSound) {
        metronomeController?.setSound(sound)
        _metronomeSound.value = metronomeController?.getSound() ?: sound
    }

    fun previewMetronome() {
        metronomeController?.preview()
    }

    fun importMetronomeSound(context: Context, uri: Uri) {
        val controller = metronomeController ?: return
        viewModelScope.launch {
            val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "custom_click.wav"
            val result = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    controller.importCustomSound(input, displayName)
                } ?: Result.failure(IllegalArgumentException("Không thể mở tệp âm thanh"))
            }
            if (result.isSuccess) {
                _metronomeSound.value = MetronomeSound.CUSTOM
                _customSoundName.value = displayName
                _metronomeFeedback.value = "Đã dùng âm metronome: $displayName"
            } else {
                _metronomeFeedback.value = result.exceptionOrNull()?.message ?: "Không thể nhập âm metronome"
            }
        }
    }

    fun clearMetronomeFeedback() {
        _metronomeFeedback.value = null
    }

    fun setDailyGoalMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setDailyGoalMinutes(minutes)
        }
    }

    fun setCountInOption(option: String) {
        viewModelScope.launch {
            settingsRepository.setCountInOption(option)
        }
    }

    fun setAutoReconnectMidi(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoReconnectMidi(enabled)
        }
    }

    fun resetData() {
        viewModelScope.launch {
            settingsRepository.resetAllUserData()
        }
    }

    fun exportBackup(context: Context, uri: Uri, includeAudio: Boolean = true) {
        val repo = backupRepository ?: return
        viewModelScope.launch {
            _backupState.value = BackupUiState.InProgress
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    val result = repo.createBackupZip(os, includeAudio)
                    if (result.isSuccess) {
                        _backupState.value = BackupUiState.Success("Xuất bản sao lưu thành công!", result.getOrThrow())
                    } else {
                        _backupState.value = BackupUiState.Error(result.exceptionOrNull()?.message ?: "Lỗi xuất sao lưu")
                    }
                } ?: run {
                    _backupState.value = BackupUiState.Error("Không thể mở tệp để ghi dữ liệu sao lưu")
                }
            } catch (e: Exception) {
                _backupState.value = BackupUiState.Error(e.message ?: "Lỗi khi sao lưu dữ liệu")
            }
        }
    }

    fun restoreBackup(context: Context, uri: Uri) {
        val repo = backupRepository ?: return
        viewModelScope.launch {
            _backupState.value = BackupUiState.InProgress
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val result = repo.restoreBackupZip(inputStream)
                    if (result.isSuccess) {
                        _backupState.value = BackupUiState.Success("Khôi phục bản sao lưu thành công!", result.getOrThrow())
                    } else {
                        _backupState.value = BackupUiState.Error(result.exceptionOrNull()?.message ?: "Lỗi khôi phục sao lưu")
                    }
                } ?: run {
                    _backupState.value = BackupUiState.Error("Không thể mở tệp sao lưu")
                }
            } catch (e: Exception) {
                _backupState.value = BackupUiState.Error(e.message ?: "Lỗi khi khôi phục dữ liệu")
            }
        }
    }

    fun dismissBackupState() {
        _backupState.value = BackupUiState.Idle
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val backupRepository: BackupRepository? = null,
        private val metronomeController: MetronomeController? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsRepository, backupRepository, metronomeController) as T
        }
    }
}
