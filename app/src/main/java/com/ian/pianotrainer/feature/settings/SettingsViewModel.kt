package com.ian.pianotrainer.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.domain.model.UserSettings
import com.ian.pianotrainer.domain.repository.BackupManifest
import com.ian.pianotrainer.domain.repository.BackupRepository
import com.ian.pianotrainer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object InProgress : BackupUiState
    data class Success(val message: String, val manifest: BackupManifest) : BackupUiState
    data class Error(val message: String) : BackupUiState
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository? = null
) : ViewModel() {

    val userSettings: StateFlow<UserSettings> = settingsRepository.userSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

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
        viewModelScope.launch {
            settingsRepository.setMetronomeVolume(volume)
        }
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
        private val backupRepository: BackupRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsRepository, backupRepository) as T
        }
    }
}
